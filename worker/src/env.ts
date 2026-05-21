export interface Env {
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
}
