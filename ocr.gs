/**
 * 圖片 OCR 模組 (Gemini)
 */

function handleImageOCR(replyToken, messageId, dataSheet, userId) {
  try {
    const lineRes = UrlFetchApp.fetch(`https://api-data.line.me/v2/bot/message/${messageId}/content`, { 'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN }, 'method': 'get' });
    const base64Image = Utilities.base64Encode(lineRes.getBlob().getBytes());
    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${GEMINI_API_KEY.trim()}`;
    const payload = { 'contents': [{ 'parts': [{ 'text': '辨識圖中票券名稱與日期。格式：名稱 2026/01/01。多張用 | 分隔。' }, { 'inline_data': { 'mime_type': 'image/jpeg', 'data': base64Image } }] }] };
    const res = UrlFetchApp.fetch(geminiUrl, { 'method': 'post', 'contentType': 'application/json', 'payload': JSON.stringify(payload) });
    const aiRaw = JSON.parse(res.getContentText()).candidates[0].content.parts[0].text.trim();
    const processed = aiRaw.split('|').map(i => { const e = parseEntry(i.trim()); return e ? `${e.name} ${e.date}` : i.trim(); });
    const qItems = processed.map(t => ({ 'type': 'action', 'action': { 'type': 'message', 'label': `存入: ${t.slice(0, 10)}`, 'text': t } }));
    if (processed.length > 1) qItems.unshift({ 'type': 'action', 'action': { 'type': 'message', 'label': '🔥 全部存入', 'text': `批次存入\n${processed.join('\n')}` } });
    qItems.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });
    sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `🤖 偵測到 ${processed.length} 張票券：\n\n${processed.join('\n')}`, 'quickReply': { 'items': qItems.slice(0, MAX_QUICK_REPLY) } }] });
  } catch (e) {
    console.log('OCR Error: ' + e.toString());
    sendMainMenu(replyToken, '❌ 圖片辨識失敗，請稍後再試或手動輸入。');
  }
}
