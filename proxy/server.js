const http = require('node:http');

const UPSTREAM = 'https://jylcbiznpnaezxeumcsm.supabase.co';
const PORT = Number(process.env.PORT || 10000);
const WINDOW_MS = 60_000;
const MAX_REQUESTS = 180;
const clients = new Map();

function send(res, status, body, headers = {}) {
  res.writeHead(status, {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,PUT,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'apikey,authorization,content-type,x-client-info,prefer,accept,range',
    'Access-Control-Expose-Headers': 'content-range,content-location',
    'Cache-Control': 'no-store',
    ...headers
  });
  res.end(body);
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, '');
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname === '/health') {
    return send(res, 200, JSON.stringify({ ok: true }), { 'Content-Type': 'application/json' });
  }

  if (!url.pathname.startsWith('/rest/v1/') && !url.pathname.startsWith('/functions/v1/')) {
    return send(res, 404, 'Not found');
  }

  const ip = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  let entry = clients.get(ip);
  if (!entry || now - entry.start >= WINDOW_MS) {
    entry = { start: now, count: 0 };
    clients.set(ip, entry);
  }
  entry.count++;
  if (entry.count > MAX_REQUESTS) return send(res, 429, 'Too many requests');

  const chunks = [];
  let size = 0;
  try {
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 1_000_000) return send(res, 413, 'Request too large');
      chunks.push(chunk);
    }

    const headers = {};
    for (const key of ['apikey', 'authorization', 'content-type', 'prefer', 'accept', 'range', 'x-client-info']) {
      if (req.headers[key]) headers[key] = req.headers[key];
    }

    const upstream = await fetch(UPSTREAM + url.pathname + url.search, {
      method: req.method,
      headers,
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : Buffer.concat(chunks),
      signal: AbortSignal.timeout(25_000)
    });

    const responseHeaders = {};
    for (const key of ['content-type', 'content-range', 'content-location', 'retry-after']) {
      const value = upstream.headers.get(key);
      if (value) responseHeaders[key] = value;
    }
    const body = Buffer.from(await upstream.arrayBuffer());
    return send(res, upstream.status, body, responseHeaders);
  } catch (error) {
    return send(res, 502, JSON.stringify({ message: 'Не удалось связаться с сервером данных. Попробуй ещё раз.' }), {
      'Content-Type': 'application/json'
    });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('ZHTI API relay listening on port ' + PORT);
});
