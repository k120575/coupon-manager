/**
 * ==========================================
 * 優惠券管理系統 (Line Bot) - 邏輯最終校正版.
 * ==========================================
 */

const scriptProps = PropertiesService.getScriptProperties();
const CHANNEL_ACCESS_TOKEN = scriptProps.getProperty('LINE_TOKEN');
const SPREADSHEET_ID = scriptProps.getProperty('SS_ID');
const GEMINI_API_KEY = scriptProps.getProperty('GEMINI_KEY');

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) return;
    const contents = JSON.parse(e.postData.contents);
    const event = contents.events[0];
    if (!event) return;

    const userId = event.source.userId;
    const replyToken = event.replyToken;
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const userSheet = ss.getSheetByName('users') || ss.insertSheet('users');
    const dataSheet = ss.getSheetByName('data') || ss.insertSheet('data');

    // --- 1. Postback 處理 (解決無限循環與樣式統一) ---
    if (event.type === 'postback') {
      const pbData = event.postback.data;
      
      if (pbData === 'action=agree') {
        if (!checkAgreement(userSheet, userId)) userSheet.appendRow([userId, true]);
        sendMainMenu(replyToken, '✅ 感謝同意！請使用下方選單：');
        return;
      }

      // 二次確認：使用票券
      if (pbData.startsWith('action=confirm_use')) {
        const rowId = pbData.split('&row=')[1].split('&')[0];
        const name = decodeURIComponent(pbData.split('&name=')[1]);
        sendToLine('reply', { 'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `❓ 確定要「使用」這張票券嗎？\n🎫 ${name}`,
          'quickReply': { 'items': [
            { 'type': 'action', 'action': { 'type': 'postback', 'label': '✅ 確定使用', 'data': `action=execute_use&row=${rowId}` } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]}
        }]});
        return;
      }

      // 二次確認：刪除票券
      if (pbData.startsWith('action=confirm_delete')) {
        const rowId = pbData.split('&row=')[1].split('&')[0];
        const name = decodeURIComponent(pbData.split('&name=')[1]);
        sendToLine('reply', { 'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `⚠️ 確定要「刪除」這張票券嗎？\n🗑️ ${name}`,
          'quickReply': { 'items': [
            { 'type': 'action', 'action': { 'type': 'message', 'label': '🔥 確定刪除', 'text': `確定刪除 ${rowId} ${name}` } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]}
        }]});
        return;
      }

      // 最終執行核銷
      if (pbData.startsWith('action=execute_use')) {
        const rowId = pbData.split('&row=')[1];
        const result = executeUseByRow(dataSheet, userId, rowId);
        sendMainMenu(replyToken, result);
        return;
      }
    }

    if (!checkAgreement(userSheet, userId)) { sendConsentMessage(replyToken); return; }

    if (event.type === 'message' && event.message.type === 'image') {
      handleImageOCR(replyToken, event.message.id, dataSheet, userId);
      return;
    }

    if (event.type === 'message' && event.message.type === 'text') {
      const userText = event.message.text.trim();

      if (userText.startsWith('批次存入')) { handleBatchInsert(replyToken, dataSheet, userId, userText); return; }
      if (userText.startsWith('強制存入 ')) { handleForceBatch(replyToken, dataSheet, userId, userText); return; }
      if (userText.startsWith('確定刪除 ')) { executeDeletionTask(replyToken, dataSheet, userId, userText); return; }

      // 模糊搜尋指令
      if (userText.startsWith('使用 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('使用 ', '').trim(), 'use'); return; }
      if (userText.startsWith('刪除 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('刪除 ', '').trim(), 'delete'); return; }

      switch (userText) {
        case '❓ 幫助': sendHelpMessage(replyToken); return;
        case '📋 查詢票券': sendSearchMenu(replyToken); return;
        case '✅ 使用票券': sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, 'active_valid')] }); return;
        case '🗑️ 刪除票券': sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, 'delete_mode')] }); return;
        case '➕ 記錄優惠券': sendMainMenu(replyToken, '請輸入「名稱 日期」或傳照片！\n例如：咖啡券 2026/05/20'); return;
        case '已處理完畢': sendMainMenu(replyToken, "批次存入已全數處理完畢！"); return;
        case '取消':
        case '返回': sendMainMenu(replyToken, '已返回主選單。'); return;
      }

      const queryMap = { '🟢 可使用票券': 'active_valid_search', '🔴 已過期票券': 'active_expired', '⚪ 已使用記錄': 'used' };
      if (queryMap[userText]) {
        sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, queryMap[userText])] });
        return;
      }

      const entry = parseEntry(userText);
      if (entry) { processManualRecord(replyToken, dataSheet, userId, entry.name, entry.date); return; }
      sendFriendlyUnknown(replyToken, `請選擇下方選單功能。`);
    }
  } catch (err) { console.log("Error: " + err.toString()); }
}

/**
 * [修復] 列表顯示 - 全部統一導向 Postback 二次確認
 */
function getCouponListByStatus(sheet, userId, filter) {
  const data = sheet.getDataRange().getValues();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  let coupons = [];
  const isActionRequest = (filter === 'active_valid' || filter === 'delete_mode');

  for (let i = 1; i < data.length; i++) {
    if (data[i][0] !== userId) continue;
    const d = new Date(data[i][2]), status = data[i][3], isExp = (d < today);
    let ok = false;
    if (filter === 'active_valid' || filter === 'active_valid_search' || filter === 'delete_mode') { if (status === 'active' && !isExp) ok = true; }
    else if (filter === 'active_expired') { if (status === 'active' && isExp) ok = true; }
    else if (filter === 'used') { if (status === 'used') ok = true; }
    if (ok) coupons.push({ row: i + 1, name: data[i][1], dStr: (d.getFullYear() === 9999 ? "無期限" : Utilities.formatDate(d, "GMT+8", "yyyy/MM/dd")), dObj: d });
  }

  if (coupons.length === 0) return { "type": "text", "text": "📭 目前沒有相關紀錄。", "quickReply": { "items": getMainMenuItems() } };

  coupons.sort((a, b) => a.dObj - b.dObj);
  const bodyContents = coupons.slice(0, 35).map(c => {
    let row = { "type": "box", "layout": "horizontal", "contents": [
      { "type": "text", "text": "• " + c.name, "weight": "bold", "size": "sm", "flex": 4, "wrap": true },
      { "type": "text", "text": c.dStr === '無期限' ? '無期限' : c.dStr.slice(5), "size": "xs", "color": "#888888", "flex": 2, "align": "end" }
    ], "margin": "md" };
    if (isActionRequest) {
      row.contents.push({ 
        "type": "button", 
        "action": { 
          "type": "postback", 
          "label": filter === 'delete_mode' ? '刪除' : '使用', 
          "data": filter === 'delete_mode' ? `action=confirm_delete&row=${c.row}&name=${encodeURIComponent(c.name)}` : `action=confirm_use&row=${c.row}&name=${encodeURIComponent(c.name)}` 
        }, 
        "flex": 2, "height": "sm", "style": "link" 
      });
    }
    return row;
  });

  return { "type": "flex", "altText": "票券列表", "contents": { "type": "bubble", "header": { "type": "box", "layout": "vertical", "contents": [{ "type": "text", "text": "🎫 票券清單", "weight": "bold", "size": "lg" }] }, "body": { "type": "box", "layout": "vertical", "contents": bodyContents } }, "quickReply": { "items": getMainMenuItems() } };
}

/**
 * [修復] 模糊搜尋 - 統一導向 Postback 避免循環
 */
function handleFuzzyRequest(replyToken, sheet, userId, keyword, mode) {
  const data = sheet.getDataRange().getValues();
  let matches = [];
  for (let i = 1; i < data.length; i++) {
    if (data[i][0] === userId && data[i][3] === 'active' && data[i][1].includes(keyword)) {
      matches.push({ row: i + 1, name: data[i][1] });
    }
  }
  if (matches.length === 0) return sendMainMenu(replyToken, `❌ 找不到包含「${keyword}」的票券。`);

  const items = matches.slice(0, 12).map(m => ({
    'type': 'action',
    'action': {
      'type': 'postback',
      'label': `${mode === 'use' ? '使用' : '刪除'}: ${m.name}`,
      'data': mode === 'use' ? `action=confirm_use&row=${m.row}&name=${encodeURIComponent(m.name)}` : `action=confirm_delete&row=${m.row}&name=${encodeURIComponent(m.name)}`,
      'displayText': `${mode === 'use' ? '使用' : '刪除'}：${m.name}`
    }
  }));
  items.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });
  sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `🔍 找到 ${matches.length} 筆「${keyword}」，請點選：`, 'quickReply': { 'items': items } }] });
}

// --- 以下功能模組完全不動，維持原樣 ---
function executeDeletionTask(replyToken, dataSheet, userId, userText) {
  const parts = userText.split(' ');
  const rowIndex = parseInt(parts[1]);
  const ticketName = userText.substring(userText.indexOf(parts[2]));
  const rowData = dataSheet.getRange(rowIndex, 1, 1, 2).getValues()[0];
  if (rowData[0] === userId && String(rowData[1]) === ticketName) {
    dataSheet.getRange(rowIndex, 4).setValue('deleted');
    sendMainMenu(replyToken, `🗑️ 已成功刪除：\n${ticketName}`);
  } else { sendMainMenu(replyToken, "❌ 刪除失敗：驗證錯誤。"); }
}

function sendHelpMessage(replyToken) {
  const helpText = "💡 【優惠券管家使用說明】\n\n1️⃣  如何記錄？ (推薦！✨)\n📷 直接傳送【優惠券照片】給我，AI 會自動辨識名稱與日期！\n✍️ 或是手動輸入「名稱 日期」，例如：『星巴克 2026/12/31』\n\n2️⃣  如何使用票券？\n點擊下方【✅ 使用票券】，系統會列出清單，或輸入「使用 關鍵字」。\n\n3️⃣  如何查詢票券？\n點擊下方【📋 查詢票券】可按狀態查看清單。\n\n4️⃣  如何刪除票券？\n點擊下方【🗑️ 刪除票券】，或輸入「刪除 關鍵字」。\n\n5️⃣  自動提醒\n系統將於到期前 7天、3天、1天及當天自動發送通知提醒。";
  sendMainMenu(replyToken, helpText);
}

function checkAndNotify() {
  try {
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const dataSheet = ss.getSheetByName('data'); if (!dataSheet) return;
    const data = dataSheet.getDataRange().getValues();
    const today = new Date(); today.setHours(0,0,0,0);
    const threeDaysLater = new Date(); threeDaysLater.setDate(today.getDate() + 3);
    let notifications = {};
    for (let i = 1; i < data.length; i++) {
      const [uId, name, date, status] = data[i]; if (status !== 'active') continue;
      const d = new Date(date);
      if (d >= today && d <= threeDaysLater) {
        if (!notifications[uId]) notifications[uId] = [];
        const diffDays = Math.ceil((d - today) / (1000 * 60 * 60 * 24));
        notifications[uId].push(`• ${name} (${diffDays === 0 ? "今天" : diffDays + "天後"}到期)`);
      }
    }
    for (const uId in notifications) { sendToLine('push', { 'to': uId, 'messages': [{ 'type': 'text', 'text': `⏰ 【到期提醒】\n\n${notifications[uId].join('\n')}\n\n請記得盡快使用！` }] }); }
  } catch (e) { console.log(e.toString()); }
}

function handleBatchInsert(replyToken, dataSheet, userId, userText) {
  let lines = userText.replace('批次存入', '').trim().split(/\n+/);
  let successList = [];
  while (lines.length > 0) {
    let currentLine = lines[0]; let entry = parseEntry(currentLine);
    if (!entry) { lines.shift(); continue; }
    if (isDuplicate(dataSheet, userId, entry.name, entry.date)) {
      const remainingLines = lines.slice(1).join('\n');
      const nextBatchSuffix = remainingLines ? `\n${remainingLines}` : "";
      sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `⚠️ 重複提醒：「${entry.name}」已存在。\n是否重複存入？`, 'quickReply': { 'items': [
        { 'type': 'action', 'action': { 'type': 'message', 'label': '👌 幫我存', 'text': `強制存入 ${entry.name} ${entry.date}${nextBatchSuffix}` } },
        { 'type': 'action', 'action': { 'type': 'message', 'label': '⏭️ 不存入', 'text': remainingLines ? `批次存入\n${remainingLines}` : "已處理完畢" } }
      ]}}] });
      return;
    } else { dataSheet.appendRow([userId, entry.name, entry.date, 'active']); successList.push(entry.name); lines.shift(); }
  }
  sendMainMenu(replyToken, `💾 批次存入完成！\n\n${successList.join('\n')}`);
}

function handleImageOCR(replyToken, messageId, dataSheet, userId) {
  try {
    const lineRes = UrlFetchApp.fetch(`https://api-data.line.me/v2/bot/message/${messageId}/content`, { 'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN }, 'method': 'get' });
    const base64Image = Utilities.base64Encode(lineRes.getBlob().getBytes());
    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${GEMINI_API_KEY.trim()}`;
    const payload = { "contents": [{ "parts": [{ "text": "辨識圖中票券名稱與日期。格式：名稱 2026/01/01。多張用 | 分隔。" }, { "inline_data": { "mime_type": "image/jpeg", "data": base64Image } }] }] };
    const res = UrlFetchApp.fetch(geminiUrl, { "method": "post", "contentType": "application/json", "payload": JSON.stringify(payload) });
    const aiRaw = JSON.parse(res.getContentText()).candidates[0].content.parts[0].text.trim();
    const processed = aiRaw.split('|').map(i => { const e = parseEntry(i.trim()); return e ? `${e.name} ${e.date}` : i.trim(); });
    const qItems = processed.map(t => ({ 'type': 'action', 'action': { 'type': 'message', 'label': `存入: ${t.slice(0, 10)}`, 'text': t } }));
    if (processed.length > 1) qItems.unshift({ 'type': 'action', 'action': { 'type': 'message', 'label': '🔥 全部存入', 'text': `批次存入\n${processed.join('\n')}` } });
    qItems.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });
    sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `🤖 偵測到 ${processed.length} 張票券：\n\n${processed.join('\n')}`, 'quickReply': { 'items': qItems.slice(0, 13) } }] });
  } catch (e) { sendMainMenu(replyToken, "❌ 系統忙碌。"); }
}

function handleForceBatch(replyToken, dataSheet, userId, userText) {
  const content = userText.replace('強制存入 ', '').trim();
  const firstLineEnd = content.indexOf('\n');
  let currentRaw = (firstLineEnd === -1) ? content : content.substring(0, firstLineEnd);
  let remaining = (firstLineEnd === -1) ? "" : content.substring(firstLineEnd + 1);
  const entry = parseEntry(currentRaw);
  if (entry) dataSheet.appendRow([userId, entry.name, entry.date, 'active']);
  if (remaining) doPost({ postData: { contents: JSON.stringify({ events: [{ source: { userId: userId }, replyToken: replyToken, type: 'message', message: { type: 'text', text: `批次存入\n${remaining}` } }] }) } });
  else sendMainMenu(replyToken, "✅ 處理完成！");
}

function parseEntry(text) {
  const t = text.trim(); const lastSpace = t.lastIndexOf(' '); if (lastSpace === -1) return null;
  const nameRaw = t.substring(0, lastSpace).trim(); const dateRaw = t.substring(lastSpace + 1).trim();
  let fDate = '';
  if (['永久', '無', '9999/12/31'].some(s => dateRaw.includes(s))) fDate = '9999/12/31';
  else { const d = new Date(dateRaw.replace(/\.|-/g, '/')); if (isNaN(d.getTime())) return null; fDate = Utilities.formatDate(d, "GMT+8", "yyyy/MM/dd"); }
  return { name: nameRaw.replace(/\s+/g, ''), date: fDate, displayDate: fDate === '9999/12/31' ? '無期限' : fDate };
}

function isDuplicate(sheet, userId, name, dateStr) { const data = sheet.getDataRange().getValues(); return data.some(r => r[0] === userId && r[1] === name && r[3] === 'active' && ((r[2] instanceof Date ? Utilities.formatDate(r[2], "GMT+8", "yyyy/MM/dd") : String(r[2])) === dateStr)); }
function checkAgreement(sheet, userId) { const data = sheet.getDataRange().getValues(); return data.some(r => r[0] === userId && r[1] === true); }
function sendConsentMessage(replyToken) { sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'template', 'altText': '隱私條款', 'template': { 'type': 'confirm', 'text': '同意儲存您的票券資訊嗎？', 'actions': [{ 'type': 'postback', 'label': '同意', 'data': 'action=agree' }, { 'type': 'message', 'label': '拒絕', 'text': '不同意' }] } }] }); }
function executeUseByRow(sheet, userId, row) { const val = sheet.getRange(row, 1, 1, 4).getValues()[0]; if (val[0] !== userId) return '❌ 權限錯誤。'; sheet.getRange(row, 4).setValue('used'); return `✅ 已標記使用：${val[1]}`; }
function sendSearchMenu(replyToken) { sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': '請選擇查詢類別：', 'quickReply': { 'items': [{ 'type': 'action', 'action': { 'type': 'message', 'label': '🟢 可使用', 'text': '🟢 可使用票券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '🔴 已過期', 'text': '🔴 已過期票券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '⚪ 已使用', 'text': '⚪ 已使用記錄' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }] } }] }); }
function sendMainMenu(replyToken, text) { sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': text, 'quickReply': { 'items': getMainMenuItems() } }] }); }
function getMainMenuItems() { return [{ 'type': 'action', 'action': { 'type': 'message', 'label': '📋 查詢', 'text': '📋 查詢票券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '➕ 記錄', 'text': '➕ 記錄優惠券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '✅ 使用', 'text': '✅ 使用票券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '🗑️ 刪除', 'text': '🗑️ 刪除票券' } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '❓ 幫助', 'text': '❓ 幫助' } }]; }
function sendToLine(type, payload) { UrlFetchApp.fetch('https://api.line.me/v2/bot/message/' + type, { 'headers': { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN }, 'method': 'post', 'payload': JSON.stringify(payload) }); }
function processManualRecord(replyToken, dataSheet, userId, name, finalDate) { if (isDuplicate(dataSheet, userId, name, finalDate)) { sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `⚠️ 重複提醒：「${name}」已存在。`, 'quickReply': { 'items': [{ 'type': 'action', 'action': { 'type': 'message', 'label': '👌 幫我存', 'text': `強制存入 ${name} ${finalDate}` } }, { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }] } }] }); return; } dataSheet.appendRow([userId, name, finalDate, 'active']); sendMainMenu(replyToken, `💾 成功記錄：${name}`); }
function sendFriendlyUnknown(replyToken, prefix) { sendMainMenu(replyToken, prefix); }