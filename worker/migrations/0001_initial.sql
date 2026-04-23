-- 使用者（僅記錄已同意條款的人，未同意者不落 DB）
CREATE TABLE IF NOT EXISTS users (
  line_user_id     TEXT PRIMARY KEY,
  agreed_at        INTEGER NOT NULL,
  last_request_at  INTEGER,
  created_at       INTEGER NOT NULL DEFAULT (unixepoch())
);

-- 優惠券主表
CREATE TABLE IF NOT EXISTS coupons (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id      TEXT    NOT NULL,
  name         TEXT    NOT NULL,
  expire_date  TEXT    NOT NULL,                -- 'YYYY-MM-DD'，無期限用 '9999-12-31'
  category     TEXT    NOT NULL,
  status       TEXT    NOT NULL DEFAULT 'active', -- 'active' | 'used' | 'deleted'
  created_at   INTEGER NOT NULL DEFAULT (unixepoch()),
  used_at      INTEGER
);

-- 查詢主要走 (user_id, status, expire_date)
CREATE INDEX IF NOT EXISTS idx_coupons_user_status
  ON coupons(user_id, status, expire_date);

-- 到期通知 cron 走 (expire_date, status)
CREATE INDEX IF NOT EXISTS idx_coupons_expire
  ON coupons(expire_date, status);

-- 模糊搜尋用（D1 支援 LIKE，量大可再升級 FTS5）
CREATE INDEX IF NOT EXISTS idx_coupons_name
  ON coupons(user_id, name);

-- 取代舊 CacheService 的 pending state
-- kind: 'category' (手動記錄等選類別) | 'force_save' | 'ocr_batch'
CREATE TABLE IF NOT EXISTS pending_actions (
  user_id     TEXT    PRIMARY KEY,
  kind        TEXT    NOT NULL,
  payload     TEXT    NOT NULL,       -- JSON
  expires_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_expires
  ON pending_actions(expires_at);
