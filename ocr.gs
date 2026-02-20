/**
 * 圖片 OCR 模組 (Gemini) — 含類別辨識
 */

function handleImageOCR(replyToken, messageId, dataSheet, userId) {
  try {
    const lineRes = UrlFetchApp.fetch(`https://api-data.line.me/v2/bot/message/${messageId}/content`, { 'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN }, 'method': 'get' });
    const base64Image = Utilities.base64Encode(lineRes.getBlob().getBytes());
    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${GEMINI_API_KEY.trim()}`;

    const categoryList = CATEGORY_KEYS.join('、');
    const prompt = `辨識圖中票券/優惠券的名稱、日期與類別。
格式：名稱 日期 類別
類別只能是：${categoryList}
日期格式：2026/01/01（若無日期用「永久」）
多張用 | 分隔。
範例：星巴克買一送一 2026/03/15 🍽️ 餐飲 | 全聯折價券 2026/06/30 🛒 購物`;

    const payload = { 'contents': [{ 'parts': [{ 'text': prompt }, { 'inline_data': { 'mime_type': 'image/jpeg', 'data': base64Image } }] }] };
    const res = UrlFetchApp.fetch(geminiUrl, { 'method': 'post', 'contentType': 'application/json', 'payload': JSON.stringify(payload) });
    const aiRaw = JSON.parse(res.getContentText()).candidates[0].content.parts[0].text.trim();

    const items = aiRaw.split('|').map(segment => {
      const s = segment.trim();
      // 嘗試解析「名稱 日期 類別」
      const parts = s.split(/\s+/);
      if (parts.length >= 3) {
        const cat = parts.slice(-2).join(' ');  // 最後兩個 token 可能是 emoji + 文字
        if (CATEGORY_KEYS.includes(cat)) {
          const nameDate = parts.slice(0, -2).join(' ');
          const entry = parseEntry(nameDate);
          if (entry) return { name: entry.name, date: entry.date, category: cat, raw: `${entry.name} ${entry.date}` };
        }
      }
      // fallback: 嘗試普通解析
      const entry = parseEntry(s);
      if (entry) return { name: entry.name, date: entry.date, category: DEFAULT_CATEGORY, raw: `${entry.name} ${entry.date}` };
      return { name: s, date: null, category: DEFAULT_CATEGORY, raw: s };
    }).filter(item => item.date !== null);

    if (items.length === 0) {
      sendMainMenu(replyToken, '❌ 無法辨識圖片中的票券，請手動輸入。');
      return;
    }

    // 暫存 OCR 結果到 cache
    const cache = CacheService.getScriptCache();
    cache.put('ocr_' + userId, JSON.stringify(items), 300);

    const displayLines = items.map(i => `${getCategoryEmoji(i.category)} ${i.name} (${i.date === '9999/12/31' ? '無期限' : i.date})`);
    const qItems = items.map((item, idx) => ({
      'type': 'action',
      'action': { 'type': 'postback', 'label': `存入: ${item.name.slice(0, 10)}`, 'data': `action=ocr_save_single&idx=${idx}`, 'displayText': `存入：${item.name}` }
    }));
    if (items.length > 1) {
      // 全部存入：直接用暫存的 OCR 結果
      qItems.unshift({ 'type': 'action', 'action': { 'type': 'message', 'label': '🔥 全部存入', 'text': 'OCR全部存入' } });
    }
    qItems.push({ 'type': 'action', 'action': { 'type': 'message', 'label': '❌ 取消', 'text': '取消' } });

    sendToLine('reply', { 'replyToken': replyToken, 'messages': [{ 'type': 'text', 'text': `🤖 偵測到 ${items.length} 張票券：\n\n${displayLines.join('\n')}`, 'quickReply': { 'items': qItems.slice(0, MAX_QUICK_REPLY) } }] });
  } catch (e) {
    console.log('OCR Error: ' + e.toString());
    sendMainMenu(replyToken, '❌ 圖片辨識失敗，請稍後再試或手動輸入。');
  }
}

/** 處理 OCR 全部存入 */
function handleOcrBatchSave(replyToken, dataSheet, userId) {
  const cache = CacheService.getScriptCache();
  const ocrRaw = cache.get('ocr_' + userId);
  if (!ocrRaw) {
    sendMainMenu(replyToken, '⚠️ OCR 資料已過期，請重新傳送圖片。');
    return;
  }
  const items = JSON.parse(ocrRaw);
  cache.remove('ocr_' + userId);
  let saved = [];
  items.forEach(item => {
    appendCouponRow(dataSheet, userId, item.name, item.date, item.category);
    saved.push(`${getCategoryEmoji(item.category)} ${item.name}`);
  });
  sendMainMenu(replyToken, `💾 已存入 ${saved.length} 張票券！\n\n${saved.join('\n')}`);
}
