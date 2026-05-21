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
  quantity: number;
  created_at: string;
  used_at: string | null;
}

export interface ParsedEntry {
  name: string;
  date: string;        // 'YYYY-MM-DD'
  displayDate: string; // 人類可讀
}

// pending_actions.payload 型態：
//   quantity   — 等使用者選張數
//   category   — 已選完張數，等使用者選類別
//   force_save — 使用者遇到重複、確認幫存，轉入 quantity 步驟
//   ocr_batch  — OCR 多筆暫存（每筆已有類別與預設張數 1）
//   action_qty — 使用/刪除多張券時，等使用者輸入要操作幾張
export type PendingPayload =
  | { kind: 'quantity'; name: string; date: string }
  | { kind: 'category'; name: string; date: string; quantity: number }
  | { kind: 'force_save'; name: string; date: string }
  | {
      kind: 'ocr_batch';
      items: Array<{ name: string; date: string; category: Category; quantity: number }>;
    }
  | { kind: 'action_qty'; id: number; mode: 'use' | 'delete' };
