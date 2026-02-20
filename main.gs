/**
 * ==========================================
 * 優惠券管理系統 (Line Bot) - v3.0 分類+分頁版
 * ==========================================
 * 
 * 檔案結構：
 *   config.gs  — 全域常數與設定（含類別定義）
 *   auth.gs    — 安全驗證（signature、同意、rate limiting）
 *   line.gs    — LINE Messaging API 工具函式
 *   utils.gs   — 共用工具函式（含類別工具）
 *   coupon.gs  — 票券 CRUD（含類別 + carousel 分頁）
 *   batch.gs   — 批次存入
 *   ocr.gs     — Gemini 圖片辨識（含類別）
 *   notify.gs  — 到期通知
 *   richmenu.gs — Rich Menu 設定腳本
 *   main.gs    — 主入口（本檔案）
 */

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) return;

    if (!verifySignature(e)) {
      console.log('Invalid signature, rejecting request.');
      return;
    }

    const contents = JSON.parse(e.postData.contents);
    const event = contents.events[0];
    if (!event) return;

    const userId = event.source.userId;
    const replyToken = event.replyToken;

    if (isRateLimited(userId)) {
      sendMainMenu(replyToken, '⏳ 操作太頻繁，請稍後再試。');
      return;
    }

    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const userSheet = ss.getSheetByName('users') || ss.insertSheet('users');
    const dataSheet = ss.getSheetByName('data') || ss.insertSheet('data');

    // --- 1. Postback ---
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
  const params = parsePostbackParams(pbData);

  // 同意條款不需要檢查
  if (params.action === 'agree') {
    if (!checkAgreement(userSheet, userId)) userSheet.appendRow([userId, true]);
    sendMainMenu(replyToken, '✅ 感謝同意！請使用下方選單：');
    return;
  }

  // 其他操作都要檢查同意
  if (!checkAgreement(userSheet, userId)) { sendConsentMessage(replyToken); return; }

  switch (params.action) {
    case 'confirm_use':
      sendToLine('reply', {
        'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `❓ 確定要「使用」這張票券嗎？\n🎫 ${params.name}`,
          'quickReply': { 'items': [
            { 'type': 'action', 'action': { 'type': 'postback', 'label': '✅ 確定使用', 'data': `action=execute_use&row=${params.row}&name=${encodeURIComponent(params.name)}` } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]}
        }]
      });
      break;

    case 'confirm_delete':
      sendToLine('reply', {
        'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `⚠️ 確定要「刪除」這張票券嗎？\n🗑️ ${params.name}`,
          'quickReply': { 'items': [
            { 'type': 'action', 'action': { 'type': 'postback', 'label': '🔥 確定刪除', 'data': `action=execute_delete&row=${params.row}&name=${encodeURIComponent(params.name)}` } },
            { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
          ]}
        }]
      });
      break;

    case 'execute_use':
      sendMainMenu(replyToken, executeActionByRow(dataSheet, userId, params.row, STATUS.USED, params.name));
      break;

    case 'execute_delete':
      sendMainMenu(replyToken, executeActionByRow(dataSheet, userId, params.row, STATUS.DELETED, params.name));
      break;

    case 'query_cat': {
      const catFilter = params.cat || null;
      const filter = params.filter || 'active_valid_search';
      sendToLine('reply', { 'replyToken': replyToken, 'messages': [getCouponListByStatus(dataSheet, userId, filter, catFilter)] });
      break;
    }

    case 'select_cat':
      handleCategorySelection(replyToken, dataSheet, userId, params.cat);
      break;

    case 'ocr_save_single': {
      const cache = CacheService.getScriptCache();
      const ocrRaw = cache.get('ocr_' + userId);
      if (!ocrRaw) { sendMainMenu(replyToken, '⚠️ OCR 資料已過期，請重新傳送圖片。'); break; }
      const ocrItems = JSON.parse(ocrRaw);
      const idx = parseInt(params.idx);
      if (isNaN(idx) || idx < 0 || idx >= ocrItems.length) { sendMainMenu(replyToken, '❌ 操作失敗。'); break; }
      const ocrItem = ocrItems[idx];
      appendCouponRow(dataSheet, userId, ocrItem.name, ocrItem.date, ocrItem.category);
      cache.remove('ocr_' + userId);
      sendMainMenu(replyToken, `💾 已存入：${getCategoryEmoji(ocrItem.category)} ${ocrItem.name}`);
      break;
    }

    case 'force_save': {
      const forceCache = CacheService.getScriptCache();
      const forceRaw = forceCache.get('pending_force_' + userId);
      if (!forceRaw) { sendMainMenu(replyToken, '⚠️ 操作已過期，請重新輸入。'); break; }
      const forceData = JSON.parse(forceRaw);
      forceCache.remove('pending_force_' + userId);
      askCategory(replyToken, userId, forceData.name, forceData.date);
      break;
    }
  }
}

// ==========================================
// 文字訊息路由
// ==========================================
function handleTextMessage(userText, replyToken, dataSheet, userId) {
  // OCR 全部存入
  if (userText === 'OCR全部存入') {
    handleOcrBatchSave(replyToken, dataSheet, userId);
    return;
  }

  if (userText.startsWith('批次存入')) { handleBatchInsert(replyToken, dataSheet, userId, userText); return; }
  if (userText.startsWith('強制存入 ')) { handleForceBatch(replyToken, dataSheet, userId, userText); return; }

  if (userText.startsWith('使用 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('使用 ', '').trim(), 'use'); return; }
  if (userText.startsWith('刪除 ')) { handleFuzzyRequest(replyToken, dataSheet, userId, userText.replace('刪除 ', '').trim(), 'delete'); return; }

  switch (userText) {
    case '❓ 幫助': sendHelpMessage(replyToken); return;
    case '📋 查詢票券': sendSearchMenu(replyToken); return;
    case '🏷️ 分類查詢': sendCategorySearchMenu(replyToken); return;
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
