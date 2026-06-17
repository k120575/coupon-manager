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
  /**
   * App 端 OCR 的 Gemini 模型優先鏈，逗號分隔（例：
   * "gemini-3.1-flash-lite,gemini-2.5-flash,gemini-3.5-flash"）。
   * 第一個是主力，遇到「可換」的失敗（限流/過載/5xx/模型不存在/超時）會自動退到下一個。
   * 設在 wrangler.toml 的 [vars]，不是 secret（模型名稱非機密）。未設時退回單一預設模型。
   */
  OCR_MODELS?: string;
}
