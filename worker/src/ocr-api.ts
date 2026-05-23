/**
 * App 端 OCR API 端點：POST /ocr
 *
 * 跟 LINE bot 的 OCR 是兩套 prompt：
 * - LINE bot 用 6 個粗分類（worker/src/config.ts 的 CATEGORIES）
 * - App 用 14 個細分類（跟 Kotlin BuiltInCategory 對齊）
 *
 * 兩套共存不互相干擾——LINE bot 那邊不動。
 *
 * 成功回應（HTTP 200）：
 * {
 *   "name": "燒肉同話",
 *   "expireDate": "2026-12-31",      // ISO；無到期日 = "9999-12-31"；無法辨識 = null
 *   "categoryId": "dining"            // 14 個 ID 其中一個；不確定 = null
 * }
 *
 * 失敗回應（HTTP 4xx/5xx）：
 * {
 *   "error": "ai_busy" | "ai_unavailable" | "ai_timeout" | "ai_failed" | "ai_blocked"
 *          | "image_invalid" | "image_too_large" | "bad_request" | "unauthorized"
 *          | "network_error" | "internal_error"
 * }
 * App 端依 error code 翻譯成使用者語言。所有錯誤情境都會被分類到上述其中一個 code。
 */

import type { Env } from './env.js';

interface AppOcrResponse {
  name: string | null;
  expireDate: string | null;
  categoryId: string | null;
}

type OcrErrorCode =
  | 'unauthorized'        // 401  — token 不對
  | 'bad_request'         // 400  — content-type / body 不合法
  | 'image_too_large'     // 413  — > 5MB
  | 'image_invalid'       // 422  — Gemini 回 400（圖片無法解讀）
  | 'ai_blocked'          // 422  — 安全過濾擋掉
  | 'ai_busy'             // 503  — Gemini 429 / 503（rate limit / 過載）
  | 'ai_unavailable'      // 503  — Gemini 401/403/404（API key / model 設定問題）
  | 'ai_timeout'          // 504  — Gemini fetch 超時
  | 'ai_failed'           // 502  — Gemini 其他 5xx / 回應 parse 失敗
  | 'network_error'       // 502  — fetch Gemini 整個 throw（DNS / TLS）
  | 'internal_error';     // 500  — 兜底

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

/**
 * Gemini fetch 超時。設 55s 留 5s 給 Worker 自身 overhead，
 * 比 App 端 readTimeout (60s) 短一點，讓我們先 abort 並回 ai_timeout 而不是讓 App socket timeout。
 */
const GEMINI_TIMEOUT_MS = 55_000;

export async function handleAppOcr(request: Request, env: Env): Promise<Response> {
  // === Auth ===
  const token = request.headers.get('x-coupy-token');
  if (!token || token !== env.OCR_CLIENT_SECRET) {
    return errorResponse('unauthorized', 401);
  }

  // === 讀圖 ===
  const contentType = request.headers.get('content-type') ?? 'image/jpeg';
  if (!contentType.startsWith('image/')) {
    return errorResponse('bad_request', 400);
  }

  let bytes: ArrayBuffer;
  try {
    bytes = await request.arrayBuffer();
  } catch (e) {
    console.error('App OCR: arrayBuffer read failed:', e);
    return errorResponse('bad_request', 400);
  }
  if (bytes.byteLength === 0) {
    return errorResponse('bad_request', 400);
  }
  if (bytes.byteLength > 5 * 1024 * 1024) {
    return errorResponse('image_too_large', 413);
  }

  // === 呼叫 Gemini ===
  try {
    const result = await callGeminiForApp(env.GEMINI_API_KEY, bytes, contentType);
    return jsonResponse(result, 200);
  } catch (e) {
    return geminiErrorToResponse(e);
  }
}

/**
 * 內部錯誤型別——把 Gemini 各種失敗統一包成 (code, status, message)，
 * 在 [geminiErrorToResponse] 那邊轉成對外 HTTP 回應。
 */
class GeminiError extends Error {
  constructor(
    public readonly code: OcrErrorCode,
    public readonly status: number,
    message: string,
  ) {
    super(message);
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

  // AbortController 控制 timeout——Workers 的 fetch 沒有內建 timeout
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);

  let res: Response;
  try {
    res = await fetch(url, {
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
      signal: controller.signal,
    });
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') {
      throw new GeminiError('ai_timeout', 504, 'Gemini fetch timed out');
    }
    throw new GeminiError('network_error', 502, `Gemini fetch threw: ${String(e)}`);
  } finally {
    clearTimeout(timeoutId);
  }

  if (!res.ok) {
    const bodyText = await safeReadText(res);
    const code = mapGeminiStatusToCode(res.status);
    const status = mapGeminiStatusToOurStatus(res.status);
    throw new GeminiError(code, status, `Gemini ${res.status}: ${bodyText.slice(0, 500)}`);
  }

  let json: unknown;
  try {
    json = await res.json();
  } catch (e) {
    throw new GeminiError('ai_failed', 502, `Gemini response not JSON: ${String(e)}`);
  }

  // 偵測 prompt 整個被擋（安全過濾、不雅內容等）
  const blockReason = getString(json, ['promptFeedback', 'blockReason']);
  if (blockReason) {
    throw new GeminiError('ai_blocked', 422, `Gemini prompt blocked: ${blockReason}`);
  }

  // 偵測 candidate 被安全過濾擋（finishReason = SAFETY / RECITATION）
  const finishReason = getString(json, ['candidates', 0, 'finishReason']);
  if (finishReason === 'SAFETY' || finishReason === 'RECITATION') {
    throw new GeminiError('ai_blocked', 422, `Gemini finished: ${finishReason}`);
  }

  const raw = getString(json, ['candidates', 0, 'content', 'parts', 0, 'text'])?.trim() ?? '';
  if (!raw) {
    // Gemini 成功回應但完全沒文字——當成「看不到票券」回全 null，App 端會選擇不扣次數。
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
    // Gemini 偶爾還是會回非 JSON 文字——同樣當「看不到票券」處理
    return { name: null, expireDate: null, categoryId: null };
  }
}

/** Gemini HTTP status → 我們的 error code */
function mapGeminiStatusToCode(status: number): OcrErrorCode {
  if (status === 400) return 'image_invalid';           // 圖片 Gemini 不接受
  if (status === 401 || status === 403) return 'ai_unavailable'; // API key 壞 / 沒權限 / billing
  if (status === 404) return 'ai_unavailable';          // model 名打錯
  if (status === 429) return 'ai_busy';                 // rate limit / quota
  if (status === 503) return 'ai_busy';                 // overloaded
  if (status === 504) return 'ai_timeout';
  if (status >= 500) return 'ai_failed';                // 其他 5xx
  return 'ai_failed';                                    // 其他非預期 4xx
}

/** Gemini HTTP status → 我們對外的 HTTP status */
function mapGeminiStatusToOurStatus(status: number): number {
  if (status === 400) return 422;
  if (status === 401 || status === 403 || status === 404) return 503;
  if (status === 429 || status === 503) return 503;
  if (status === 504) return 504;
  return 502;
}

function geminiErrorToResponse(e: unknown): Response {
  if (e instanceof GeminiError) {
    console.error(`App OCR ${e.code}: ${e.message}`);
    return errorResponse(e.code, e.status);
  }
  console.error('App OCR unexpected error:', e);
  return errorResponse('internal_error', 500);
}

async function safeReadText(res: Response): Promise<string> {
  try {
    return await res.text();
  } catch {
    return '';
  }
}

/** 從巢狀物件安全取出字串，路徑上任何一段缺值就回 undefined */
function getString(root: unknown, path: ReadonlyArray<string | number>): string | undefined {
  let cur: unknown = root;
  for (const key of path) {
    if (cur == null) return undefined;
    if (typeof key === 'number') {
      if (!Array.isArray(cur)) return undefined;
      cur = cur[key];
    } else {
      if (typeof cur !== 'object') return undefined;
      cur = (cur as Record<string, unknown>)[key];
    }
  }
  return typeof cur === 'string' ? cur : undefined;
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

function errorResponse(code: OcrErrorCode, status: number): Response {
  return new Response(JSON.stringify({ error: code }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
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
