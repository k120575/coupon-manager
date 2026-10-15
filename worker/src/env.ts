export interface Env {
  /** D1：users / coupons / pending_actions / usage_counters（成本閘計數，見 migrations/0004） */
  DB: D1Database;
  LINE_CHANNEL_ACCESS_TOKEN: string;
  LINE_CHANNEL_SECRET: string;
  GEMINI_API_KEY: string;
  /**
   * App 端 OCR 共用密鑰。App 在 HTTP header X-Coupy-Token 帶這個值，
   * Worker 驗證才執行 OCR——避免 endpoint 被外人濫用 Gemini 額度。
   * 設定方式：npx wrangler secret put OCR_CLIENT_SECRET
   */
  OCR_CLIENT_SECRET: string;
  /**
   * Gemini OCR 模型優先鏈，LINE bot 圖片辨識與 App 端 OCR API 共用，逗號分隔（例：
   * "gemini-3.1-flash-lite,gemini-2.5-flash,gemini-3.6-flash"）。
   * 第一個是主力，遇到「可換」的失敗（限流/過載/5xx/模型不存在/超時）會自動退到下一個。
   * 設定位置：Cloudflare Dashboard → Settings → Variables（明文即可，模型名稱非機密）。
   * 不寫在 wrangler.toml，改模型不用重新部署；keep_vars = true 保證 deploy 不會清掉。
   * 未設時退回 gemini.ts 的單一預設模型。
   */
  OCR_MODELS?: string;
}
