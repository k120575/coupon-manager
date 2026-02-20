/**
 * LINE Messaging API 工具函式
 */

function sendToLine(type, payload) {
  UrlFetchApp.fetch('https://api.line.me/v2/bot/message/' + type, {
    'headers': { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'post',
    'payload': JSON.stringify(payload)
  });
}

function sendMainMenu(replyToken, text) {
  sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': text }] });
}

function sendSearchMenu(replyToken) {
  sendToLine('reply', {
    'replyToken': replyToken, 'messages': [{
      'type': 'text', 'text': '請選擇查詢類別：', 'quickReply': {
        'items': [
          { 'type': 'action', 'action': { 'type': 'message', 'label': '🟢 可使用', 'text': '🟢 可使用票券' } },
          { 'type': 'action', 'action': { 'type': 'message', 'label': '🔴 已過期', 'text': '🔴 已過期票券' } },
          { 'type': 'action', 'action': { 'type': 'message', 'label': '⚪ 已使用', 'text': '⚪ 已使用記錄' } },
          { 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } }
        ]
      }
    }]
  });
}

/** 分類查詢選單 — 列出所有類別讓使用者點選 */
function sendCategorySearchMenu(replyToken) {
  const items = [
    { 'type': 'action', 'action': { 'type': 'postback', 'label': '📋 全部', 'data': 'action=query_cat&filter=active_valid_search', 'displayText': '查詢：全部類別' } }
  ].concat(CATEGORY_KEYS.map(cat => ({
    'type': 'action',
    'action': {
      'type': 'postback',
      'label': cat,
      'data': `action=query_cat&cat=${encodeURIComponent(cat)}&filter=active_valid_search`,
      'displayText': `查詢：${cat}`
    }
  })));
  items.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });
  sendToLine('reply', {
    'replyToken': replyToken, 'messages': [{
      'type': 'text', 'text': '🏷️ 請選擇要查詢的票券類別：', 'quickReply': { 'items': items }
    }]
  });
}

function sendHelpMessage(replyToken) {
  const helpText = '💡 【優惠券管家使用說明】\n\n1️⃣  如何記錄？ (推薦！✨)\n📷 直接傳送【優惠券照片】給我，AI 會自動辨識名稱、日期與類別！\n✍️ 或是手動輸入「名稱 日期」，例如：『星巴克 2026/12/31』\n\n2️⃣  如何使用票券？\n點擊下方【✅ 使用票券】，系統會列出清單，或輸入「使用 關鍵字」。\n\n3️⃣  如何查詢票券？\n📋 按狀態查詢（可使用/已過期/已使用）\n🏷️ 按類別查詢（餐飲/購物/娛樂...）\n\n4️⃣  如何刪除票券？\n點擊下方【🗑️ 刪除票券】，或輸入「刪除 關鍵字」。\n\n5️⃣  自動提醒\n系統將於到期前 7天、3天、1天及當天自動發送通知提醒。';
  sendMainMenu(replyToken, helpText);
}
