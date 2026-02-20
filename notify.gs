/**
 * 到期通知模組 (7/3/1/0 天)
 */

function checkAndNotify() {
  try {
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const dataSheet = ss.getSheetByName('data'); if (!dataSheet) return;
    const lastRow = dataSheet.getLastRow();
    if (lastRow < 2) return;

    const data = dataSheet.getRange(1, 1, lastRow, 4).getValues();
    const today = new Date(); today.setHours(0, 0, 0, 0);
    let notifications = {};

    for (let i = 1; i < data.length; i++) {
      const [uId, name, date, status] = data[i];
      if (status !== STATUS.ACTIVE) continue;
      const d = new Date(date); d.setHours(0, 0, 0, 0);
      if (d.getFullYear() === 9999) continue;

      const diffDays = Math.round((d - today) / (1000 * 60 * 60 * 24));
      if (NOTIFY_DAYS.includes(diffDays)) {
        if (!notifications[uId]) notifications[uId] = [];
        const label = diffDays === 0 ? '🔴 今天到期' : `🟡 ${diffDays} 天後到期`;
        notifications[uId].push(`• ${name} (${label})`);
      }
    }
    for (const uId in notifications) {
      sendToLine('push', { 'to': uId, 'messages': [{ 'type': 'text', 'text': `⏰ 【到期提醒】\n\n${notifications[uId].join('\n')}\n\n請記得盡快使用！` }] });
    }
  } catch (e) { console.log('checkAndNotify error: ' + e.toString()); }
}
