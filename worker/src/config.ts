export const STATUS = {
  ACTIVE: 'active',
  USED: 'used',
  DELETED: 'deleted',
} as const;
export type Status = (typeof STATUS)[keyof typeof STATUS];

export const NOTIFY_DAYS = [7, 3, 1, 0] as const;

export const MAX_BUBBLE_ITEMS = 10;
export const MAX_BUBBLES = 10;
export const MAX_QUICK_REPLY = 13;
export const MAX_FUZZY_RESULTS = 12;

export const RATE_LIMIT_SECONDS = 2;
export const PENDING_TTL_SECONDS = 300;

// ---------- 成本閘（每日上限；台灣日期為一天）----------
// 數字是初始值，依實際用量在這裡調整即可（改完要 deploy）。
/** LINE 圖片辨識：每位使用者每日次數 */
export const LINE_OCR_DAILY_PER_USER = 20;
/** LINE 圖片辨識：全站每日次數 */
export const LINE_OCR_DAILY_GLOBAL = 300;
/** App /ocr：每個來源 IP 每日次數（App 免費額度是每月 15 次，30/日對 NAT 後多台裝置也夠） */
export const APP_OCR_DAILY_PER_IP = 30;
/** App /ocr：全站每日次數 */
export const APP_OCR_DAILY_GLOBAL = 500;
/** 「批次存入」一則訊息最多處理幾行 */
export const BATCH_MAX_LINES = 30;
/** 票券名稱最長字元數（超過直接截斷） */
export const MAX_NAME_LENGTH = 50;
/** 每位使用者最多幾筆 active 票券 */
export const MAX_ACTIVE_COUPONS_PER_USER = 500;
/** usage_counters 保留天數 */
export const USAGE_COUNTER_RETENTION_DAYS = 7;

export const CATEGORIES = [
  '🍽️ 餐飲',
  '🛒 購物',
  '🎬 娛樂',
  '🚗 交通',
  '🏥 醫療',
  '📦 其他',
] as const;
export type Category = (typeof CATEGORIES)[number];

export const DEFAULT_CATEGORY: Category = '📦 其他';
export const UNLIMITED_DATE = '9999-12-31';

export function isCategory(value: string): value is Category {
  return (CATEGORIES as readonly string[]).includes(value);
}
