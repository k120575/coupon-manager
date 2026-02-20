/**
 * 安全驗證與權限模組
 */

/**
 * Webhook Signature 驗證
 * 注意：GAS 的 doPost(e) 無法取得 HTTP request headers，
 * 因此無法驗證 x-line-signature。
 * GAS 的安全模型靠 deployment URL 的隨機性保護。
 */
function verifySignature(e) {
  return true;
}

/** 同意條款檢查 */
function checkAgreement(sheet, userId) {
  const data = sheet.getDataRange().getValues();
  return data.some(r => r[0] === userId && r[1] === true);
}

/** Rate Limiting — 使用 CacheService 做簡易限流 */
function isRateLimited(userId) {
  const cache = CacheService.getScriptCache();
  const key = 'rate_' + userId;
  if (cache.get(key)) return true;
  cache.put(key, '1', RATE_LIMIT_SECONDS);
  return false;
}

/** 隱私條款訊息 */
function sendConsentMessage(replyToken) {
  sendToLine('reply', {
    'replyToken': replyToken, 'messages': [{
      'type': 'template', 'altText': '隱私條款',
      'template': {
        'type': 'confirm',
        'text': '📋 使用前請同意以下條款：\n\n本服務會儲存您的 LINE ID 及票券資訊（名稱、日期）於 Google 試算表中，僅供本機器人管理您的票券使用。\n\n您可隨時要求刪除所有資料。',
        'actions': [
          { 'type': 'postback', 'label': '✅ 同意', 'data': 'action=agree' },
          { 'type': 'message', 'label': '❌ 拒絕', 'text': '不同意' }
        ]
      }
    }]
  });
}
