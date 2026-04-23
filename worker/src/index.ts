import { checkAndBumpRateLimit, hasAgreed } from './db.js';
import type { Env } from './env.js';
import { handleImageOcr } from './image.js';
import { lineReply } from './line.js';
import { consentMessage, textMsg } from './messages.js';
import { runDailyNotify } from './notify.js';
import { handlePostback } from './postback.js';
import { verifyLineSignature } from './signature.js';
import { handleTextMessage } from './text.js';
import type { LineEvent, LineWebhookBody } from './types.js';

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === 'GET' && url.pathname === '/health') {
      return new Response('ok');
    }

    if (request.method !== 'POST' || url.pathname !== '/webhook') {
      return new Response('Not Found', { status: 404 });
    }

    const rawBody = await request.text();
    const signature = request.headers.get('x-line-signature');
    const ok = await verifyLineSignature(rawBody, signature, env.LINE_CHANNEL_SECRET);
    if (!ok) {
      console.warn('Invalid signature');
      return new Response('invalid signature', { status: 401 });
    }

    // 立即 ACK，後續處理放 waitUntil；LINE 的 reply token 有 10 秒壽命，
    // 我們不阻塞 LINE platform 的回應。
    ctx.waitUntil(dispatchWebhook(env, rawBody));
    return new Response('ok');
  },

  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(runDailyNotify(env));
  },
};

async function dispatchWebhook(env: Env, rawBody: string): Promise<void> {
  let body: LineWebhookBody;
  try {
    body = JSON.parse(rawBody) as LineWebhookBody;
  } catch (e) {
    console.error('Invalid webhook JSON:', e);
    return;
  }

  for (const event of body.events ?? []) {
    try {
      await dispatchEvent(env, event);
    } catch (err) {
      console.error('Event handler error:', err, JSON.stringify(event));
    }
  }
}

async function dispatchEvent(env: Env, event: LineEvent): Promise<void> {
  const userId = event.source?.userId;
  const replyToken = 'replyToken' in event ? event.replyToken : undefined;
  if (!userId || !replyToken) return;

  // Postback 的 'agree' 例外處理：允許未同意者觸發
  if (event.type === 'postback') {
    const pData = (event as { postback: { data: string } }).postback.data;
    if (pData.startsWith('action=agree')) {
      await handlePostback(env, replyToken, userId, pData);
      return;
    }
  }

  // 未同意條款 → 回傳同意訊息（除了 agree 之外的所有事件）
  const agreed = await hasAgreed(env.DB, userId);
  if (!agreed) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [consentMessage()]);
    return;
  }

  // Rate limit
  if (await checkAndBumpRateLimit(env.DB, userId)) {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [
      textMsg('⏳ 操作太頻繁，請稍後再試。'),
    ]);
    return;
  }

  if (event.type === 'postback') {
    const pData = (event as { postback: { data: string } }).postback.data;
    await handlePostback(env, replyToken, userId, pData);
    return;
  }

  if (event.type === 'message') {
    const msg = (event as { message: { type: string; id: string; text?: string } }).message;
    if (msg.type === 'text' && typeof msg.text === 'string') {
      await handleTextMessage(env, replyToken, userId, msg.text);
      return;
    }
    if (msg.type === 'image') {
      await handleImageOcr(env, replyToken, userId, msg.id);
      return;
    }
  }

  if (event.type === 'follow') {
    await lineReply(env.LINE_CHANNEL_ACCESS_TOKEN, replyToken, [consentMessage()]);
    return;
  }
}
