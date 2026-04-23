import type { Category, Status } from './config.js';

// ---------- LINE webhook ----------

export interface LineWebhookBody {
  destination: string;
  events: LineEvent[];
}

export type LineEvent =
  | LineMessageEvent
  | LinePostbackEvent
  | LineFollowEvent
  | { type: string; source?: { userId?: string }; replyToken?: string };

export interface LineSource {
  type: 'user' | 'group' | 'room';
  userId?: string;
}

export interface LineMessageEvent {
  type: 'message';
  replyToken: string;
  source: LineSource;
  message:
    | { type: 'text'; id: string; text: string }
    | { type: 'image'; id: string }
    | { type: string; id: string };
}

export interface LinePostbackEvent {
  type: 'postback';
  replyToken: string;
  source: LineSource;
  postback: { data: string };
}

export interface LineFollowEvent {
  type: 'follow';
  replyToken: string;
  source: LineSource;
}

// ---------- Domain ----------

export interface CouponRow {
  id: number;
  user_id: string;
  name: string;
  expire_date: string;
  category: string;
  status: Status;
  created_at: number;
  used_at: number | null;
}

export interface ParsedEntry {
  name: string;
  date: string;        // 'YYYY-MM-DD'
  displayDate: string; // 人類可讀
}

// pending_actions.payload 的三種型態
export type PendingPayload =
  | { kind: 'category'; name: string; date: string }
  | { kind: 'force_save'; name: string; date: string }
  | { kind: 'ocr_batch'; items: Array<{ name: string; date: string; category: Category }> };
