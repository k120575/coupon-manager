/**
 * App 端 OCR API 端點：POST /ocr
 *
 * 跟 LINE bot 的 OCR 是兩套 prompt：
 * - LINE bot 用 6 個粗分類（worker/src/config.ts 的 CATEGORIES）
 * - App 用 14 個細分類（跟 Kotlin BuiltInCategory 對齊）
 *
 * 兩套 prompt 共存不互相干擾，但模型鏈與 failover 共用 gemini.ts（後台 OCR_MODELS 一處設定）。
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
 *          | "quota_exceeded" | "network_error" | "internal_error"
 * }
 * App 端依 error code 翻譯成使用者語言。所有錯誤情境都會被分類到上述其中一個 code。
 */

import { APP_OCR_DAILY_GLOBAL, APP_OCR_DAILY_PER_IP, MAX_NAME_LENGTH } from './config.js';
import { bumpDailyCounter } from './db.js';
import type { Env } from './env.js';
import { GeminiError, generateTextFromImage, resolveOcrModels } from './gemini.js';
import type { GeminiErrorCode } from './gemini.js';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

interface AppOcrResponse {
  name: string | null;
  expireDate: string | null;
  categoryId: string | null;
}

type OcrErrorCode =
  | 'unauthorized'        // 401  — token 不對
  | 'bad_request'         // 400  — content-type / body 不合法
  | 'image_too_large'     // 413  — > 5MB
  | 'quota_exceeded'      // 429  — 每日成本閘（每 IP 或全站）
  | GeminiErrorCode;      // 其餘 Gemini 相關錯誤碼定義在 gemini.ts

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
    return errorResponse('unauthorized', 401);
  }

  // === 成本閘（token 是全裝置共用、可從 APK 取出，所以不能只靠它）===
  // 每個來源 IP 每日 + 全站每日；先加再比，被拒的那次也留紀錄。
  const ip = request.headers.get('cf-connecting-ip') ?? 'unknown';
  const ipCount = await bumpDailyCounter(env.DB, `app:ip:${ip}`);
  if (ipCount > APP_OCR_DAILY_PER_IP) {
    console.warn(`App OCR per-IP daily limit hit: ${ip} ${ipCount}`);
    return errorResponse('quota_exceeded', 429);
  }
  const globalCount = await bumpDailyCounter(env.DB, 'app:global');
  if (globalCount > APP_OCR_DAILY_GLOBAL) {
    console.warn(`App OCR global daily limit hit: ${globalCount}`);
    return errorResponse('quota_exceeded', 429);
  }

  // === 讀圖 ===
  const contentType = request.headers.get('content-type') ?? 'image/jpeg';
  if (!contentType.startsWith('image/')) {
    return errorResponse('bad_request', 400);
  }
  // 有 Content-Length 就先擋，不要把超大 body 整個讀進記憶體
  const declaredLength = Number(request.headers.get('content-length') ?? '0');
  if (declaredLength > MAX_IMAGE_BYTES) {
    return errorResponse('image_too_large', 413);
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
  if (bytes.byteLength > MAX_IMAGE_BYTES) {
    return errorResponse('image_too_large', 413);
  }

  // === 呼叫 Gemini（依模型鏈依序嘗試）===
  try {
    const models = resolveOcrModels(env);
    const result = await callGeminiForApp(env.GEMINI_API_KEY, bytes, contentType, models);
    return jsonResponse(result, 200);
  } catch (e) {
    return geminiErrorToResponse(e);
  }
}

/**
 * 依模型鏈呼叫 Gemini（鏈與 failover 邏輯在 gemini.ts），把回傳文字解析成 App 回應格式。
 */
async function callGeminiForApp(
  apiKey: string,
  bytes: ArrayBuffer,
  mimeType: string,
  models: string[],
): Promise<AppOcrResponse> {
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

  const raw = await generateTextFromImage(apiKey, models, {
    prompt,
    bytes,
    mimeType,
    generationConfig: {
      responseMimeType: 'application/json',
      temperature: 0.1, // 低溫度讓辨識結果穩定
      maxOutputTokens: 256, // 回應只有三個短欄位；同時擋掉圖片提示注入要模型吐超長內容
    },
    tag: 'App OCR',
  });

  if (!raw) {
    // Gemini 成功回應但完全沒文字——當成「看不到票券」回全 null，App 端會選擇不扣次數。
    return { name: null, expireDate: null, categoryId: null };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<AppOcrResponse>;
    return {
      name:
        typeof parsed.name === 'string' && parsed.name.trim() !== ''
          ? parsed.name.trim().slice(0, MAX_NAME_LENGTH)
          : null,
      expireDate: normalizeDate(parsed.expireDate),
      categoryId: normalizeCategoryId(parsed.categoryId),
    };
  } catch {
    // Gemini 偶爾還是會回非 JSON 文字——同樣當「看不到票券」處理
    return { name: null, expireDate: null, categoryId: null };
  }
}

function geminiErrorToResponse(e: unknown): Response {
  if (e instanceof GeminiError) {
    console.error(`App OCR ${e.code}: ${e.message}`);
    return errorResponse(e.code, e.status);
  }
  console.error('App OCR unexpected error:', e);
  return errorResponse('internal_error', 500);
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
