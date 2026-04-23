import { NOTIFY_DAYS, UNLIMITED_DATE } from './config.js';
import { cleanupExpiredPending, listCouponsExpiringIn } from './db.js';
import { linePush } from './line.js';
import { getCategoryEmoji } from './messages.js';
import { toIsoDate } from './parser.js';
import type { Env } from './env.js';

export async function runDailyNotify(env: Env): Promise<void> {
  await cleanupExpiredPending(env.DB);

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
    await linePush(env.LINE_CHANNEL_ACCESS_TOKEN, uid, [
      {
        type: 'text',
        text: `⏰ 【到期提醒】\n\n${list.join('\n')}\n\n請記得盡快使用！`,
      },
    ]);
  }
}
