/**
 * 批次存入模組（含類別）
 */

function handleBatchInsert(replyToken, dataSheet, userId, userText) {
  let lines = userText.replace('批次存入', '').trim().split(/\n+/);
  let successList = [];
  while (lines.length > 0) {
    let currentLine = lines[0]; let entry = parseEntry(currentLine);
    if (!entry) { lines.shift(); continue; }
    if (isDuplicate(dataSheet, userId, entry.name, entry.date)) {
      const remainingLines = lines.slice(1).join('\n');
      const nextBatchSuffix = remainingLines ? `\n${remainingLines}` : '';
      const successPrefix = successList.length > 0 ? `（已存入 ${successList.length} 筆）\n\n` : '';
      sendToLine('reply', {
        'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `${successPrefix}⚠️ 重複提醒：「${entry.name}」已存在。\n是否重複存入？`, 'quickReply': {
            'items': [
              { 'type': 'action', 'action': { 'type': 'message', 'label': '👌 幫我存', 'text': `強制存入 ${entry.name} ${entry.date}${nextBatchSuffix}` } },
              { 'type': 'action', 'action': { 'type': 'message', 'label': '⏭️ 不存入', 'text': remainingLines ? `批次存入\n${remainingLines}` : '已處理完畢' } }
            ]
          }
        }]
      });
      return;
    } else {
      appendCouponRow(dataSheet, userId, entry.name, entry.date, DEFAULT_CATEGORY);
      successList.push(entry.name);
      lines.shift();
    }
  }
  sendMainMenu(replyToken, `💾 批次存入完成！（類別：${DEFAULT_CATEGORY}）\n\n${successList.join('\n')}`);
}

/** 強制存入（for loop，不遞迴） */
function handleForceBatch(replyToken, dataSheet, userId, userText) {
  const content = userText.replace('強制存入 ', '').trim();
  const allLines = content.split('\n');
  let successList = [];

  for (let i = 0; i < allLines.length; i++) {
    const line = allLines[i].trim();
    if (!line) continue;
    const entry = parseEntry(line);
    if (!entry) continue;

    if (i === 0) {
      appendCouponRow(dataSheet, userId, entry.name, entry.date, DEFAULT_CATEGORY);
      successList.push(entry.name);
      continue;
    }

    if (isDuplicate(dataSheet, userId, entry.name, entry.date)) {
      const remainingLines = allLines.slice(i + 1).join('\n');
      const nextBatchSuffix = remainingLines ? `\n${remainingLines}` : '';
      const successPrefix = successList.length > 0 ? `✅ 已存入 ${successList.length} 筆\n\n` : '';
      sendToLine('reply', {
        'replyToken': replyToken, 'messages': [{
          'type': 'text', 'text': `${successPrefix}⚠️ 重複提醒：「${entry.name}」已存在。\n是否重複存入？`, 'quickReply': {
            'items': [
              { 'type': 'action', 'action': { 'type': 'message', 'label': '👌 幫我存', 'text': `強制存入 ${entry.name} ${entry.date}${nextBatchSuffix}` } },
              { 'type': 'action', 'action': { 'type': 'message', 'label': '⏭️ 不存入', 'text': remainingLines ? `批次存入\n${remainingLines}` : '已處理完畢' } }
            ]
          }
        }]
      });
      return;
    } else {
      appendCouponRow(dataSheet, userId, entry.name, entry.date, DEFAULT_CATEGORY);
      successList.push(entry.name);
    }
  }
  sendMainMenu(replyToken, `✅ 處理完成！\n\n${successList.join('\n')}`);
}
