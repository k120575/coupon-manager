# 券管家 Coupy

票券/優惠券管理工具——記錄、提醒到期、隨手核銷。**個人開發者作品**。

## 兩個平台共享同一品牌

| 平台 | 狀態 | 後端 |
|---|---|---|
| **LINE bot** | 上線中（8 個早期使用者）| Cloudflare Worker + D1 |
| **Android App** | 開發中（v1.0 alpha） | 本機 SQLite，Pro 才上雲 |

未來 Pro 版才會做雙向同步（v1.1+）。

## 專案結構（monorepo）

```
.
├── worker/         Cloudflare Worker
│                   ├─ LINE bot webhook
│                   ├─ Daily cron（到期前 7 天 LINE push）
│                   └─ POST /ocr 給 App 端用 Gemini 辨識
│
├── android/        Android App（Kotlin + Jetpack Compose）
│
├── legacy/         舊版 Google Apps Script 程式碼（已遷移到 worker/，保留歷史）
│
├── SPRINT_1.md     Android v1.0 Sprint 1 規格
├── logo.html       LINE bot 原始 logo
└── logo_compare.html  App 品牌視覺定案過程
```

## Worker 開發

```sh
cd worker
npm install
npm run dev          # 本機 wrangler dev
npm run typecheck    # tsc
```

**Deploy**：GitHub Actions 自動觸發（推 `worker/**` 到 master）。

**Secrets**（`npx wrangler secret put <KEY>`）：
- `LINE_CHANNEL_ACCESS_TOKEN`
- `LINE_CHANNEL_SECRET`
- `GEMINI_API_KEY`
- `OCR_CLIENT_SECRET` — App OCR 共用 token，跟 App `local.properties` 的 `coupy.ocrToken` 一致

## Android App 開發

用 Android Studio 開 `android/` 資料夾即可（不是專案根目錄）。

**本機設定** `android/local.properties`（不進 git）：
```properties
coupy.workerBaseUrl=https://coupon-manager.<account>.workers.dev
coupy.ocrToken=<跟 Worker OCR_CLIENT_SECRET 同一個 token>
```

**技術棧**：
- Kotlin 2.1.0 + Jetpack Compose
- Hilt (DI) + Room (SQLite) + DataStore (preferences)
- WorkManager (推播排程)
- OkHttp (API 呼叫)
- min SDK 26 (Android 8.0) / target 35

## 商業模式：Freemium 訂閱

**免費版**（v1.0 範圍）：無限票券（本機）、12+ 內建分類可重新命名、基本搜尋、到期前 7 天推播、OCR 5 次/月、JSON / Drive 手動備份。

**Pro NT$60/月**（v1.1+ 才開賣）：自動雲端同步、無限 OCR、自訂分類無上限、進階篩選、自訂提醒時間。

v1.0 上線時 Pro UI 已備但功能未實作——「通知我上線」按鈕收集付費意願訊號，依此決定是否做 Pro。

## 設計準則

1. **資料不被綁架**：免費版可隨時匯出 JSON / Google Drive，不上傳任何資料到我們的伺服器
2. **付費賣便利不賣權利**：自動化、跨裝置、規模是 Pro 賣點，不是「不付錢就拿不回資料」
3. **克制 > 功能多**：高耗電 / 高隱私成本的功能（地理圍欄等）寧可不做
4. **看 vs 通知分開設計**：Dashboard 顯示 30 天範圍是「主動查」，推播限 7 天是「不打擾」
