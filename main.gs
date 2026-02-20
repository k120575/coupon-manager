/**
 * ==========================================
 * 優惠券管理系統 (Line Bot) - v2.0 模組化版
 * ==========================================
 * 
 * 檔案結構：
 *   config.gs  — 全域常數與設定
 *   auth.gs    — 安全驗證（signature、同意、rate limiting）
 *   line.gs    — LINE Messaging API 工具函式
 *   utils.gs   — 共用工具函式
 *   coupon.gs  — 票券 CRUD 操作
 *   batch.gs   — 批次存入
 *   ocr.gs     — Gemini 圖片辨識
 *   notify.gs  — 到期通知
 *   main.gs    — 主入口（本檔案）
 */

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) return;

    // Signature 驗證
    if (!verifySignature(e)) {
      console.log('Invalid signature, rejecting request.');
      return;
    }

    const contents = JSON.parse(e.postData.contents);
    const event = contents.events[0];
    if (!event) return;

    const userId = event.source.userId;
    const replyToken = event.replyToken;

    // Rate Limiting
    if (isRateLimited(userId)) {
      sendMainMenu(replyToken, '⏳ 操作太頻繁，請稍後再試。');
      return;
    }

    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const userSheet = ss.getSheetByName('users') || ss.insertSheet('users');
    const dataSheet = ss.getSheetByName('data') || ss.insertSheet('data');

    // --- 1. Postback 處理 ---
    if (event.type === 'postback') {
      handlePostback(event.postback.data, replyToken, userId, userSheet, dataSheet);
      return;
    }

    // --- 2. 同意檢查 ---
    if (!checkAgreement(userSheet, userId)) { sendConsentMessage(replyToken); return; }

    // --- 3. 圖片 OCR ---
    if (event.type === 'message' && event.message.type === 'image') {
      handleImageOCR(replyToken, event.message.id, dataSheet, userId);
      return;
    }

    // --- 4. 文字訊息 ---
    if (event.type === 'message' && event.message.type === 'text') {
      handleTextMessage(event.message.text.trim(), replyToken, dataSheet, userId);
    }
  } catch (err) {
    console.log('doPost Error: ' + err.toString() + '\nStack: ' + err.stack);
  }
}

// ==========================================
// Postback 路由
// ==========================================
function handlePostback(pbData, replyToken, userId, userSheet, dataSheet) {
  if (pbData === 'action=agree') {
    if (!checkAgreement(userSheet, userId)) userSheet.appendRow([userId, true]);
    sendMainMenu(replyToken, '✅ 感謝同意！請使用下方選單：');
    return;
  }

  if (pbData.startsWith('action=confirm_use')) {
    const params = parsePostbackParams(pbData);
    sendToLine('reply', {
      'replyToken': replyToken, 'messages': [{
        'type': 'text', 'text': `❓ 確定要「使用」這張票券嗎？\n🎫 ${params.name}`,
        'quickReply': { 'items': [
          { 'type': 'action', 'action': { 'type': 'postback', 'label': '✅ 確定使用', 'data': `action=execute_use&row=${params.row}` } },
          { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
        ]}
      }]
    });
    return;
  }

  if (pbData.startsWith('action=confirm_delete')) {
    const params = parsePostbackParams(pbData);
    sendToLine('reply', {
      'replyToken': replyToken, 'messages': [{
        'type': 'text', 'text': `⚠️ 確定要「刪除」這張票券嗎？\n🗑️ ${params.name}`,
        'quickReply': { 'items': [
          { 'type': 'action', 'action': { 'type': 'postback', 'label': '🔥 確定刪除', 'data': `action=execute_delete&row=${params.row}` } },
          { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
        ]}
      }]
    });
    return;
  }

  if (pbData.startsWith('action=execute_use')) {
    const row = pbData.split('&row=')[1];
    sendMainMenu(replyToken, executeActionByRow(dataSheet, userId, row, STATUS.USED));
    return;
  }

  if (pbData.startsWith('action=execute_delete')) {
    const row = pbData.split('&row=')[1];
    sendMainMenu(replyToken, executeActionByRow(dataSheet, userId, row, STATUS.DELETED));
    return;
  }
}

// ==========================================
// 文字訊息路由
// ==========================================
function handleTextMessage(userText, replyToken, dataSheet, userId) {
  if (userText.startsWith('批次存入')) { handleBatchInsert(replyToken, dataSheet, userId, userText); return; }
  if (userText.startsWith('強制存入 ')) { handleForceBatch(replyToken, dataSheet, userId, userText); return; }

  if (userText.startsWith('使用 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('使用 ', '').trim(), 'use'); return; }
  if (userText.startsWith('刪除 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('刪除 ', '').trim(), 'delete'); return; }

  switch (userText) {
    case '❓ 幫助': sendHelpMessage(replyToken); return;
    case '📋 查詢票券': sendSearchMenu(replyToken); return;
    case '✅ 使用票券': sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, 'active_valid')] }); return;
    case '🗑️ 刪除票券': sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, 'delete_mode')] }); return;
    case '➕ 記錄優惠券': sendMainMenu(replyToken, '請輸入「名稱 日期」或傳照片！\n例如：咖啡券 2026/05/20'); return;
    case '已處理完畢': sendMainMenu(replyToken, '批次存入已全數處理完畢！'); return;
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

  sendMainMenu(replyToken, '⚠️ 無法辨識您的輸入。\n\n💡 輸入格式：名稱 日期\n例如：星巴克 2026/12/31\n\n或使用下方選單操作：');
}
