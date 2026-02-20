/**
 * 全域常數與設定
 */
const STATUS = { ACTIVE: 'active', USED: 'used', DELETED: 'deleted' };
const NOTIFY_DAYS = [7, 3, 1, 0];
const MAX_LIST_ITEMS = 35;
const MAX_QUICK_REPLY = 13;
const MAX_FUZZY_RESULTS = 12;
const RATE_LIMIT_SECONDS = 2; // 每位使用者最短操作間隔

const scriptProps = PropertiesService.getScriptProperties();
const CHANNEL_ACCESS_TOKEN = scriptProps.getProperty('LINE_TOKEN');
const CHANNEL_SECRET = scriptProps.getProperty('LINE_CHANNEL_SECRET');
const SPREADSHEET_ID = scriptProps.getProperty('SS_ID');
const GEMINI_API_KEY = scriptProps.getProperty('GEMINI_KEY');
