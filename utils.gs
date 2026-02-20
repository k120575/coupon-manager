/**
 * 共用工具函式
 */

/** 解析 postback 參數 */
function parsePostbackParams(pbData) {
  const params = {};
  pbData.split('&').forEach(pair => {
    const idx = pair.indexOf('=');
    if (idx === -1) return;
    params[pair.substring(0, idx)] = decodeURIComponent(pair.substring(idx + 1));
  });
  return params;
}

/** 解析「名稱 日期」格式 */
function parseEntry(text) {
  const t = text.trim(); const lastSpace = t.lastIndexOf(' '); if (lastSpace === -1) return null;
  const nameRaw = t.substring(0, lastSpace).trim(); const dateRaw = t.substring(lastSpace + 1).trim();
  let fDate = '';
  if (['永久', '無', '9999/12/31'].some(s => dateRaw.includes(s))) fDate = '9999/12/31';
  else { const d = new Date(dateRaw.replace(/\.|-/g, '/')); if (isNaN(d.getTime())) return null; fDate = Utilities.formatDate(d, 'GMT+8', 'yyyy/MM/dd'); }
  return { name: nameRaw.replace(/\s+/g, ' '), date: fDate, displayDate: fDate === '9999/12/31' ? '無期限' : fDate };
}

/** 重複檢查 — 讀 5 欄 */
function isDuplicate(sheet, userId, name, dateStr) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return false;
  const data = sheet.getRange(1, 1, lastRow, 5).getValues();
  return data.some(r => r[0] === userId && r[1] === name && r[3] === STATUS.ACTIVE && ((r[2] instanceof Date ? Utilities.formatDate(r[2], 'GMT+8', 'yyyy/MM/dd') : String(r[2])) === dateStr));
}

/** 取得類別 emoji（向下相容：舊資料第 5 欄為空時回傳預設） */
function getCategoryDisplay(cat) {
  if (!cat || !CATEGORY_KEYS.includes(cat)) return DEFAULT_CATEGORY;
  return cat;
}

/** 取得類別 emoji 簡短顯示（只取第一個 emoji） */
function getCategoryEmoji(cat) {
  const display = getCategoryDisplay(cat);
  return display.split(' ')[0] || '📦';
}

/** 建立類別選擇 quickReply items（postback） */
function buildCategoryQuickReply() {
  return CATEGORY_KEYS.map(cat => ({
    'type': 'action',
    'action': { 'type': 'postback', 'label': cat, 'data': `action=select_cat&cat=${encodeURIComponent(cat)}`, 'displayText': cat }
  })).concat([
    { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
  ]);
}
