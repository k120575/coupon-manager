/**
 * 把 Google Sheets 匯出的兩張 CSV 轉成 SQL，供 wrangler d1 execute 匯入。
 *
 * 使用方式：
 *   1. 在 Google Sheets 把 users 分頁「檔案 → 下載 → CSV」存成 users.csv
 *   2. 同上，data 分頁存成 data.csv
 *   3. 執行：
 *      npx tsx scripts/import-from-csv.ts --users users.csv --data data.csv > import.sql
 *   4. 執行：
 *      wrangler d1 execute coupon-manager --remote --file=./import.sql
 *
 * CSV 欄位（無標題）：
 *   users.csv:  userId, agreed(true/false)
 *   data.csv:   userId, name, date, status, category
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    users: { type: 'string' },
    data: { type: 'string' },
    out: { type: 'string' },
  },
});

if (!values.users || !values.data) {
  console.error(
    'Usage: tsx scripts/import-from-csv.ts --users users.csv --data data.csv [--out import.sql]',
  );
  process.exit(1);
}

// 極簡 CSV 解析：處理引號與跳脫。不依賴外部套件。
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuote = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuote) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (c === '"') {
        inQuote = false;
      } else {
        field += c;
      }
    } else {
      if (c === '"') inQuote = true;
      else if (c === ',') {
        row.push(field);
        field = '';
      } else if (c === '\n') {
        row.push(field);
        rows.push(row);
        row = [];
        field = '';
      } else if (c !== '\r') {
        field += c;
      }
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((r) => r.some((f) => f.length > 0));
}

function esc(s: string): string {
  return `'${s.replace(/'/g, "''")}'`;
}

function toIsoDate(input: string): string {
  const t = input.trim();
  if (!t || t === '9999/12/31' || t === '9999-12-31' || t === '永久' || t === '無') {
    return '9999-12-31';
  }
  // 支援 2026/1/1, 2026-1-1, 2026.1.1
  const m = t.match(/^(\d{4})[\/\-.](\d{1,2})[\/\-.](\d{1,2})/);
  if (!m) throw new Error(`Bad date: ${t}`);
  const [, y, mo, d] = m;
  return `${y}-${mo!.padStart(2, '0')}-${d!.padStart(2, '0')}`;
}

const usersCsv = readFileSync(values.users, 'utf8');
const dataCsv = readFileSync(values.data, 'utf8');
const userRows = stripHeaderIfAny(parseCsv(usersCsv));
const dataRows = stripHeaderIfAny(parseCsv(dataCsv));

/**
 * 如果第一列看起來像標題（不是 LINE userId 格式），就拿掉。
 * LINE userId 固定 33 字元、以 U 開頭。
 */
function stripHeaderIfAny(rows: string[][]): string[][] {
  if (rows.length === 0) return rows;
  const first = rows[0]![0] ?? '';
  const looksLikeUserId = /^U[0-9a-f]{32}$/i.test(first);
  return looksLikeUserId ? rows : rows.slice(1);
}

const out: string[] = [];

// users: 舊 Sheet 只有「已同意」的使用者會進表；agreed_at 不明，用 0 填。
for (const [uid, agreed] of userRows) {
  if (!uid) continue;
  const ok = String(agreed).toLowerCase() === 'true';
  if (!ok) continue;
  out.push(
    `INSERT INTO users (line_user_id, agreed_at) VALUES (${esc(uid)}, 0) ON CONFLICT DO NOTHING;`,
  );
}

// coupons
let imported = 0;
let skipped = 0;
for (const cells of dataRows) {
  const [uid, name, date, status, category] = cells;
  if (!uid || !name || !date) {
    skipped++;
    continue;
  }
  const normalized = {
    status: (status || 'active').trim(),
    category: (category || '📦 其他').trim(),
    date: toIsoDate(date),
  };
  // 確保 user 存在
  out.push(
    `INSERT INTO users (line_user_id, agreed_at) VALUES (${esc(uid)}, 0) ON CONFLICT DO NOTHING;`,
  );
  out.push(
    `INSERT INTO coupons (user_id, name, expire_date, category, status) VALUES (${esc(uid)}, ${esc(name)}, ${esc(normalized.date)}, ${esc(normalized.category)}, ${esc(normalized.status)});`,
  );
  imported++;
}

const sql = out.join('\n');
if (values.out) {
  writeFileSync(values.out, sql, 'utf8');
  console.error(`✅ wrote ${sql.length} bytes to ${values.out}`);
} else {
  console.log(sql);
}
console.error(`-- imported: ${imported} coupons, skipped: ${skipped}`);
