-- 將所有 INTEGER unix timestamp 欄位改成 TEXT 'yyyy-MM-dd HH:mm:ss'，
-- 統一以 UTC+8 儲存。
-- SQLite 不支援直接改欄位型別，所以重建三張表並把舊資料 strftime 轉換過去。

-- ---------- users ----------
CREATE TABLE users_new (
  line_user_id     TEXT PRIMARY KEY,
  agreed_at        TEXT NOT NULL,
  last_request_at  TEXT,
  created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours'))
);

INSERT INTO users_new (line_user_id, agreed_at, last_request_at, created_at)
SELECT
  line_user_id,
  strftime('%Y-%m-%d %H:%M:%S', agreed_at, 'unixepoch', '+8 hours'),
  CASE WHEN last_request_at IS NULL THEN NULL
       ELSE strftime('%Y-%m-%d %H:%M:%S', last_request_at, 'unixepoch', '+8 hours')
  END,
  strftime('%Y-%m-%d %H:%M:%S', created_at, 'unixepoch', '+8 hours')
FROM users;

DROP TABLE users;
ALTER TABLE users_new RENAME TO users;

-- ---------- coupons ----------
CREATE TABLE coupons_new (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id      TEXT    NOT NULL,
  name         TEXT    NOT NULL,
  expire_date  TEXT    NOT NULL,
  category     TEXT    NOT NULL,
  status       TEXT    NOT NULL DEFAULT 'active',
  created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours')),
  used_at      TEXT,
  quantity     INTEGER NOT NULL DEFAULT 1
);

INSERT INTO coupons_new (id, user_id, name, expire_date, category, status, created_at, used_at, quantity)
SELECT
  id,
  user_id,
  name,
  expire_date,
  category,
  status,
  strftime('%Y-%m-%d %H:%M:%S', created_at, 'unixepoch', '+8 hours'),
  CASE WHEN used_at IS NULL THEN NULL
       ELSE strftime('%Y-%m-%d %H:%M:%S', used_at, 'unixepoch', '+8 hours')
  END,
  quantity
FROM coupons;

DROP TABLE coupons;
ALTER TABLE coupons_new RENAME TO coupons;

CREATE INDEX IF NOT EXISTS idx_coupons_user_status
  ON coupons(user_id, status, expire_date);
CREATE INDEX IF NOT EXISTS idx_coupons_expire
  ON coupons(expire_date, status);
CREATE INDEX IF NOT EXISTS idx_coupons_name
  ON coupons(user_id, name);

-- ---------- pending_actions ----------
CREATE TABLE pending_actions_new (
  user_id     TEXT PRIMARY KEY,
  kind        TEXT NOT NULL,
  payload     TEXT NOT NULL,
  expires_at  TEXT NOT NULL
);

INSERT INTO pending_actions_new (user_id, kind, payload, expires_at)
SELECT
  user_id,
  kind,
  payload,
  strftime('%Y-%m-%d %H:%M:%S', expires_at, 'unixepoch', '+8 hours')
FROM pending_actions;

DROP TABLE pending_actions;
ALTER TABLE pending_actions_new RENAME TO pending_actions;

CREATE INDEX IF NOT EXISTS idx_pending_expires
  ON pending_actions(expires_at);
