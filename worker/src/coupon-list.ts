import { MAX_BUBBLES, MAX_BUBBLE_ITEMS } from './config.js';
import { getCategoryEmoji } from './messages.js';
import { formatCouponDate } from './parser.js';
import type { CouponRow } from './types.js';
import type { ListFilter } from './db.js';

export function buildCouponListMessage(
  coupons: CouponRow[],
  filter: ListFilter,
  today: Date,
): unknown {
  if (coupons.length === 0) {
    return { type: 'text', text: '📭 目前沒有相關紀錄。' };
  }

  const isActionRequest = filter === 'active_valid' || filter === 'delete_mode';
  const maxTotal = MAX_BUBBLE_ITEMS * MAX_BUBBLES;
  const display = coupons.slice(0, maxTotal);
  const total = coupons.length;
  const totalPages = Math.ceil(display.length / MAX_BUBBLE_ITEMS);
  const bubbles: unknown[] = [];

  for (let p = 0; p < display.length; p += MAX_BUBBLE_ITEMS) {
    const chunk = display.slice(p, p + MAX_BUBBLE_ITEMS);
    const pageNum = Math.floor(p / MAX_BUBBLE_ITEMS) + 1;

    const bodyContents = chunk.map((c) => {
      const row: Record<string, unknown> = {
        type: 'box',
        layout: 'horizontal',
        contents: [
          { type: 'text', text: getCategoryEmoji(c.category), size: 'sm', flex: 1 },
          { type: 'text', text: c.name, weight: 'bold', size: 'sm', flex: 5, wrap: true },
          {
            type: 'text',
            text: formatCouponDate(c.expire_date, today),
            size: 'xs',
            color: '#888888',
            flex: 2,
            align: 'end',
          },
        ],
        margin: 'md',
        paddingAll: '8px',
        cornerRadius: '8px',
      };

      if (isActionRequest) {
        const action = filter === 'delete_mode' ? 'confirm_delete' : 'confirm_use';
        row.action = {
          type: 'postback',
          label: filter === 'delete_mode' ? '刪除' : '使用',
          data: `action=${action}&id=${c.id}`,
        };
        row.backgroundColor = '#f0f0f0';
        (row.contents as unknown[]).push({
          type: 'text',
          text: filter === 'delete_mode' ? '✕' : '▶',
          size: 'lg',
          color: filter === 'delete_mode' ? '#ff6b6b' : '#4ecdc4',
          flex: 0,
          align: 'end',
          gravity: 'center',
        });
      }

      return row;
    });

    bubbles.push({
      type: 'bubble',
      header: {
        type: 'box',
        layout: 'vertical',
        contents: [
          {
            type: 'text',
            text: totalPages > 1 ? `🎫 票券清單 (${pageNum}/${totalPages})` : '🎫 票券清單',
            weight: 'bold',
            size: 'md',
            wrap: true,
          },
        ],
      },
      body: { type: 'box', layout: 'vertical', contents: bodyContents },
    });
  }

  if (total > maxTotal) {
    bubbles.push({
      type: 'bubble',
      body: {
        type: 'box',
        layout: 'vertical',
        justifyContent: 'center',
        contents: [
          {
            type: 'text',
            text: `⚠️ 還有 ${total - maxTotal} 筆未顯示`,
            weight: 'bold',
            align: 'center',
            wrap: true,
          },
          {
            type: 'text',
            text: '請用「使用/刪除 關鍵字」搜尋',
            size: 'sm',
            color: '#888888',
            align: 'center',
            margin: 'md',
          },
        ],
      },
    });
  }

  const contents = bubbles.length === 1 ? bubbles[0] : { type: 'carousel', contents: bubbles };
  return { type: 'flex', altText: `票券列表 (共 ${total} 筆)`, contents };
}
