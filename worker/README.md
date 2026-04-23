# Coupon Manager — Cloudflare Workers 版

從 Google Apps Script + Google Sheets 重構而來。Runtime 換成 Cloudflare Workers (TypeScript)，DB 換成 D1 (SQLite)。

## 主要改進

- **補上 LINE signature 驗證**（GAS 時期做不到）— `src/signature.ts`
- **SQL 取代全表掃描** — 所有查詢走 index，重複檢查、模糊搜尋、列表從 O(N) 變 O(log N)
- **State 落 DB** — 原本靠 `CacheService` 的 pending state（5 分鐘過期）改用 `pending_actions` 表
- **Postback 用 `id` 而非 sheet row 號** — 不會因資料排序/刪除錯亂
- **cron 取代 GAS trigger** — 到期通知每天 UTC 00:00（台灣 08:00）自動跑
- **型別安全** — 所有 LINE event、domain model、D1 row 都有型別

## 目錄

```
worker/
├── src/
│   ├── index.ts          # fetch() / scheduled() 入口
│   ├── config.ts         # 常數（狀態、類別、限額）
│   ├── env.ts            # Env bindings 型別
│   ├── types.ts          # LINE event + domain types
│   ├── signature.ts      # x-line-signature HMAC 驗證
│   ├── line.ts           # LINE API client
│   ├── messages.ts       # Flex / QuickReply builders
│   ├── parser.ts         # 解析「名稱 日期」
│   ├── db.ts             # D1 queries（唯一直接接觸 SQL 的地方）
│   ├── coupon-list.ts    # 票券列表 Flex 產生器
│   ├── text.ts           # 文字訊息 router
│   ├── postback.ts       # postback router
│   ├── image.ts          # Gemini OCR
│   └── notify.ts         # cron 到期通知
├── migrations/
│   └── 0001_initial.sql
├── scripts/
│   └── import-from-csv.ts # 從 Sheets CSV 匯入 D1
├── wrangler.toml
├── package.json
└── tsconfig.json
```

## 首次部署

### 1. 安裝

```bash
cd worker
npm install
```

### 2. 建立 D1

```bash
npx wrangler d1 create coupon-manager
```

把回傳的 `database_id` 填入 `wrangler.toml` 的 `REPLACE_WITH_ACTUAL_D1_ID`。

### 3. 套用 schema

```bash
npm run db:remote   # 正式環境
# 或 npm run db:local 本機測試
```

### 4. 設定 secrets

```bash
npx wrangler secret put LINE_CHANNEL_ACCESS_TOKEN
npx wrangler secret put LINE_CHANNEL_SECRET
npx wrangler secret put GEMINI_API_KEY
```

### 5. 匯入舊資料（可選）

在 Google Sheets 分別把 `users` 和 `data` 分頁「下載為 CSV」，然後：

```bash
npm run import -- --users users.csv --data data.csv > import.sql
npx wrangler d1 execute coupon-manager --remote --file=./import.sql
```

### 6. 部署

```bash
npm run deploy
```

部署後會拿到 `https://coupon-manager.<subdomain>.workers.dev` 之類的 URL，把 LINE Developer Console 的 **Webhook URL** 改成 `<URL>/webhook` 即可。

### 7. 關掉舊 GAS

LINE Console 切 webhook 後，GAS 那邊的 webhook 就不會再被觸發，可以進 Apps Script 專案把 deployment 停用。

## 本機開發

```bash
npm run dev
```

會起本地 Workers + 本地 D1。測試 webhook 要用 ngrok 之類工具暴露 URL。

## 常見操作

```bash
# 看某個使用者的所有票券
npm run db:query -- "SELECT * FROM coupons WHERE user_id='U123abc...' ORDER BY expire_date"

# 看即將到期的票券
npm run db:query -- "SELECT * FROM coupons WHERE status='active' AND expire_date <= date('now', '+7 days')"

# 手動觸發 cron
npx wrangler triggers deploy
# 或本機測試
npx wrangler dev --test-scheduled
# 然後 curl http://localhost:8787/__scheduled
```

## 流程改動對照

| 項目 | 舊（GAS） | 新（Workers） |
|---|---|---|
| Webhook 驗證 | 無（拿不到 header） | HMAC-SHA256 驗 `x-line-signature` |
| 重複檢查 | 讀整張表 loop | SQL `WHERE` + index |
| Pending 狀態 | `CacheService` + postback URL | `pending_actions` 表 |
| 票券定位 | sheet 列號 | `coupons.id` (stable) |
| 到期通知 | GAS trigger (每天) | CF cron trigger `0 0 * * *` UTC |
| 批次存入重複 | 逐筆 prompt（遞迴送文字） | 一次處理完，彙總一筆 prompt「全部強制存入」 |
