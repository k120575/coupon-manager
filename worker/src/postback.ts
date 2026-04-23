import { DEFAULT_CATEGORY, isCategory, STATUS } from './config.js';
import { buildCouponListMessage } from './coupon-list.js';
import {
  hasAgreed,
  insertCoupon,
  listCoupons,
  recordAgreement,
  takePending,
  updateCouponStatus,
} from './db.js';
import { lineReply } from './line.js';
import { qrMessage, qrPostback, textMsg } from './messages.js';
import { parsePostbackParams, toIsoDate } from './parser.js';
import { askCategory } from './text.js';
import type { Env } from './env.js';

export async function handlePostback(
  env: Env,
  replyToken: string,
  userId: string,
  data: string,
): Promise<void> {
  const params = parsePostbackParams(data);

  // 同意不需要先檢查同意狀態
  if (params.action === 'agree') {
    if (!(await hasAgreed(env.DB, userId))) await recordAgreement(env.DB, userId);
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('✅ 感謝同意！請使用下方選單：'),
    ]);
    return;
  }

  if (!(await hasAgreed(env.DB, userId))) {
    const { consentMessage } = await import('./messages.js');
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [consentMessage()]);
    return;
  }

  switch (params.action) {
    case 'confirm_use':
      await confirmAction(env, replyToken, params.id, 'use');
      return;
    case 'confirm_delete':
      await confirmAction(env, replyToken, params.id, 'delete');
      return;
    case 'execute_use':
      await executeAction(env, replyToken, userId, params.id, 'used');
      return;
    case 'execute_delete':
      await executeAction(env, replyToken, userId, params.id, 'deleted');
      return;
    case 'query_cat':
      await queryByCategory(env, replyToken, userId, params);
      return;
    case 'select_cat':
      await handleCategorySelection(env, replyToken, userId, params.cat);
      return;
    case 'force_save':
      await handleForceSave(env, replyToken, userId);
      return;
    case 'force_batch':
      await handleForceBatch(env, replyToken, userId);
      return;
    case 'ocr_save_single':
      await handleOcrSaveSingle(env, replyToken, userId, params.idx);
      return;
    case 'ocr_save_all':
      await handleOcrSaveAll(env, replyToken, userId);
      return;
  }
}

async function confirmAction(
  env: Env,
  replyToken: string,
  idStr: string | undefined,
  mode: 'use' | 'delete',
): Promise<void> {
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [textMsg('❌ 操作失敗。')]);
    return;
  }
  const verb = mode === 'use' ? '使用' : '刪除';
  const actionKey = mode === 'use' ? 'execute_use' : 'execute_delete';
  const emoji = mode === 'use' ? '🎫' : '🗑️';
  const confirmLabel = mode === 'use' ? '✅ 確定使用' : '🔥 確定刪除';

  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    {
      type: 'text',
      text: `❓ 確定要「${verb}」這張票券嗎？\n${emoji} (票券 #${id})`,
      quickReply: {
        items: [
          qrPostback(confirmLabel, `action=${actionKey}&id=${id}`),
          qrMessage('❌ 取消', '取消'),
        ],
      },
    },
  ]);
}

async function executeAction(
  env: Env,
  replyToken: string,
  userId: string,
  idStr: string | undefined,
  newStatus: 'used' | 'deleted',
): Promise<void> {
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [textMsg('❌ 操作失敗。')]);
    return;
  }
  const updated = await updateCouponStatus(env.DB, id, userId, newStatus);
  if (!updated) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('❌ 此票券已被使用、刪除或不屬於您。'),
    ]);
    return;
  }
  const verb = newStatus === STATUS.USED ? '使用' : '刪除';
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(`✅ 已成功${verb}：${updated.name}`),
  ]);
}

async function queryByCategory(
  env: Env,
  replyToken: string,
  userId: string,
  params: Record<string, string>,
): Promise<void> {
  const filter = ((params.filter as string) || 'active_valid_search') as
    | 'active_valid_search'
    | 'active_valid'
    | 'delete_mode'
    | 'active_expired'
    | 'used';
  const cat = params.cat || null;
  const today = new Date();
  const rows = await listCoupons(env.DB, userId, filter, cat, toIsoDate(today));
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    buildCouponListMessage(rows, filter, today),
  ]);
}

async function handleCategorySelection(
  env: Env,
  replyToken: string,
  userId: string,
  cat: string | undefined,
): Promise<void> {
  if (!cat || !isCategory(cat)) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [textMsg('❌ 類別錯誤。')]);
    return;
  }
  const pending = await takePending(env.DB, userId);
  if (!pending || pending.kind !== 'category') {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ 操作已過期，請重新輸入票券資訊。'),
    ]);
    return;
  }
  await insertCoupon(env.DB, userId, pending.name, pending.date, cat);
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(`💾 成功記錄：${pending.name}\n${cat}`),
  ]);
}

async function handleForceSave(env: Env, replyToken: string, userId: string): Promise<void> {
  const pending = await takePending(env.DB, userId);
  if (!pending || pending.kind !== 'force_save') {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ 操作已過期，請重新輸入。'),
    ]);
    return;
  }
  // 強制存入流程繼續問類別
  const displayDate = pending.date === '9999-12-31' ? '無期限' : pending.date;
  await askCategory(env, replyToken, userId, pending.name, pending.date, displayDate);
}

async function handleForceBatch(env: Env, replyToken: string, userId: string): Promise<void> {
  const pending = await takePending(env.DB, userId);
  if (!pending || pending.kind !== 'ocr_batch') {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ 操作已過期，請重新輸入。'),
    ]);
    return;
  }
  for (const item of pending.items) {
    await insertCoupon(env.DB, userId, item.name, item.date, item.category);
  }
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(`✅ 已強制存入 ${pending.items.length} 筆`),
  ]);
}

async function handleOcrSaveSingle(
  env: Env,
  replyToken: string,
  userId: string,
  idxStr: string | undefined,
): Promise<void> {
  const idx = Number(idxStr);
  if (!Number.isFinite(idx)) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [textMsg('❌ 操作失敗。')]);
    return;
  }
  const pending = await takePending(env.DB, userId);
  if (!pending || pending.kind !== 'ocr_batch' || idx < 0 || idx >= pending.items.length) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ OCR 資料已過期，請重新傳送圖片。'),
    ]);
    return;
  }
  const item = pending.items[idx]!;
  await insertCoupon(env.DB, userId, item.name, item.date, item.category);
  const cat = isCategory(item.category) ? item.category : DEFAULT_CATEGORY;
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(`💾 已存入：${cat.split(' ')[0]} ${item.name}`),
  ]);
}

async function handleOcrSaveAll(env: Env, replyToken: string, userId: string): Promise<void> {
  const pending = await takePending(env.DB, userId);
  if (!pending || pending.kind !== 'ocr_batch') {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⚠️ OCR 資料已過期，請重新傳送圖片。'),
    ]);
    return;
  }
  const lines: string[] = [];
  for (const item of pending.items) {
    await insertCoupon(env.DB, userId, item.name, item.date, item.category);
    const emoji = (isCategory(item.category) ? item.category : DEFAULT_CATEGORY).split(' ')[0];
    lines.push(`${emoji} ${item.name}`);
  }
  await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
    textMsg(`💾 已存入 ${pending.items.length} 張票券！\n\n${lines.join('\n')}`),
  ]);
}
