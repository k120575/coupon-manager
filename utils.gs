/**
 * 共用工具函式
 */

/** 解析 postback 參數 */
function parsePostbackParams(pbData) {
  const params = {};
  pbData.split('&').forEach(pair => {
    const [key, val] = pair.split('=');
    params[key] = decodeURIComponent(val || '');
  });
  return { row: params.row, name: params.name };
}

/** 解析「名稱 日期」格式 */
function parseEntry(text) {
  const t = text.trim(); const lastSpace = t.lastIndexOf(' '); if (lastSpace === -1) return null;
  const nameRaw = t.substring(0, lastSpace).trim(); const dateRaw = t.substring(lastSpace + 1).trim();
  let fDate = '';
  if (['永久', '無', '9999/12/31'].some(s => dateRaw.includes(s))) fDate = '9999/12/31';
  else { const d = new Date(dateRaw.replace(/\.|-/g, '/')); if (isNaN(d.getTime())) return null; fDate = Utilities.formatDate(d, 'GMT+8', 'yyyy/MM/dd'); }
  return { name: nameRaw.replace(/\s+/g, ''), date: fDate, displayDate: fDate === '9999/12/31' ? '無期限' : fDate };
}

/** 重複檢查 */
function isDuplicate(sheet, userId, name, dateStr) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return false;
  const data = sheet.getRange(1, 1, lastRow, 4).getValues();
  return data.some(r => r[0] === userId && r[1] === name && r[3] === STATUS.ACTIVE && ((r[2] instanceof Date ? Utilities.formatDate(r[2], 'GMT+8', 'yyyy/MM/dd') : String(r[2])) === dateStr));
}
