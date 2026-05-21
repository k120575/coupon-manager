/**
 * App 端 OCR API 端點：POST /ocr
 *
 * 跟 LINE bot 的 OCR 是兩套 prompt：
 * - LINE bot 用 6 個粗分類（worker/src/config.ts 的 CATEGORIES）
 * - App 用 14 個細分類（跟 Kotlin BuiltInCategory 對齊）
 *
 * 兩套共存不互相干擾——LINE bot 那邊不動。
 *
 * 回應格式：JSON
 * {
 *   "name": "燒肉同話",
 *   "expireDate": "2026-12-31",      // ISO 格式；無到期日 = "9999-12-31"；無法辨識 = null
 *   "categoryId": "dining"            // 14 個 ID 其中一個；不確定 = null
 * }
 */

import type { Env } from './env.js';

interface AppOcrResponse {
  name: string | null;
  expireDate: string | null;
  categoryId: string | null;
}

/**
 * App 端 14 個分類 ID，跟 Kotlin BuiltInCategory enum 對齊。
 * 任何改動都要同步改 App。
 */
const APP_CATEGORY_IDS = [
  'dining',
  'movie',
  'shopping',
  'beauty',
  'massage',
  'fitness',
  'medical',
  'pet',
  'education',
  'tech',
  'lodging',
  'transport',
  'coffee',
  'other',
] as const;

type AppCategoryId = (typeof APP_CATEGORY_IDS)[number];

export async function handleAppOcr(request: Request, env: Env): Promise<Response> {
  // === Auth ===
  const token = request.headers.get('x-coupy-token');
  if (!token || token !== env.OCR_CLIENT_SECRET) {
    return jsonResponse({ error: 'unauthorized' }, 401);
  }

  // === 讀圖 ===
  const contentType = request.headers.get('content-type') ?? 'image/jpeg';
  if (!contentType.startsWith('image/')) {
    return jsonResponse({ error: 'unsupported_content_type' }, 400);
  }

  const bytes = await request.arrayBuffer();
  if (bytes.byteLength === 0) {
    return jsonResponse({ error: 'empty_body' }, 400);
  }
  if (bytes.byteLength > 5 * 1024 * 1024) {
    // Gemini Vision 對單張圖約 20MB 限制，但我們保守 5MB 避免 Worker 記憶體吃緊
    return jsonResponse({ error: 'image_too_large' }, 413);
  }

  // === 呼叫 Gemini ===
  try {
    const result = await callGeminiForApp(env.GEMINI_API_KEY, bytes, contentType);
    return jsonResponse(result, 200);
  } catch (e) {
    console.error('App OCR error:', e);
    return jsonResponse({ error: 'ocr_failed', message: String(e) }, 500);
  }
}

async function callGeminiForApp(
  apiKey: string,
  bytes: ArrayBuffer,
  mimeType: string,
): Promise<AppOcrResponse> {
  const base64 = arrayBufferToBase64(bytes);

  const prompt = `辨識圖中票券/優惠券的資訊。回傳純 JSON，不要 markdown 包裹。

格式：
{
  "name": "票券名稱（如店家+品項，例如「全家濃湯」「燒肉同話雙人套餐」）",
  "expireDate": "YYYY-MM-DD（ISO 8601；無到期日寫 9999-12-31）",
  "categoryId": "從下方 14 個 ID 選一個最接近的"
}

categoryId 可用值（括號內是該分類涵蓋的典型店家/品項）：
- dining（餐飲、火鍋、燒肉、便當、餐廳、速食）
- movie（電影票、影城）
- shopping（零售、超商、賣場、生活用品、量販）
- beauty（剪髮、染髮、美甲、美容、保養、SPA 美容類）
- massage（按摩、推拿、足療）
- fitness（健身房、瑜珈、運動、有氧）
- medical（診所、醫院、藥局、體檢）
- pet（寵物用品、寵物美容、寵物醫療）
- education（補習班、課程、線上學習）
- tech（3C、電子產品、手機門市、家電）
- lodging（飯店、旅館、民宿、住宿）
- transport（交通、停車場、計程車、加油站）
- coffee（咖啡、手搖飲、飲料店、茶飲）
- other（不確定或不屬於以上）

若圖中沒有可辨識的票券資訊，全部欄位回 null。
若辨識到多張，只回傳第一張的資訊。`;

  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey.trim()}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [
        {
          parts: [{ text: prompt }, { inline_data: { mime_type: mimeType, data: base64 } }],
        },
      ],
      generationConfig: {
        responseMimeType: 'application/json',
        temperature: 0.1, // 低溫度讓辨識結果穩定
      },
    }),
  });

  if (!res.ok) {
    throw new Error(`Gemini ${res.status}: ${await res.text()}`);
  }

  const json = (await res.json()) as {
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };
  const raw = json.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? '';
  if (!raw) {
    return { name: null, expireDate: null, categoryId: null };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<AppOcrResponse>;
    return {
      name: typeof parsed.name === 'string' && parsed.name.trim() !== '' ? parsed.name.trim() : null,
      expireDate: normalizeDate(parsed.expireDate),
      categoryId: normalizeCategoryId(parsed.categoryId),
    };
  } catch {
    // Gemini 偶爾還是會回非 JSON 文字
    return { name: null, expireDate: null, categoryId: null };
  }
}

function normalizeDate(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const s = value.trim();
  if (s === '' || s === 'null') return null;
  // 接受 YYYY-MM-DD 與 YYYY/MM/DD，統一輸出 YYYY-MM-DD
  const m = s.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
  if (!m) return null;
  const yyyy = m[1]!;
  const mm = m[2]!.padStart(2, '0');
  const dd = m[3]!.padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function normalizeCategoryId(value: unknown): AppCategoryId | null {
  if (typeof value !== 'string') return null;
  const s = value.trim().toLowerCase();
  if ((APP_CATEGORY_IDS as readonly string[]).includes(s)) {
    return s as AppCategoryId;
  }
  return null;
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}
