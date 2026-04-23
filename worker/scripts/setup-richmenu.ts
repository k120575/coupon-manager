/**
 * 一鍵建立 / 上傳 / 套用 LINE Rich Menu。
 *
 * 使用方式：
 *   1. 在 richmenu-generator.html 下載 PNG（預設檔名 richmenu-2500x1686.png）
 *   2. 把 LINE channel access token 設到環境變數（PowerShell）：
 *      $env:LINE_CHANNEL_ACCESS_TOKEN = "你的_token"
 *   3. 執行：
 *      npm run richmenu -- path/to/richmenu-2500x1686.png
 *      （或預設路徑）npm run richmenu
 *
 * 會做三件事：
 *   a. 建立新 rich menu 物件（3x2 佈局）
 *   b. 上傳圖片
 *   c. 設為所有使用者的預設
 *   並順便把舊的 rich menu 刪掉（避免累積）。
 */

import { readFileSync, statSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const LINE_API = 'https://api.line.me/v2/bot';
const LINE_DATA_API = 'https://api-data.line.me/v2/bot';

const token = process.env.LINE_CHANNEL_ACCESS_TOKEN;
if (!token) {
  console.error('❌ 缺少環境變數 LINE_CHANNEL_ACCESS_TOKEN');
  console.error('   PowerShell:  $env:LINE_CHANNEL_ACCESS_TOKEN = "..."');
  process.exit(1);
}

const imgPath = resolve(process.argv[2] ?? '../richmenu-2500x1686.png');
let imgSize: number;
try {
  imgSize = statSync(imgPath).size;
} catch {
  console.error(`❌ 找不到圖片：${imgPath}`);
  console.error('   先用 richmenu-generator.html 下載 PNG/JPG，或提供正確路徑。');
  process.exit(1);
}

const LINE_MAX_BYTES = 1024 * 1024; // 1 MB
if (imgSize > LINE_MAX_BYTES) {
  const mb = (imgSize / 1024 / 1024).toFixed(2);
  console.error(`❌ 圖片 ${mb} MB，超過 LINE 上限 1 MB`);
  console.error('   解法：在 richmenu-generator.html 改按「下載 JPG」（通常 200–500 KB）');
  process.exit(1);
}

const richMenuBody = {
  size: { width: 2500, height: 1686 },
  selected: true,
  name: '優惠券管家主選單',
  chatBarText: '📋 開啟選單',
  areas: [
    { bounds: { x: 0,    y: 0,   width: 833, height: 843 }, action: { type: 'message', text: '📋 查詢票券' } },
    { bounds: { x: 833,  y: 0,   width: 834, height: 843 }, action: { type: 'message', text: '🏷️ 分類查詢' } },
    { bounds: { x: 1667, y: 0,   width: 833, height: 843 }, action: { type: 'message', text: '➕ 記錄優惠券' } },
    { bounds: { x: 0,    y: 843, width: 833, height: 843 }, action: { type: 'message', text: '✅ 使用票券' } },
    { bounds: { x: 833,  y: 843, width: 834, height: 843 }, action: { type: 'message', text: '🗑️ 刪除票券' } },
    { bounds: { x: 1667, y: 843, width: 833, height: 843 }, action: { type: 'message', text: '❓ 幫助' } },
  ],
} as const;

async function api<T>(path: string, init: RequestInit): Promise<T> {
  const res = await fetch(`${LINE_API}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...(init.headers ?? {}) },
  });
  const body = await res.text();
  if (!res.ok) throw new Error(`LINE ${path} ${res.status}: ${body}`);
  return body ? (JSON.parse(body) as T) : (undefined as T);
}

async function listExistingMenus(): Promise<Array<{ richMenuId: string; name: string }>> {
  const { richmenus } = await api<{ richmenus: Array<{ richMenuId: string; name: string }> }>(
    '/richmenu/list',
    { method: 'GET' },
  );
  return richmenus;
}

async function createMenu(): Promise<string> {
  const { richMenuId } = await api<{ richMenuId: string }>('/richmenu', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(richMenuBody),
  });
  return richMenuId;
}

async function uploadImage(richMenuId: string): Promise<void> {
  const bytes = readFileSync(imgPath);
  const mime = imgPath.toLowerCase().endsWith('.jpg') || imgPath.toLowerCase().endsWith('.jpeg')
    ? 'image/jpeg'
    : 'image/png';
  const res = await fetch(`${LINE_DATA_API}/richmenu/${richMenuId}/content`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': mime },
    body: bytes,
  });
  if (!res.ok) throw new Error(`upload image ${res.status}: ${await res.text()}`);
}

async function setDefault(richMenuId: string): Promise<void> {
  await api(`/user/all/richmenu/${richMenuId}`, { method: 'POST' });
}

async function deleteMenu(richMenuId: string): Promise<void> {
  await api(`/richmenu/${richMenuId}`, { method: 'DELETE' });
}

async function main() {
  console.log(`📷 圖片：${basename(imgPath)}`);
  const existing = await listExistingMenus();
  if (existing.length > 0) console.log(`🗂️  發現 ${existing.length} 個舊 rich menu，稍後會清掉`);

  console.log('⏳ 建立 rich menu...');
  const richMenuId = await createMenu();
  console.log(`   ✓ richMenuId = ${richMenuId}`);

  console.log('⏳ 上傳圖片...');
  await uploadImage(richMenuId);

  console.log('⏳ 設為預設...');
  await setDefault(richMenuId);

  for (const m of existing) {
    try {
      await deleteMenu(m.richMenuId);
      console.log(`   🗑️ 清除舊選單 ${m.name}`);
    } catch (e) {
      console.warn(`   ⚠️ 無法清除 ${m.richMenuId}: ${(e as Error).message}`);
    }
  }

  console.log('🎉 完成！在 LINE 聊天室就能看到新選單（可能要重啟 App 或等幾秒）');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
