/**
 * Gemini 呼叫共用模組——LINE bot 的圖片 OCR（image.ts）與 App 端 OCR API（ocr-api.ts）
 * 都走這裡，模型鏈只從 env.OCR_MODELS 讀一次，兩條路徑永遠同一套設定。
 *
 * 模型鏈設定位置：Cloudflare Dashboard → Worker → Settings → Variables → OCR_MODELS
 * （逗號分隔，第一個是主力）。wrangler.toml 已設 keep_vars = true，deploy 不會清掉。
 * 未設定時退回 [DEFAULT_OCR_MODEL] 單一模型（會在 log 警告）。
 */

import type { Env } from './env.js';

/** 未設定 env.OCR_MODELS 時退回的單一模型。這是全 worker 唯一寫死的模型名。 */
const DEFAULT_OCR_MODEL = 'gemini-3.1-flash-lite';

/**
 * 整條模型鏈共用的總時間預算。設 55s 留 5s 給 Worker 自身 overhead，
 * 比 App 端 readTimeout (60s) 短一點，讓我們先 abort 並回 ai_timeout 而不是讓 App socket timeout。
 * 多模型 fallback 時，這 55s 是「全部嘗試加起來」的上限，不是每個模型各自 55s。
 */
export const GEMINI_TOTAL_BUDGET_MS = 55_000;

/** 單一模型單次嘗試的上限；實際還會被「剩餘總預算」夾住，取兩者較小值。 */
export const PER_MODEL_TIMEOUT_MS = 25_000;

/** 剩餘總預算低於此值就不再開新嘗試（避免開了一個注定 timeout 的請求）。 */
export const MIN_ATTEMPT_MS = 8_000;

export type GeminiErrorCode =
  | 'image_invalid'       // 422  — Gemini 回 400（圖片無法解讀）
  | 'ai_blocked'          // 422  — 安全過濾擋掉
  | 'ai_busy'             // 503  — Gemini 429 / 503（rate limit / 過載）
  | 'ai_unavailable'      // 503  — Gemini 401/403/404（API key / model 設定問題）
  | 'ai_timeout'          // 504  — Gemini fetch 超時
  | 'ai_failed'           // 502  — Gemini 其他 5xx / 回應 parse 失敗
  | 'network_error'       // 502  — fetch Gemini 整個 throw（DNS / TLS）
  | 'internal_error';     // 500  — 兜底

/**
 * 把 Gemini 各種失敗統一包成 (code, status, message)，呼叫端再決定怎麼對外呈現。
 */
export class GeminiError extends Error {
  constructor(
    public readonly code: GeminiErrorCode,
    public readonly status: number,
    message: string,
    /**
     * true = 「非我方問題」，換下一個模型可能成功（限流/過載/5xx/模型不存在/超時/網路）。
     * false = 我方/內容問題，換模型也沒用（圖片被拒、安全過濾、金鑰/權限），直接拋。
     */
    public readonly failover: boolean = false,
  ) {
    super(message);
  }
}

/** 解析 env.OCR_MODELS（逗號分隔）成優先鏈；空值退回單一預設並警告。 */
export function resolveOcrModels(env: Pick<Env, 'OCR_MODELS'>): string[] {
  const seen = new Set<string>();
  const chain = (env.OCR_MODELS ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((m) => m && !seen.has(m) && seen.add(m));
  if (chain.length) return chain;
  console.warn(`OCR_MODELS not set in Dashboard Variables, falling back to ${DEFAULT_OCR_MODEL}`);
  return [DEFAULT_OCR_MODEL];
}

export interface GeminiImageRequest {
  /** 指令文字 */
  prompt: string;
  /** 圖片 bytes 與 MIME type */
  bytes: ArrayBuffer;
  mimeType: string;
  /** 直接透傳給 Gemini 的 generationConfig（如 responseMimeType、temperature） */
  generationConfig?: Record<string, unknown>;
  /** log 前綴，方便分辨是 LINE 還是 App 路徑 */
  tag: string;
}

/**
 * 依模型優先鏈依序對 Gemini 送「文字 + 圖片」請求，回傳第一個 candidate 的文字。
 * - 成功 → 回傳文字（Gemini 成功但沒文字時回空字串，交給呼叫端決定怎麼看待）。
 * - 失敗且 [GeminiError.failover] 為 true 且還有下一個模型且還有時間 → 換下一個。
 * - 否則 → 拋出 [GeminiError]。
 *
 * 時間預算：整條鏈共用 [GEMINI_TOTAL_BUDGET_MS]。每次嘗試拿 min(每模型上限, 剩餘預算)，
 * 剩餘不足 [MIN_ATTEMPT_MS] 就不再開新嘗試。
 */
export async function generateTextFromImage(
  apiKey: string,
  models: string[],
  req: GeminiImageRequest,
): Promise<string> {
  const base64 = arrayBufferToBase64(req.bytes);
  const deadline = Date.now() + GEMINI_TOTAL_BUDGET_MS;

  let lastErr: unknown;
  for (let i = 0; i < models.length; i++) {
    const remaining = deadline - Date.now();
    if (remaining < MIN_ATTEMPT_MS) {
      throw lastErr ?? new GeminiError('ai_timeout', 504, 'OCR budget exhausted before any attempt');
    }
    const attemptTimeout = Math.min(PER_MODEL_TIMEOUT_MS, remaining);
    try {
      return await callOneModel(apiKey, models[i]!, base64, req, attemptTimeout);
    } catch (e) {
      lastErr = e;
      const canFailover =
        e instanceof GeminiError &&
        e.failover &&
        i < models.length - 1 &&
        deadline - Date.now() >= MIN_ATTEMPT_MS;
      if (canFailover) {
        console.warn(`${req.tag}: model ${models[i]} failed (${(e as GeminiError).code}), trying ${models[i + 1]}`);
        continue;
      }
      throw e;
    }
  }
  throw lastErr ?? new GeminiError('internal_error', 500, 'No OCR model configured');
}

/** 對單一模型打一次。失敗一律包成帶 failover 旗標的 [GeminiError]。 */
async function callOneModel(
  apiKey: string,
  model: string,
  base64: string,
  req: GeminiImageRequest,
  timeoutMs: number,
): Promise<string> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;

  // AbortController 控制 timeout——Workers 的 fetch 沒有內建 timeout
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  let res: Response;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // key 走 header 不走 query string，避免被 log 記到 URL 裡
        'x-goog-api-key': apiKey.trim(),
      },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              { text: req.prompt },
              { inline_data: { mime_type: req.mimeType, data: base64 } },
            ],
          },
        ],
        ...(req.generationConfig ? { generationConfig: req.generationConfig } : {}),
      }),
      signal: controller.signal,
    });
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') {
      throw new GeminiError('ai_timeout', 504, 'Gemini fetch timed out', true);
    }
    throw new GeminiError('network_error', 502, `Gemini fetch threw: ${String(e)}`, true);
  } finally {
    clearTimeout(timeoutId);
  }

  if (!res.ok) {
    const bodyText = await safeReadText(res);
    throw new GeminiError(
      mapGeminiStatusToCode(res.status),
      mapGeminiStatusToOurStatus(res.status),
      `Gemini ${res.status}: ${bodyText.slice(0, 500)}`,
      isFailoverStatus(res.status),
    );
  }

  let json: unknown;
  try {
    json = await res.json();
  } catch (e) {
    throw new GeminiError('ai_failed', 502, `Gemini response not JSON: ${String(e)}`, true);
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

  return getString(json, ['candidates', 0, 'content', 'parts', 0, 'text'])?.trim() ?? '';
}

/**
 * Gemini 回非 2xx 時，這個 HTTP status 換下一個模型有沒有機會成功？
 * - 404 模型不存在/打錯、408 逾時、429 限流、5xx 過載/錯誤 → true
 * - 400 圖片本身被拒、401/403 金鑰/權限/帳單 → false，直接拋
 *
 * 注意：400 對 OCR 代表「這張圖 Gemini 不接受」，換模型還是同一張圖，照樣被拒，所以不換。
 */
function isFailoverStatus(status: number): boolean {
  if (status === 404 || status === 408) return true;
  if (status === 429) return true;
  if (status >= 500) return true; // 500/502/503/504
  return false; // 400/401/403 及其他 4xx 都是我方/內容問題
}

/** Gemini HTTP status → 我們的 error code */
function mapGeminiStatusToCode(status: number): GeminiErrorCode {
  if (status === 400) return 'image_invalid';                   // 圖片 Gemini 不接受
  if (status === 401 || status === 403) return 'ai_unavailable'; // API key 壞 / 沒權限 / billing
  if (status === 404) return 'ai_unavailable';                  // model 名打錯
  if (status === 429) return 'ai_busy';                         // rate limit / quota
  if (status === 503) return 'ai_busy';                         // overloaded
  if (status === 504) return 'ai_timeout';
  if (status >= 500) return 'ai_failed';                        // 其他 5xx
  return 'ai_failed';                                            // 其他非預期 4xx
}

/** Gemini HTTP status → 我們對外的 HTTP status */
function mapGeminiStatusToOurStatus(status: number): number {
  if (status === 400) return 422;
  if (status === 401 || status === 403 || status === 404) return 503;
  if (status === 429 || status === 503) return 503;
  if (status === 504) return 504;
  return 502;
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

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}
