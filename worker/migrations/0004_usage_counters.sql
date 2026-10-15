-- 每日用量計數（成本閘）。scope 例：
--   'line:u:<lineUserId>'  LINE 圖片辨識，每人每日
--   'line:global'          LINE 圖片辨識，全站每日
--   'app:ip:<ip>'          App /ocr，每個來源 IP 每日
--   'app:global'           App /ocr，全站每日
-- day 為台灣日期 'YYYY-MM-DD'；cron 每天清掉 7 天前的列。
CREATE TABLE IF NOT EXISTS usage_counters (
  scope  TEXT    NOT NULL,
  day    TEXT    NOT NULL,
  count  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (scope, day)
);
