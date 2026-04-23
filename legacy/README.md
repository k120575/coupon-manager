# Legacy · Google Apps Script 版

這個資料夾保留舊版 GAS 的程式碼作為參考，不再維護。

新版已全面遷移到 `worker/` (Cloudflare Workers + D1)。請勿修改這裡的檔案 —— 它們不會被部署。

## 舊版功能對照到新版位置

| `.gs` 檔 | 對應 `worker/src/` |
|---|---|
| `main.gs` | `index.ts` + `text.ts` + `postback.ts` |
| `auth.gs` | `signature.ts` + `db.ts`（rate limit / agreement） |
| `config.gs` | `config.ts` + `env.ts` |
| `coupon.gs` | `db.ts` + `coupon-list.ts` + `text.ts` |
| `batch.gs` | `text.ts`（handleBatchInsert） |
| `line.gs` | `line.ts` + `messages.ts` |
| `notify.gs` | `notify.ts` |
| `ocr.gs` | `image.ts` |
| `richmenu.gs` | `worker/scripts/setup-richmenu.ts` |
| `utils.gs` | `parser.ts` + `messages.ts` |
