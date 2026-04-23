-- 加上張數欄位：一張同樣的券可以記一筆但有多張
-- 既有資料預設 1 張
ALTER TABLE coupons ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1;
