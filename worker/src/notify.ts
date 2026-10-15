import { NOTIFY_DAYS, UNLIMITED_DATE } from './config.js';
import { cleanupExpiredPending, cleanupOldUsageCounters, listCouponsExpiringIn } from './db.js';
import { linePush } from './line.js';
import { getCategoryEmoji } from './messages.js';
import { toIsoDate } from './parser.js';
import type { Env } from './env.js';

/** LINE 文字訊息上限 5,000 字，留一點餘裕 */
const MAX_PUSH_TEXT = 4_900;

export async function runDailyNotify(env: Env): Promise<void> {
  await cleanupExpiredPending(env.DB);
  await cleanupOldUsageCounters(env.DB);

  const today = new Date();
  const notifications = new Map<string, string[]>();

  for (const days of NOTIFY_DAYS) {
    const target = new Date(today);
    target.setUTCDate(target.getUTCDate() + days);
    const iso = toIsoDate(target);
    if (iso.startsWith('9999')) continue;

    const rows = await listCouponsExpiringIn(env.DB, iso);
    const label = days === 0 ? '🔴 今天到期' : `🟡 ${days} 天後到期`;

    for (const r of rows) {
      if (r.expire_date === UNLIMITED_DATE) continue;
      const list = notifications.get(r.user_id) ?? [];
      list.push(`${getCategoryEmoji(r.category)} ${r.name} — ${r.expire_date} (${label})`);
      notifications.set(r.user_id, list);
    }
  }

  for (const [uid, list] of notifications) {
    // 一個人的推播失敗（網路層 throw、超長被 LINE 拒）不能讓後面所有人都收不到
    try {
      const text = `⏰ 【到期提醒】\n\n${list.join('\n')}\n\n請記得盡快使用！`.slice(0, MAX_PUSH_TEXT);
      await linePush(env.LINE_CHANNEL_ACCESS_TOKEN, uid, [{ type: 'text', text }]);
    } catch (e) {
      console.error(`Daily notify push failed for ${uid}:`, e);
    }
  }
}
