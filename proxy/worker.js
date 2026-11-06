const UPSTREAM = "https://jylcbiznpnaezxeumcsm.supabase.co";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, prefer, accept, range, x-client-info",
  "Access-Control-Expose-Headers": "content-range, content-location",
  "Cache-Control": "no-store"
};

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    if (url.pathname === "/health") {
      return Response.json({ ok: true, service: "zhti-relay" }, { headers: cors });
    }

    // Fixed upstream and API-only paths: this is not an arbitrary open proxy.
    if (!url.pathname.startsWith("/rest/v1/") &&
        !url.pathname.startsWith("/functions/v1/")) {
      return new Response("Not found", { status: 404, headers: cors });
    }

    const headers = new Headers();
    for (const name of [
      "apikey", "authorization", "content-type", "prefer",
      "accept", "range", "x-client-info"
    ]) {
      const value = request.headers.get(name);
      if (value) headers.set(name, value);
    }

    try {
      const upstream = await fetch(UPSTREAM + url.pathname + url.search, {
        method: request.method,
        headers,
        body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
        redirect: "manual"
      });

      const responseHeaders = new Headers(cors);
      for (const name of ["content-type", "content-range", "content-location", "retry-after"]) {
        const value = upstream.headers.get(name);
        if (value) responseHeaders.set(name, value);
      }

      return new Response(upstream.body, {
        status: upstream.status,
        headers: responseHeaders
      });
    } catch {
      return Response.json(
        { message: "Не удалось связаться с сервером данных. Попробуй ещё раз." },
        { status: 502, headers: cors }
      );
    }
  }
};
