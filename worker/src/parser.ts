import { UNLIMITED_DATE } from './config.js';
import type { ParsedEntry } from './types.js';

const UNLIMITED_TOKENS = ['永久', '無', '9999/12/31', '9999-12-31'];

/** 解析「名稱 日期」格式。支援 2026/01/01、2026-01-01、2026.01.01。張數另外透過互動式訊息詢問。 */
export function parseEntry(text: string): ParsedEntry | null {
  const t = text.trim();
  const lastSpace = t.lastIndexOf(' ');
  if (lastSpace === -1) return null;

  const nameRaw = t.substring(0, lastSpace).trim().replace(/\s+/g, ' ');
  const dateRaw = t.substring(lastSpace + 1).trim();
  if (!nameRaw) return null;

  if (UNLIMITED_TOKENS.some((s) => dateRaw.includes(s))) {
    return { name: nameRaw, date: UNLIMITED_DATE, displayDate: '無期限' };
  }

  const normalized = dateRaw.replace(/[./]/g, '-');
  const d = new Date(normalized);
  if (isNaN(d.getTime())) return null;

  const iso = toIsoDate(d);
  return { name: nameRaw, date: iso, displayDate: iso };
}

/** 轉成 yyyy-MM-dd（GMT+8）。Workers 沒有 GAS 的 Utilities.formatDate。 */
export function toIsoDate(d: Date): string {
  // 用台灣時區顯示：UTC + 8 小時
  const tw = new Date(d.getTime() + 8 * 60 * 60 * 1000);
  const y = tw.getUTCFullYear();
  const m = String(tw.getUTCMonth() + 1).padStart(2, '0');
  const day = String(tw.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** 轉成 'yyyy-MM-dd HH:mm:ss'（GMT+8），DB 所有 timestamp 欄位都用這個格式。 */
export function formatDateTimeTw(d: Date): string {
  const tw = new Date(d.getTime() + 8 * 60 * 60 * 1000);
  const y = tw.getUTCFullYear();
  const M = String(tw.getUTCMonth() + 1).padStart(2, '0');
  const D = String(tw.getUTCDate()).padStart(2, '0');
  const h = String(tw.getUTCHours()).padStart(2, '0');
  const m = String(tw.getUTCMinutes()).padStart(2, '0');
  const s = String(tw.getUTCSeconds()).padStart(2, '0');
  return `${y}-${M}-${D} ${h}:${m}:${s}`;
}

/** 現在時間（UTC+8）字串，可加上 secondsOffset 取得未來/過去時間。 */
export function nowTwString(secondsOffset: number = 0): string {
  return formatDateTimeTw(new Date(Date.now() + secondsOffset * 1000));
}

/** 解析 postback 的 query-string */
export function parsePostbackParams(data: string): Record<string, string> {
  const params: Record<string, string> = {};
  for (const pair of data.split('&')) {
    const idx = pair.indexOf('=');
    if (idx === -1) continue;
    params[pair.substring(0, idx)] = decodeURIComponent(pair.substring(idx + 1));
  }
  return params;
}

/** 顯示用：短日期（當年顯示 MM/DD，跨年顯示 yyyy/MM/dd，無期限顯示「無期限」） */
export function formatCouponDate(iso: string, today: Date): string {
  if (iso.startsWith('9999')) return '無期限';
  const [y, m, d] = iso.split('-');
  const twToday = new Date(today.getTime() + 8 * 60 * 60 * 1000);
  if (Number(y) !== twToday.getUTCFullYear()) return `${y}/${m}/${d}`;
  return `${m}/${d}`;
}
