/**
 * 全域常數與設定
 */
const STATUS = { ACTIVE: 'active', USED: 'used', DELETED: 'deleted' };
const NOTIFY_DAYS = [7, 3, 1, 0];
const MAX_BUBBLE_ITEMS = 10; // 每個 bubble 顯示幾筆
const MAX_BUBBLES = 10;      // carousel 最多幾個 bubble（LINE 上限 12）
const MAX_QUICK_REPLY = 13;
const MAX_FUZZY_RESULTS = 12;
const RATE_LIMIT_SECONDS = 2;

// 票券類別
const CATEGORIES = {
  '🍽️ 餐飲': '🍽️ 餐飲',
  '🛒 購物': '🛒 購物',
  '🎬 娛樂': '🎬 娛樂',
  '🚗 交通': '🚗 交通',
  '🏥 醫療': '🏥 醫療',
  '📦 其他': '📦 其他'
};
const DEFAULT_CATEGORY = '📦 其他';
const CATEGORY_KEYS = Object.keys(CATEGORIES);

const scriptProps = PropertiesService.getScriptProperties();
const CHANNEL_ACCESS_TOKEN = scriptProps.getProperty('LINE_TOKEN');
const CHANNEL_SECRET = scriptProps.getProperty('LINE_CHANNEL_SECRET');
const SPREADSHEET_ID = scriptProps.getProperty('SS_ID');
const GEMINI_API_KEY = scriptProps.getProperty('GEMINI_KEY');
