# Coupon Manager — Cloudflare Workers 版

LINE Bot 優惠券管理系統。從 Google Apps Script + Google Sheets 重構而來，Runtime 改用 Cloudflare Workers (TypeScript)，DB 換成 D1 (SQLite)。

## 主要改進（相對於 GAS 版）

- **補上 LINE signature 驗證**（GAS 時期做不到）— `src/signature.ts`
- **SQL 取代全表掃描** — 所有查詢走 index，重複檢查、模糊搜尋、列表從 O(N) 變 O(log N)
- **State 落 DB** — 原本靠 `CacheService`（5 分鐘過期）改用 `pending_actions` 表
- **Postback 用 `coupons.id`** 而非 sheet 列號，避免資料排序/刪除後錯亂
- **cron 取代 GAS trigger** — 到期通知每天 UTC 00:00（台灣 08:00）自動跑
- **型別安全** — LINE event、domain model、D1 row 全部有 TypeScript 型別
- **張數（quantity）功能** — 一筆同名券可以記多張，使用時扣一張、全用完才變 used
- **互動式輸入流程** — 名稱日期 → 問張數 → 問類別，取代舊版一行到底的模式
- **CI/CD** — push 到 GitHub `master` 會自動型別檢查 + 部署

## 目錄結構

```
worker/
├── src/
│   ├── index.ts          # fetch() / scheduled() 入口
│   ├── config.ts         # 常數（狀態、類別、限額、TTL）
│   ├── env.ts            # Env bindings 型別
│   ├── types.ts          # LINE event + domain types
│   ├── signature.ts      # x-line-signature HMAC 驗證
│   ├── line.ts           # LINE API client
│   ├── messages.ts       # Flex / QuickReply builders
│   ├── parser.ts         # 解析「名稱 日期」
│   ├── db.ts             # D1 queries（唯一直接接觸 SQL 的地方）
│   ├── coupon-list.ts    # 票券列表 Flex builder
│   ├── text.ts           # 文字訊息 router
│   ├── postback.ts       # postback router
│   ├── image.ts          # Gemini OCR
│   └── notify.ts         # cron 到期通知
├── migrations/
│   ├── 0001_initial.sql
│   └── 0002_add_quantity.sql
├── scripts/
│   ├── import-from-csv.ts  # 從 Sheets CSV 匯入 D1
│   └── setup-richmenu.ts   # 建立 / 上傳 rich menu
├── wrangler.toml           # Workers 設定（D1 binding、cron、observability）
├── package.json
├── tsconfig.json           # 主設定（只吃 @cloudflare/workers-types）
└── tsconfig.scripts.json   # scripts/ 用，吃 @types/node
```

---

## 首次部署

### 1. 安裝 CLI 依賴

```bash
cd worker
npm install
```

### 2. 建立 D1 資料庫

```bash
npx wrangler d1 create coupon-manager
```

回傳的 `database_id` 填入 `wrangler.toml` 的 `d1_databases.database_id` 欄位。

### 3. 套用所有 migrations

```bash
# 按順序執行
npx wrangler d1 execute coupon-manager --remote --file=./migrations/0001_initial.sql
npx wrangler d1 execute coupon-manager --remote --file=./migrations/0002_add_quantity.sql
```

### 4. 設定 runtime secrets（三個都要）

```bash
npx wrangler secret put LINE_CHANNEL_ACCESS_TOKEN
npx wrangler secret put LINE_CHANNEL_SECRET
npx wrangler secret put GEMINI_API_KEY
```

- `LINE_CHANNEL_ACCESS_TOKEN` — LINE Console → Messaging API 分頁
- `LINE_CHANNEL_SECRET` — LINE Console → Basic settings 分頁
- `GEMINI_API_KEY` — Google AI Studio

### 5. 匯入舊資料（可選，若從 GAS 遷移）

從 Google Sheets 把 `users` 與 `data` 分頁各別下載為 CSV，然後：

```bash
npm run import -- --users users.csv --data data.csv --out import.sql
npx wrangler d1 execute coupon-manager --remote --file=./import.sql
```

> ⚠️ `--out` 讓 script 直接寫檔，不要用 `>` 重導（PowerShell 預設 UTF-16 會把檔案搞壞）。
> `import.sql` 含使用者資料，已經 gitignore，不會 commit。

### 6. 部署

```bash
npm run deploy
```

完成後會拿到 `https://coupon-manager.<subdomain>.workers.dev`。把 LINE Developer Console 的 **Webhook URL** 改成 `<URL>/webhook` 並啟用 Use webhook。

### 7. 設定 rich menu

1. 開 `../richmenu-generator.html`（專案根目錄）→ 按 **下載 JPG**（PNG 通常超過 LINE 的 1 MB 上限）
2. 跑：
   ```bash
   $env:LINE_CHANNEL_ACCESS_TOKEN = "你的_token"  # PowerShell
   npm run richmenu -- ../richmenu-2500x1686.jpg
   ```
   會自動建立新 menu、上傳圖、設為所有使用者的預設，順便清掉舊的。

---

## CI/CD（GitHub Actions 自動部署）

專案根目錄的 `.github/workflows/deploy.yml` 已設定好：push 到 `master` 且動到 `worker/**` 就會自動 typecheck + deploy。

**GitHub repo 必須先在 Settings → Secrets and variables → Actions 設兩個 secret：**

| Name | 內容 |
|---|---|
| `CLOUDFLARE_API_TOKEN` | 具備 `Workers Scripts: Edit` + `D1: Edit` 權限的 token |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare dashboard 右下可看到 |

Runtime secrets（LINE_*, GEMINI_*）**不需要**放 GitHub，它們存在 Cloudflare 的 secret store，每次部署自動帶入。

### ⚠️ 更新 schema 時的順序

要加欄位 / 改表時**先套 migration 到 remote D1，再 push 新程式碼**：

```bash
# 步驟順序
1. 寫 migrations/000X_xxx.sql
2. npx wrangler d1 execute coupon-manager --remote --file=./migrations/000X_xxx.sql
3. git commit & push → Actions 會自動部署
```

倒過來會導致新程式碼上線時欄位還不存在、全部 SQL 爆掉。

---

## 本機開發

```bash
npm run dev
```

會起本地 Workers + 本地 D1。測試 LINE webhook 要用 ngrok 或 Cloudflare tunnel 把 localhost 暴露出來。

測 cron：
```bash
npx wrangler dev --test-scheduled
# 另開一個 terminal
curl "http://localhost:8787/__scheduled?cron=0+0+*+*+*"
```

---

## 常見除錯指令

```bash
# 看某使用者所有票券
npm run db:query -- "SELECT * FROM coupons WHERE user_id='U123...' ORDER BY expire_date"

# 看 7 天內到期
npm run db:query -- "SELECT * FROM coupons WHERE status='active' AND expire_date <= date('now', '+7 days')"

# 清掉過期的 pending actions（其實 cron 會自動清）
npm run db:query -- "DELETE FROM pending_actions WHERE expires_at < unixepoch()"

# 看所有 rich menu（管理用）
$env:LINE_CHANNEL_ACCESS_TOKEN = "..."
npx tsx scripts/setup-richmenu.ts  # 內建會先 list 一次

# 檢查型別
npm run typecheck
```

---

## 流程對照（舊 vs 新）

| 項目 | 舊（GAS） | 新（Workers） |
|---|---|---|
| Webhook 驗證 | 無（GAS 拿不到 header） | HMAC-SHA256 驗 `x-line-signature` |
| 查詢效能 | 全表 loop | SQL `WHERE` + index |
| Pending 狀態 | `CacheService` + 把資料塞進 postback URL | `pending_actions` 表（server-side token） |
| 票券定位 | sheet 列號 | `coupons.id`（stable） |
| 到期通知 | GAS trigger | CF cron `0 0 * * *` (UTC) |
| 批次重複 | 逐筆 prompt 遞迴 | 一次處理完彙總 prompt 全部強制 |
| 記錄流程 | 名稱日期 → 類別 | 名稱日期 → **張數** → 類別 |
| 票券張數 | 不支援 | `quantity` 欄位，使用時扣一張 |
| 到期通知時區 | 台灣時間 | UTC 排程，內部以台灣時區判斷 |
| 部署 | 手動在 GAS 編輯器按 Deploy | `git push` → GitHub Actions 自動部署 |

---

## 安全邊界（什麼進 repo、什麼不進）

| 資料 | 位置 | 原因 |
|---|---|---|
| `wrangler.toml` 含 `database_id` | ✅ repo | D1 ID 不是機密（要 token 才能用） |
| `LINE_CHANNEL_*`、`GEMINI_API_KEY` | ❌ repo | 放在 `wrangler secret`（Cloudflare 端） |
| `CLOUDFLARE_API_TOKEN` | ❌ repo | 本機 env var + GitHub Actions secret |
| `import.sql`（含使用者資料） | ❌ repo | 已 gitignore |
| `.dev.vars`（本機開發 secrets） | ❌ repo | 已 gitignore |
