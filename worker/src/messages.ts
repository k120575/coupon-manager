import { CATEGORIES, DEFAULT_CATEGORY } from './config.js';

export function textMsg(text: string): unknown {
  return { type: 'text', text };
}

export function getCategoryEmoji(cat: string): string {
  const safe = CATEGORIES.includes(cat as (typeof CATEGORIES)[number])
    ? cat
    : DEFAULT_CATEGORY;
  return safe.split(' ')[0] ?? '📦';
}

export function consentMessage(): unknown {
  return {
    type: 'template',
    altText: '隱私條款',
    template: {
      type: 'confirm',
      text: '📋 使用前請同意以下條款：\n\n本服務會儲存您的 LINE ID 及票券資訊（名稱、日期）於雲端資料庫中，僅供本機器人管理您的票券使用。\n\n您可隨時要求刪除所有資料。',
      actions: [
        { type: 'postback', label: '✅ 同意', data: 'action=agree' },
        { type: 'message', label: '❌ 拒絕', text: '不同意' },
      ],
    },
  };
}

export function searchMenuMessage(): unknown {
  return {
    type: 'text',
    text: '請選擇查詢類別：',
    quickReply: {
      items: [
        qrMessage('🟢 可使用', '🟢 可使用票券'),
        qrMessage('🔴 已過期', '🔴 已過期票券'),
        qrMessage('⚪ 已使用', '⚪ 已使用記錄'),
        qrMessage('❌ 取消', '取消'),
      ],
    },
  };
}

export function categorySearchMenu(): unknown {
  const items = [
    qrPostback('📋 全部', 'action=query_cat&filter=active_valid_search', '查詢：全部類別'),
    ...CATEGORIES.map((cat) =>
      qrPostback(
        cat,
        `action=query_cat&cat=${encodeURIComponent(cat)}&filter=active_valid_search`,
        `查詢：${cat}`,
      ),
    ),
    qrMessage('❌ 取消', '取消'),
  ];
  return {
    type: 'text',
    text: '🏷️ 請選擇要查詢的票券類別：',
    quickReply: { items },
  };
}

export function categoryPickerMessage(name: string, displayDate: string): unknown {
  const items = [
    ...CATEGORIES.map((cat) =>
      qrPostback(
        cat,
        `action=select_cat&cat=${encodeURIComponent(cat)}`,
        cat,
      ),
    ),
    qrMessage('❌ 取消', '取消'),
  ];
  return {
    type: 'text',
    text: `📝 ${name} (${displayDate})\n\n請選擇票券類別：`,
    quickReply: { items },
  };
}

export function helpMessage(): unknown {
  return textMsg(
    '💡 【優惠券管家使用說明】\n\n' +
      '1️⃣  如何記錄？ (推薦！✨)\n' +
      '📷 直接傳送【優惠券照片】給我，AI 會自動辨識名稱、日期與類別！\n' +
      '✍️ 或是手動輸入「名稱 日期」，例如：『星巴克 2026/12/31』\n\n' +
      '2️⃣  如何使用票券？\n' +
      '點擊下方【✅ 使用票券】，系統會列出清單，或輸入「使用 關鍵字」。\n\n' +
      '3️⃣  如何查詢票券？\n' +
      '📋 按狀態查詢（可使用/已過期/已使用）\n' +
      '🏷️ 按類別查詢（餐飲/購物/娛樂...）\n\n' +
      '4️⃣  如何刪除票券？\n' +
      '點擊下方【🗑️ 刪除票券】，或輸入「刪除 關鍵字」。\n\n' +
      '5️⃣  自動提醒\n' +
      '系統將於到期前 7天、3天、1天及當天自動發送通知提醒。',
  );
}

export function qrMessage(label: string, text: string): unknown {
  return { type: 'action', action: { type: 'message', label, text } };
}

export function qrPostback(label: string, data: string, displayText?: string): unknown {
  return {
    type: 'action',
    action: {
      type: 'postback',
      label,
      data,
      ...(displayText ? { displayText } : {}),
    },
  };
}
