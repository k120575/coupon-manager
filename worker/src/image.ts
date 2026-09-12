import {
  CATEGORIES,
  DEFAULT_CATEGORY,
  MAX_QUICK_REPLY,
  UNLIMITED_DATE,
  isCategory,
} from './config.js';
import type { Category } from './config.js';
import { putPending } from './db.js';
import { lineFetchContent, lineReply } from './line.js';
import { qrMessage, qrPostback, textMsg } from './messages.js';
import { parseEntry } from './parser.js';
import { generateTextFromImage, resolveOcrModels } from './gemini.js';
import type { Env } from './env.js';

interface OcrItem {
  name: string;
  date: string;
  category: Category;
  quantity: number;
}

export async function handleImageOcr(
  env: Env,
  replyToken: string,
  userId: string,
  messageId: string,
): Promise<void> {
  try {
    const { bytes, contentType } = await lineFetchContent(
      env.LINE_CHANNEL_ACCESS_TOKEN,
      messageId,
    );
    // 模型鏈與 App 端 OCR 共用同一個後台設定 OCR_MODELS（見 gemini.ts）
    const items = await ocrWithGemini(env, bytes, contentType);

    if (items.length === 0) {
      await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
        textMsg('❌ 無法辨識圖片中的票券，請手動輸入。'),
      ]);
      return;
    }

    await putPending(env.DB, userId, { kind: 'ocr_batch', items });

    const displayLines = items.map(
      (i) =>
        `${i.category.split(' ')[0]} ${i.name} (${i.date === UNLIMITED_DATE ? '無期限' : i.date})`,
    );

    const qItems: unknown[] = items.map((item, idx) =>
      qrPostback(
        `存入: ${item.name.slice(0, 10)}`,
        `action=ocr_save_single&idx=${idx}`,
        `存入：${item.name}`,
      ),
    );
    if (items.length > 1) {
      qItems.unshift(qrPostback('🔥 全部存入', 'action=ocr_save_all', '全部存入'));
    }
    qItems.push(qrMessage('❌ 取消', '取消'));

    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      {
        type: 'text',
        text: `🤖 偵測到 ${items.length} 筆票券：\n\n${displayLines.join('\n')}`,
        quickReply: { items: qItems.slice(0, MAX_QUICK_REPLY) },
      },
    ]);
  } catch (e) {
    console.error('OCR error:', e);
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('❌ 圖片辨識失敗，請稍後再試或手動輸入。'),
    ]);
  }
}

async function ocrWithGemini(
  env: Env,
  bytes: ArrayBuffer,
  mimeType: string,
): Promise<OcrItem[]> {
  const categoryList = CATEGORIES.join('、');
  const prompt = `辨識圖中票券/優惠券的名稱、日期與類別。
格式：名稱 日期 類別
類別只能是：${categoryList}
日期格式：2026/01/01（若無日期用「永久」）
多張用 | 分隔。
範例：星巴克買一送一 2026/03/15 🍽️ 餐飲 | 全聯折價券 2026/06/30 🛒 購物`;

  const raw = await generateTextFromImage(env.GEMINI_API_KEY, resolveOcrModels(env), {
    prompt,
    bytes,
    mimeType,
    tag: 'LINE OCR',
  });
  if (!raw) return [];

  return raw
    .split('|')
    .map((segment) => parseOcrSegment(segment.trim()))
    .filter((i): i is OcrItem => i !== null);
}

function parseOcrSegment(s: string): OcrItem | null {
  if (!s) return null;
  const parts = s.split(/\s+/);

  // 嘗試「名稱 日期 emoji 類別」→ 最後兩個 token 合併為類別
  if (parts.length >= 3) {
    const lastTwo = parts.slice(-2).join(' ');
    if (isCategory(lastTwo)) {
      const nameDate = parts.slice(0, -2).join(' ');
      const entry = parseEntry(nameDate);
      if (entry) return { name: entry.name, date: entry.date, category: lastTwo, quantity: 1 };
    }
  }

  const entry = parseEntry(s);
  if (entry) return { name: entry.name, date: entry.date, category: DEFAULT_CATEGORY, quantity: 1 };
  return null;
}
