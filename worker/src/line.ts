const LINE_API = 'https://api.line.me/v2/bot';
const LINE_DATA_API = 'https://api-data.line.me/v2/bot';

export async function lineReply(
  token: string,
  replyToken: string,
  messages: unknown[],
): Promise<void> {
  await callLine(token, 'reply', { replyToken, messages });
}

export async function linePush(
  token: string,
  to: string,
  messages: unknown[],
): Promise<void> {
  await callLine(token, 'push', { to, messages });
}

async function callLine(
  token: string,
  kind: 'reply' | 'push',
  payload: unknown,
): Promise<void> {
  const res = await fetch(`${LINE_API}/message/${kind}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const text = await res.text();
    console.error(`LINE ${kind} failed: ${res.status} ${text}`);
  }
}

export async function lineFetchContent(
  token: string,
  messageId: string,
): Promise<{ bytes: ArrayBuffer; contentType: string }> {
  const res = await fetch(`${LINE_DATA_API}/message/${messageId}/content`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`LINE content fetch failed: ${res.status}`);
  return {
    bytes: await res.arrayBuffer(),
    contentType: res.headers.get('content-type') ?? 'image/jpeg',
  };
}
