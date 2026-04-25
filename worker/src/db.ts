import { PENDING_TTL_SECONDS, RATE_LIMIT_SECONDS, STATUS } from './config.js';
import type { CouponRow, PendingPayload } from './types.js';

const now = (): number => Math.floor(Date.now() / 1000);

// ---------- users / 同意 ----------

export async function hasAgreed(db: D1Database, userId: string): Promise<boolean> {
  const row = await db
    .prepare('SELECT 1 FROM users WHERE line_user_id = ? LIMIT 1')
    .bind(userId)
    .first();
  return row !== null;
}

export async function recordAgreement(db: D1Database, userId: string): Promise<void> {
  await db
    .prepare(
      `INSERT INTO users (line_user_id, agreed_at)
       VALUES (?, ?)
       ON CONFLICT(line_user_id) DO NOTHING`,
    )
    .bind(userId, now())
    .run();
}

/** 回傳 true 代表被限流。同時更新 last_request_at。 */
export async function checkAndBumpRateLimit(
  db: D1Database,
  userId: string,
): Promise<boolean> {
  const t = now();
  const threshold = t - RATE_LIMIT_SECONDS;
  const row = await db
    .prepare('SELECT last_request_at FROM users WHERE line_user_id = ?')
    .bind(userId)
    .first<{ last_request_at: number | null }>();

  if (row?.last_request_at && row.last_request_at > threshold) return true;

  // 只有已同意的 user 才在表裡；limit 只在已同意後才走到這
  await db
    .prepare('UPDATE users SET last_request_at = ? WHERE line_user_id = ?')
    .bind(t, userId)
    .run();
  return false;
}

// ---------- coupons ----------

export async function insertCoupon(
  db: D1Database,
  userId: string,
  name: string,
  date: string,
  category: string,
  quantity: number = 1,
): Promise<number> {
  const qty = Math.max(1, Math.min(999, Math.floor(quantity)));
  const res = await db
    .prepare(
      `INSERT INTO coupons (user_id, name, expire_date, category, status, quantity)
       VALUES (?, ?, ?, ?, ?, ?)`,
    )
    .bind(userId, name, date, category, STATUS.ACTIVE, qty)
    .run();
  return Number(res.meta.last_row_id);
}

export async function isDuplicate(
  db: D1Database,
  userId: string,
  name: string,
  date: string,
): Promise<boolean> {
  const row = await db
    .prepare(
      `SELECT 1 FROM coupons
       WHERE user_id = ? AND name = ? AND expire_date = ? AND status = ?
       LIMIT 1`,
    )
    .bind(userId, name, date, STATUS.ACTIVE)
    .first();
  return row !== null;
}

export type ListFilter =
  | 'active_valid'
  | 'active_valid_search'
  | 'active_expired'
  | 'used'
  | 'delete_mode';

export async function listCoupons(
  db: D1Database,
  userId: string,
  filter: ListFilter,
  categoryFilter: string | null,
  todayIso: string,
): Promise<CouponRow[]> {
  let sql = `SELECT * FROM coupons WHERE user_id = ?`;
  const params: (string | number)[] = [userId];

  if (filter === 'active_valid' || filter === 'active_valid_search' || filter === 'delete_mode') {
    sql += ` AND status = 'active' AND expire_date >= ?`;
    params.push(todayIso);
  } else if (filter === 'active_expired') {
    sql += ` AND status = 'active' AND expire_date < ?`;
    params.push(todayIso);
  } else if (filter === 'used') {
    sql += ` AND status = 'used'`;
  }

  if (categoryFilter) {
    sql += ` AND category = ?`;
    params.push(categoryFilter);
  }

  sql += ` ORDER BY expire_date ASC, id ASC`;

  const res = await db
    .prepare(sql)
    .bind(...params)
    .all<CouponRow>();
  return res.results ?? [];
}

export async function fuzzySearchActive(
  db: D1Database,
  userId: string,
  keyword: string,
  limit: number,
): Promise<CouponRow[]> {
  const res = await db
    .prepare(
      `SELECT * FROM coupons
       WHERE user_id = ? AND status = 'active' AND name LIKE ?
       ORDER BY expire_date ASC
       LIMIT ?`,
    )
    .bind(userId, `%${keyword}%`, limit + 1)
    .all<CouponRow>();
  return res.results ?? [];
}

/**
 * 使用一張券：quantity > 1 就扣一張仍 active，剩最後一張才變 used。
 * 回傳使用後的狀態（含剩餘張數）；找不到或已用完回 null。
 */
export async function useOneCoupon(
  db: D1Database,
  id: number,
  userId: string,
): Promise<{ name: string; remaining: number; isLast: boolean } | null> {
  const current = await db
    .prepare(
      `SELECT * FROM coupons WHERE id = ? AND user_id = ? AND status = 'active' LIMIT 1`,
    )
    .bind(id, userId)
    .first<CouponRow>();
  if (!current) return null;

  if (current.quantity > 1) {
    await db
      .prepare(
        `UPDATE coupons SET quantity = quantity - 1 WHERE id = ? AND status = 'active' AND quantity > 1`,
      )
      .bind(id)
      .run();
    return { name: current.name, remaining: current.quantity - 1, isLast: false };
  }

  await db
    .prepare(
      `UPDATE coupons SET status = 'used', used_at = ?, quantity = 0 WHERE id = ? AND status = 'active'`,
    )
    .bind(now(), id)
    .run();
  return { name: current.name, remaining: 0, isLast: true };
}

/** 刪除：不管張數，整筆標記為 deleted。 */
export async function getCouponName(
  db: D1Database,
  id: number,
  userId: string,
): Promise<string | null> {
  const row = await db
    .prepare(`SELECT name FROM coupons WHERE id = ? AND user_id = ? LIMIT 1`)
    .bind(id, userId)
    .first<{ name: string }>();
  return row?.name ?? null;
}

export async function deleteCoupon(
  db: D1Database,
  id: number,
  userId: string,
): Promise<CouponRow | null> {
  const current = await db
    .prepare(
      `SELECT * FROM coupons WHERE id = ? AND user_id = ? AND status = 'active' LIMIT 1`,
    )
    .bind(id, userId)
    .first<CouponRow>();
  if (!current) return null;

  await db
    .prepare(`UPDATE coupons SET status = 'deleted' WHERE id = ? AND status = 'active'`)
    .bind(id)
    .run();
  return { ...current, status: 'deleted' };
}

// ---------- 到期通知 ----------

export async function listCouponsExpiringIn(
  db: D1Database,
  targetDate: string,
): Promise<CouponRow[]> {
  const res = await db
    .prepare(
      `SELECT * FROM coupons
       WHERE status = 'active' AND expire_date = ?`,
    )
    .bind(targetDate)
    .all<CouponRow>();
  return res.results ?? [];
}

// ---------- pending actions ----------

export async function putPending(
  db: D1Database,
  userId: string,
  payload: PendingPayload,
): Promise<void> {
  const expires = now() + PENDING_TTL_SECONDS;
  await db
    .prepare(
      `INSERT INTO pending_actions (user_id, kind, payload, expires_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET
         kind = excluded.kind,
         payload = excluded.payload,
         expires_at = excluded.expires_at`,
    )
    .bind(userId, payload.kind, JSON.stringify(payload), expires)
    .run();
}

/** 讀取但不刪除（用於先判斷類型，再決定要不要消耗）。 */
export async function peekPending(
  db: D1Database,
  userId: string,
): Promise<PendingPayload | null> {
  const row = await db
    .prepare(`SELECT payload, expires_at FROM pending_actions WHERE user_id = ?`)
    .bind(userId)
    .first<{ payload: string; expires_at: number }>();
  if (!row || row.expires_at < now()) return null;
  return JSON.parse(row.payload) as PendingPayload;
}

export async function takePending(
  db: D1Database,
  userId: string,
): Promise<PendingPayload | null> {
  const row = await db
    .prepare(
      `SELECT payload, expires_at FROM pending_actions WHERE user_id = ?`,
    )
    .bind(userId)
    .first<{ payload: string; expires_at: number }>();
  if (!row) return null;

  await db
    .prepare('DELETE FROM pending_actions WHERE user_id = ?')
    .bind(userId)
    .run();

  if (row.expires_at < now()) return null;
  return JSON.parse(row.payload) as PendingPayload;
}

export async function cleanupExpiredPending(db: D1Database): Promise<void> {
  await db
    .prepare('DELETE FROM pending_actions WHERE expires_at < ?')
    .bind(now())
    .run();
}
