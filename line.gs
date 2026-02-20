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
  sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': text, 'quickReply': { 'items': getMainMenuItems() } }] });
}

function getMainMenuItems() {
  return [
    { 'type': 'action', 'action': { 'type': 'message', 'label': '📋 查詢', 'text': '📋 查詢票券' } },
    { 'type': 'action', 'action': { 'type': 'message', 'label': '➕ 記錄', 'text': '➕ 記錄優惠券' } },
    { 'type': 'action', 'action': { 'type': 'message', 'label': '✅ 使用', 'text': '✅ 使用票券' } },
    { 'type': 'action', 'action': { 'type': 'message', 'label': '🗑️ 刪除', 'text': '🗑️ 刪除票券' } },
    { 'type': 'action', 'action': { 'type': 'message', 'label': '❓ 幫助', 'text': '❓ 幫助' } }
  ];
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

function sendHelpMessage(replyToken) {
  const helpText = '💡 【優惠券管家使用說明】\n\n1️⃣  如何記錄？ (推薦！✨)\n📷 直接傳送【優惠券照片】給我，AI 會自動辨識名稱與日期！\n✍️ 或是手動輸入「名稱 日期」，例如：『星巴克 2026/12/31』\n\n2️⃣  如何使用票券？\n點擊下方【✅ 使用票券】，系統會列出清單，或輸入「使用 關鍵字」。\n\n3️⃣  如何查詢票券？\n點擊下方【📋 查詢票券】可按狀態查看清單。\n\n4️⃣  如何刪除票券？\n點擊下方【🗑️ 刪除票券】，或輸入「刪除 關鍵字」。\n\n5️⃣  自動提醒\n系統將於到期前 7天、3天、1天及當天自動發送通知提醒。';
  sendMainMenu(replyToken, helpText);
}
