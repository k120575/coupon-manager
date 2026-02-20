/**
 * 票券 CRUD 操作（含類別 + carousel 分頁）
 */

/** 統一的 row-based 操作，嚴格驗證 userId + name */
function executeActionByRow(sheet, userId, row, newStatus, expectedName) {
  try {
    const rowNum = parseInt(row);
    if (isNaN(rowNum) || rowNum < 2) return '❌ 操作失敗：無效的列號。';
    const lastRow = sheet.getLastRow();
    if (rowNum > lastRow) return '❌ 操作失敗：票券不存在。';

    const val = sheet.getRange(rowNum, 1, 1, 5).getValues()[0];
    if (val[0] !== userId) return '❌ 權限錯誤：這不是您的票券。';
    if (expectedName && val[1] !== expectedName) return '❌ 票券資料已變更，請重新查詢。';
    if (val[3] !== STATUS.ACTIVE) return '❌ 此票券已被使用或刪除。';

    sheet.getRange(rowNum, 4).setValue(newStatus);
    const actionLabel = newStatus === STATUS.USED ? '使用' : '刪除';
    return `✅ 已成功${actionLabel}：${val[1]}`;
  } catch (err) {
    console.log('executeActionByRow error: ' + err.toString());
    return '❌ 操作失敗，請稍後再試。';
  }
}

/**
 * 列表顯示 — carousel 分頁 + 類別顯示
 * categoryFilter: 可選，只顯示特定類別
 */
function getCouponListByStatus(sheet, userId, filter, categoryFilter) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return { 'type': 'text', 'text': '📭 目前沒有相關紀錄。' };

  const data = sheet.getRange(1, 1, lastRow, 5).getValues();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const currentYear = today.getFullYear();
  let coupons = [];
  const isActionRequest = (filter === 'active_valid' || filter === 'delete_mode');

  for (let i = 1; i < data.length; i++) {
    if (data[i][0] !== userId) continue;
    const d = new Date(data[i][2]), status = data[i][3], isExp = (d < today);
    const cat = getCategoryDisplay(data[i][4]);
    let ok = false;
    if (filter === 'active_valid' || filter === 'active_valid_search' || filter === 'delete_mode') { if (status === STATUS.ACTIVE && !isExp) ok = true; }
    else if (filter === 'active_expired') { if (status === STATUS.ACTIVE && isExp) ok = true; }
    else if (filter === 'used') { if (status === STATUS.USED) ok = true; }

    // 類別篩選
    if (ok && categoryFilter && cat !== categoryFilter) ok = false;

    if (ok) {
      let dateDisplay;
      if (d.getFullYear() === 9999) {
        dateDisplay = '無期限';
      } else if (d.getFullYear() !== currentYear) {
        dateDisplay = Utilities.formatDate(d, 'GMT+8', 'yyyy/MM/dd');
      } else {
        dateDisplay = Utilities.formatDate(d, 'GMT+8', 'MM/dd');
      }
      coupons.push({ row: i + 1, name: data[i][1], dStr: dateDisplay, dObj: d, cat: cat });
    }
  }

  if (coupons.length === 0) return { 'type': 'text', 'text': '📭 目前沒有相關紀錄。' };

  coupons.sort((a, b) => a.dObj - b.dObj);

  // 分頁：每 MAX_BUBBLE_ITEMS 筆一個 bubble，最多 MAX_BUBBLES 個
  const maxTotal = MAX_BUBBLE_ITEMS * MAX_BUBBLES;
  const displayCoupons = coupons.slice(0, maxTotal);
  const totalCount = coupons.length;
  const bubbles = [];

  for (let p = 0; p < displayCoupons.length; p += MAX_BUBBLE_ITEMS) {
    const chunk = displayCoupons.slice(p, p + MAX_BUBBLE_ITEMS);
    const pageNum = Math.floor(p / MAX_BUBBLE_ITEMS) + 1;
    const totalPages = Math.ceil(displayCoupons.length / MAX_BUBBLE_ITEMS);

    const bodyContents = chunk.map(c => {
      let row = {
        'type': 'box', 'layout': 'horizontal', 'contents': [
          { 'type': 'text', 'text': getCategoryEmoji(c.cat), 'size': 'sm', 'flex': 1 },
          { 'type': 'text', 'text': c.name, 'weight': 'bold', 'size': 'sm', 'flex': 5, 'wrap': true },
          { 'type': 'text', 'text': c.dStr, 'size': 'xs', 'color': '#888888', 'flex': 2, 'align': 'end' }
        ], 'margin': 'md', 'paddingAll': '8px', 'cornerRadius': '8px'
      };
      if (isActionRequest) {
        const actionData = filter === 'delete_mode'
          ? `action=confirm_delete&row=${c.row}&name=${encodeURIComponent(c.name)}`
          : `action=confirm_use&row=${c.row}&name=${encodeURIComponent(c.name)}`;
        // 整列可點擊，不再用獨立 button
        row.action = { 'type': 'postback', 'label': filter === 'delete_mode' ? '刪除' : '使用', 'data': actionData };
        row.backgroundColor = '#f0f0f0';
        // 右側操作提示
        row.contents.push({
          'type': 'text', 'text': filter === 'delete_mode' ? '✕' : '▶',
          'size': 'lg', 'color': filter === 'delete_mode' ? '#ff6b6b' : '#4ecdc4',
          'flex': 0, 'align': 'end', 'gravity': 'center'
        });
      }
      return row;
    });

    const headerText = totalPages > 1
      ? `🎫 票券清單 (${pageNum}/${totalPages})`
      : '🎫 票券清單';

    bubbles.push({
      'type': 'bubble',
      'header': { 'type': 'box', 'layout': 'vertical', 'contents': [{ 'type': 'text', 'text': headerText, 'weight': 'bold', 'size': 'md', 'wrap': true }] },
      'body': { 'type': 'box', 'layout': 'vertical', 'contents': bodyContents }
    });
  }

  // 超出上限提示
  if (totalCount > maxTotal) {
    bubbles.push({
      'type': 'bubble',
      'body': { 'type': 'box', 'layout': 'vertical', 'justifyContent': 'center', 'contents': [
        { 'type': 'text', 'text': `⚠️ 還有 ${totalCount - maxTotal} 筆未顯示`, 'weight': 'bold', 'align': 'center', 'wrap': true },
        { 'type': 'text', 'text': '請用「使用/刪除 關鍵字」搜尋', 'size': 'sm', 'color': '#888888', 'align': 'center', 'margin': 'md' }
      ]}
    });
  }

  // 只有一個 bubble 時用 bubble，多個時用 carousel
  const flexContents = bubbles.length === 1 ? bubbles[0] : { 'type': 'carousel', 'contents': bubbles };
  return { 'type': 'flex', 'altText': `票券列表 (共 ${totalCount} 筆)`, 'contents': flexContents };
}

/** 模糊搜尋 - 統一導向 Postback */
function handleFuzzyRequest(replyToken, sheet, userId, keyword, mode) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return sendMainMenu(replyToken, `❌ 找不到包含「${keyword}」的票券。`);

  const data = sheet.getRange(1, 1, lastRow, 5).getValues();
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

/**
 * 手動記錄 — 先暫存，等使用者選類別
 * 流程：使用者輸入 → 暫存 CacheService → 問類別 → 選了之後存入
 */
function processManualRecord(replyToken, dataSheet, userId, name, finalDate) {
  if (isDuplicate(dataSheet, userId, name, finalDate)) {
    const cache = CacheService.getScriptCache();
    cache.put('pending_force_' + userId, JSON.stringify({ name: name, date: finalDate }), 300);
    sendToLine('reply', {
      'replyToken': replyToken, 'messages': [{
        'type': 'text', 'text': `⚠️ 重複提醒：「${name}」已存在。`, 'quickReply': {
          'items': [
            { 'type': 'action', 'action': { 'type': 'postback', 'label': '👌 幫我存', 'data': 'action=force_save', 'displayText': '幫我存' } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]
        }
      }]
    });
    return;
  }
  askCategory(replyToken, userId, name, finalDate);
}

/** 暫存票券資訊到 CacheService，然後詢問類別 */
function askCategory(replyToken, userId, name, date) {
  const cache = CacheService.getScriptCache();
  cache.put('pending_' + userId, JSON.stringify({ name: name, date: date }), 300); // 5 分鐘過期
  sendToLine('reply', {
    'replyToken': replyToken, 'messages': [{
      'type': 'text',
      'text': `📝 ${name} (${date === '9999/12/31' ? '無期限' : date})\n\n請選擇票券類別：`,
      'quickReply': { 'items': buildCategoryQuickReply() }
    }]
  });
}

/** 處理類別選擇 — 從 cache 取出暫存資料並存入 */
function handleCategorySelection(replyToken, dataSheet, userId, category) {
  const cache = CacheService.getScriptCache();
  const pendingRaw = cache.get('pending_' + userId);
  if (!pendingRaw) {
    sendMainMenu(replyToken, '⚠️ 操作已過期，請重新輸入票券資訊。');
    return;
  }
  const pending = JSON.parse(pendingRaw);
  cache.remove('pending_' + userId);
  dataSheet.appendRow([userId, pending.name, pending.date, STATUS.ACTIVE, category]);
  sendMainMenu(replyToken, `💾 成功記錄：${pending.name}\n${category}`);
}

/** 直接存入（含類別，用於批次/強制存入） */
function appendCouponRow(dataSheet, userId, name, date, category) {
  dataSheet.appendRow([userId, name, date, STATUS.ACTIVE, category || DEFAULT_CATEGORY]);
}
