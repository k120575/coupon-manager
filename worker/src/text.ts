import { DEFAULT_CATEGORY, MAX_FUZZY_RESULTS } from './config.js';
import { buildCouponListMessage } from './coupon-list.js';
import {
  fuzzySearchActive,
  insertCoupon,
  isDuplicate,
  listCoupons,
  peekPending,
  putPending,
  takePending,
} from './db.js';
import type { ListFilter } from './db.js';
import { lineReply } from './line.js';
import {
  categoryPickerMessage,
  categorySearchMenu,
  helpMessage,
  qrMessage,
  qrPostback,
  quantityPickerMessage,
  searchMenuMessage,
  textMsg,
} from './messages.js';
import { parseEntry, toIsoDate } from './parser.js';
import type { Env } from './env.js';

export async function handleTextMessage(
  env: Env,
  replyToken: string,
  userId: string,
  userText: string,
): Promise<void> {
  const text = userText.trim();

  // 使用者可能處在「選張數」步驟，直接輸入數字
  if (/^\d{1,3}$/.test(text)) {
    const pending = await peekPending(env.DB, userId);
    if (pending && pending.kind === 'quantity') {
      const n = Number(text);
      if (n >= 1 && n <= 999) {
        await takePending(env.DB, userId);
        await transitionToCategory(env, replyToken, userId, pending.name, pending.date, n);
        return;
      }
    }
  }

  if (text === '批次存入' || text.startsWith('批次存入\n') || text.startsWith('批次存入 ')) {
    await handleBatchInsert(env, replyToken, userId, text);
    return;
  }

  if (text.startsWith('使用 ')) {
    await handleFuzzy(env, replyToken, userId, text.slice(3).trim(), 'use');
    return;
  }
  if (text.startsWith('刪除 ')) {
    await handleFuzzy(env, replyToken, userId, text.slice(3).trim(), 'delete');
    return;
  }

  switch (text) {
    case '❓ 幫助':
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [helpMessage()]);
      return;
    case '📋 查詢票券':
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [searchMenuMessage()]);
      return;
    case '🏷️ 分類查詢':
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [categorySearchMenu()]);
      return;
    case '✅ 使用票券':
      await replyList(env, replyToken, userId, 'active_valid', null);
      return;
    case '🗑️ 刪除票券':
      await replyList(env, replyToken, userId, 'delete_mode', null);
      return;
    case '➕ 記錄優惠券':
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
        textMsg(
          '請輸入「名稱 日期」或傳照片！\n例如：咖啡券 2026/05/20\n\n📌 輸入後會依序詢問「張數」與「類別」。',
        ),
      ]);
      return;
    case '取消':
    case '返回':
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [textMsg('已返回主選單。')]);
      return;
  }

  const statusMap: Record<string, ListFilter> = {
    '🟢 可使用票券': 'active_valid_search',
    '🔴 已過期票券': 'active_expired',
    '⚪ 已使用記錄': 'used',
  };
  const filter = statusMap[text];
  if (filter) {
    await replyList(env, replyToken, userId, filter, null);
    return;
  }

  const entry = parseEntry(text);
  if (entry) {
    await handleManualRecord(env, replyToken, userId, entry.name, entry.date, entry.displayDate);
    return;
  }

  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(
      '⚠️ 無法辨識您的輸入。\n\n💡 輸入格式：名稱 日期\n例如：星巴克 2026/12/31\n\n或使用下方選單操作：',
    ),
  ]);
}

async function replyList(
  env: Env,
  replyToken: string,
  userId: string,
  filter: ListFilter,
  category: string | null,
): Promise<void> {
  const today = new Date();
  const todayIso = toIsoDate(today);
  const rows = await listCoupons(env.DB, userId, filter, category, todayIso);
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    buildCouponListMessage(rows, filter, today),
  ]);
}

export async function handleManualRecord(
  env: Env,
  replyToken: string,
  userId: string,
  name: string,
  date: string,
  displayDate: string,
): Promise<void> {
  if (await isDuplicate(env.DB, userId, name, date)) {
    await putPending(env.DB, userId, { kind: 'force_save', name, date });
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      {
        type: 'text',
        text: `⚠️ 重複提醒：「${name}」已存在。`,
        quickReply: {
          items: [
            qrPostback('👌 幫我存', 'action=force_save', '幫我存'),
            qrMessage('❌ 取消', '取消'),
          ],
        },
      },
    ]);
    return;
  }
  await askQuantity(env, replyToken, userId, name, date, displayDate);
}

/** Step 1: 問張數 */
export async function askQuantity(
  env: Env,
  replyToken: string,
  userId: string,
  name: string,
  date: string,
  displayDate: string,
): Promise<void> {
  await putPending(env.DB, userId, { kind: 'quantity', name, date });
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    quantityPickerMessage(name, displayDate),
  ]);
}

/** Step 2: 張數選完 → 轉到類別選擇 */
export async function transitionToCategory(
  env: Env,
  replyToken: string,
  userId: string,
  name: string,
  date: string,
  quantity: number,
): Promise<void> {
  await putPending(env.DB, userId, { kind: 'category', name, date, quantity });
  const displayDate = date === '9999-12-31' ? '無期限' : date;
  const qtyHint = quantity > 1 ? `　×${quantity} 張` : '';
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    categoryPickerMessage(name, displayDate + qtyHint),
  ]);
}

/**
 * 批次存入：改進原本的「逐筆 prompt 重複」流程，
 * 改為一次處理，結尾彙總「已存入 N 筆，X 筆重複」並提供「強制存入全部重複項」按鈕。
 */
async function handleBatchInsert(
  env: Env,
  replyToken: string,
  userId: string,
  userText: string,
): Promise<void> {
  const body = userText.replace(/^批次存入\s*/, '').trim();
  const lines = body.split(/\n+/).map((l) => l.trim()).filter(Boolean);

  const saved: string[] = [];
  const duplicates: Array<{ name: string; date: string }> = [];
  const invalid: string[] = [];

  for (const line of lines) {
    const entry = parseEntry(line);
    if (!entry) {
      invalid.push(line);
      continue;
    }
    if (await isDuplicate(env.DB, userId, entry.name, entry.date)) {
      duplicates.push({ name: entry.name, date: entry.date });
      continue;
    }
    await insertCoupon(env.DB, userId, entry.name, entry.date, DEFAULT_CATEGORY, 1);
    saved.push(entry.name);
  }

  const summary: string[] = [];
  if (saved.length > 0) summary.push(`✅ 已存入 ${saved.length} 筆\n${saved.join('\n')}`);
  if (invalid.length > 0) summary.push(`⚠️ 格式錯誤 ${invalid.length} 筆已略過`);

  if (duplicates.length > 0) {
    await putPending(env.DB, userId, {
      kind: 'ocr_batch',
      items: duplicates.map((d) => ({
        name: d.name,
        date: d.date,
        category: DEFAULT_CATEGORY,
        quantity: 1,
      })),
    });
    summary.push(`⚠️ 發現 ${duplicates.length} 筆重複：\n${duplicates.map((d) => d.name).join('\n')}`);
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      {
        type: 'text',
        text: summary.join('\n\n'),
        quickReply: {
          items: [
            qrPostback('👌 全部強制存入', 'action=force_batch', '全部強制存入'),
            qrMessage('⏭️ 略過重複', '取消'),
          ],
        },
      },
    ]);
    return;
  }

  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(summary.length > 0 ? summary.join('\n\n') : '📭 沒有有效的票券資料。'),
  ]);
}

async function handleFuzzy(
  env: Env,
  replyToken: string,
  userId: string,
  keyword: string,
  mode: 'use' | 'delete',
): Promise<void> {
  if (!keyword) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ 請輸入關鍵字，例如：使用 星巴克'),
    ]);
    return;
  }
  const matches = await fuzzySearchActive(env.DB, userId, keyword, MAX_FUZZY_RESULTS);
  if (matches.length === 0) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg(`❌ 找不到包含「${keyword}」的票券。`),
    ]);
    return;
  }

  const hasMore = matches.length > MAX_FUZZY_RESULTS;
  const slice = matches.slice(0, MAX_FUZZY_RESULTS);
  const verb = mode === 'use' ? '使用' : '刪除';
  const action = mode === 'use' ? 'confirm_use' : 'confirm_delete';

  const items: unknown[] = slice.map((m) =>
    qrPostback(
      `${verb}: ${m.name}`.slice(0, 20),
      `action=${action}&id=${m.id}`,
      `${verb}：${m.name}`,
    ),
  );
  items.push(qrMessage('❌ 取消', '取消'));

  const overflow = hasMore ? `\n⚠️ 僅顯示前 ${MAX_FUZZY_RESULTS} 筆，請縮小關鍵字範圍。` : '';

  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    {
      type: 'text',
      text: `🔍 找到 ${matches.length} 筆「${keyword}」，請點選：${overflow}`,
      quickReply: { items },
    },
  ]);
}

