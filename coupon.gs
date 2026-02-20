/**
 * 票券 CRUD 操作
 */

/** 統一的 row-based 操作，嚴格驗證 userId */
function executeActionByRow(sheet, userId, row, newStatus) {
  try {
    const rowNum = parseInt(row);
    if (isNaN(rowNum) || rowNum < 2) return '❌ 操作失敗：無效的列號。';
    const lastRow = sheet.getLastRow();
    if (rowNum > lastRow) return '❌ 操作失敗：票券不存在。';

    const val = sheet.getRange(rowNum, 1, 1, 4).getValues()[0];
    if (val[0] !== userId) return '❌ 權限錯誤：這不是您的票券。';
    if (val[3] !== STATUS.ACTIVE) return '❌ 此票券已被使用或刪除。';

    sheet.getRange(rowNum, 4).setValue(newStatus);
    const actionLabel = newStatus === STATUS.USED ? '使用' : '刪除';
    return `✅ 已成功${actionLabel}：${val[1]}`;
  } catch (err) {
    console.log('executeActionByRow error: ' + err.toString());
    return '❌ 操作失敗，請稍後再試。';
  }
}

/** 列表顯示 - 統一導向 Postback 二次確認 */
function getCouponListByStatus(sheet, userId, filter) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return { 'type': 'text', 'text': '📭 目前沒有相關紀錄。', 'quickReply': { 'items': getMainMenuItems() } };

  const data = sheet.getRange(1, 1, lastRow, 4).getValues();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const currentYear = today.getFullYear();
  let coupons = [];
  const isActionRequest = (filter === 'active_valid' || filter === 'delete_mode');

  for (let i = 1; i < data.length; i++) {
    if (data[i][0] !== userId) continue;
    const d = new Date(data[i][2]), status = data[i][3], isExp = (d < today);
    let ok = false;
    if (filter === 'active_valid' || filter === 'active_valid_search' || filter === 'delete_mode') { if (status === STATUS.ACTIVE && !isExp) ok = true; }
    else if (filter === 'active_expired') { if (status === STATUS.ACTIVE && isExp) ok = true; }
    else if (filter === 'used') { if (status === STATUS.USED) ok = true; }

    if (ok) {
      let dateDisplay;
      if (d.getFullYear() === 9999) {
        dateDisplay = '無期限';
      } else if (d.getFullYear() !== currentYear) {
        dateDisplay = Utilities.formatDate(d, 'GMT+8', 'yyyy/MM/dd');
      } else {
        dateDisplay = Utilities.formatDate(d, 'GMT+8', 'MM/dd');
      }
      coupons.push({ row: i + 1, name: data[i][1], dStr: dateDisplay, dObj: d });
    }
  }

  if (coupons.length === 0) return { 'type': 'text', 'text': '📭 目前沒有相關紀錄。', 'quickReply': { 'items': getMainMenuItems() } };

  coupons.sort((a, b) => a.dObj - b.dObj);
  const totalCount = coupons.length;
  const displayCoupons = coupons.slice(0, MAX_LIST_ITEMS);
  const bodyContents = displayCoupons.map(c => {
    let row = {
      'type': 'box', 'layout': 'horizontal', 'contents': [
        { 'type': 'text', 'text': '• ' + c.name, 'weight': 'bold', 'size': 'sm', 'flex': 4, 'wrap': true },
        { 'type': 'text', 'text': c.dStr, 'size': 'xs', 'color': '#888888', 'flex': 2, 'align': 'end' }
      ], 'margin': 'md'
    };
    if (isActionRequest) {
      row.contents.push({
        'type': 'button',
        'action': {
          'type': 'postback',
          'label': filter === 'delete_mode' ? '刪除' : '使用',
          'data': filter === 'delete_mode' ? `action=confirm_delete&row=${c.row}&name=${encodeURIComponent(c.name)}` : `action=confirm_use&row=${c.row}&name=${encodeURIComponent(c.name)}`
        },
        'flex': 2, 'height': 'sm', 'style': 'link'
      });
    }
    return row;
  });

  const headerText = totalCount > MAX_LIST_ITEMS
    ? `🎫 票券清單 (顯示前 ${MAX_LIST_ITEMS} 筆，共 ${totalCount} 筆，請用「使用/刪除 關鍵字」搜尋)`
    : '🎫 票券清單';

  return { 'type': 'flex', 'altText': '票券列表', 'contents': { 'type': 'bubble', 'header': { 'type': 'box', 'layout': 'vertical', 'contents': [{ 'type': 'text', 'text': headerText, 'weight': 'bold', 'size': 'md', 'wrap': true }] }, 'body': { 'type': 'box', 'layout': 'vertical', 'contents': bodyContents } }, 'quickReply': { 'items': getMainMenuItems() } };
}

/** 模糊搜尋 - 統一導向 Postback */
function handleFuzzyRequest(replyToken, sheet, userId, keyword, mode) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return sendMainMenu(replyToken, `❌ 找不到包含「${keyword}」的票券。`);

  const data = sheet.getRange(1, 1, lastRow, 4).getValues();
  let matches = [];
  for (let i = 1; i < data.length; i++) {
    if (data[i][0] === userId && data[i][3] === STATUS.ACTIVE && String(data[i][1]).includes(keyword)) {
      matches.push({ row: i + 1, name: data[i][1] });
    }
  }
  if (matches.length === 0) return sendMainMenu(replyToken, `❌ 找不到包含「${keyword}」的票券。`);

  const overflowMsg = matches.length > MAX_FUZZY_RESULTS ? `\n⚠️ 僅顯示前 ${MAX_FUZZY_RESULTS} 筆，請縮小關鍵字範圍。` : '';

  const items = matches.slice(0, MAX_FUZZY_RESULTS).map(m => ({
    'type': 'action',
    'action': {
      'type': 'postback',
      'label': `${mode === 'use' ? '使用' : '刪除'}: ${m.name}`.slice(0, 20),
      'data': mode === 'use' ? `action=confirm_use&row=${m.row}&name=${encodeURIComponent(m.name)}` : `action=confirm_delete&row=${m.row}&name=${encodeURIComponent(m.name)}`,
      'displayText': `${mode === 'use' ? '使用' : '刪除'}：${m.name}`
    }
  }));
  items.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });
  sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `🔍 找到 ${matches.length} 筆「${keyword}」，請點選：${overflowMsg}`, 'quickReply': { 'items': items } }] });
}

/** 手動記錄單筆 */
function processManualRecord(replyToken, dataSheet, userId, name, finalDate) {
  if (isDuplicate(dataSheet, userId, name, finalDate)) {
    sendToLine('reply', {
      'replyToken': replyToken, 'messages': [{
        'type': 'text', 'text': `⚠️ 重複提醒：「${name}」已存在。`, 'quickReply': {
          'items': [
            { 'type': 'action', 'action': { 'type': 'message', 'label': '👌 幫我存', 'text': `強制存入 ${name} ${finalDate}` } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]
        }
      }]
    });
    return;
  }
  dataSheet.appendRow([userId, name, finalDate, STATUS.ACTIVE]);
  sendMainMenu(replyToken, `💾 成功記錄：${name}`);
}
