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
