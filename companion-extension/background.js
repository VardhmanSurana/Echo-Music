const SERVER_BASE = "http://127.0.0.1:45454";

const IMPORTANT_COOKIES = new Set([
  "SAPISID",
  "__Secure-1PSAPISID",
  "__Secure-3PSAPISID",
  "HSID",
  "SSID",
  "LSID",
  "LOGIN_INFO",
  "SID",
  "__Secure-3PAPISID",
]);

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "SYNC") {
    syncToDesktop()
      .then((result) => sendResponse(result))
      .catch((error) =>
        sendResponse({ ok: false, message: error && error.message ? error.message : String(error) })
      );
    return true;
  }
});

async function syncToDesktop() {
  const allCookies = await chrome.cookies.getAll({ domain: "music.youtube.com" });
  const cookies = {};
  for (const cookie of allCookies) {
    if (IMPORTANT_COOKIES.has(cookie.name) && cookie.value) {
      cookies[cookie.name] = cookie.value;
    }
  }
  if (Object.keys(cookies).length === 0) {
    return {
      ok: false,
      message: "No YouTube Music login cookies found. Sign in at music.youtube.com first.",
    };
  }

  const handshake = await request("/auth/handshake", { method: "GET" });
  if (handshake.error) {
    return {
      ok: false,
      message:
        "Echo Music Desktop is not running. Open the desktop app, then try again. (" +
        handshake.error +
        ")",
    };
  }
  const body = await request("/auth/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      token: handshake.data.token,
      cookies,
      userAgent: navigator.userAgent,
    }),
  });
  if (body.error) {
    return {
      ok: false,
      message:
        "Echo Music Desktop is not running. Open the desktop app, then try again. (" +
        body.error +
        ")",
    };
  }
  if (!body.response.ok || body.data.status !== "ok") {
    const reason = body.data && body.data.reason ? body.data.reason : "HTTP " + body.response.status;
    return { ok: false, message: "Sync rejected by Echo Music Desktop: " + reason };
  }

  const lastSyncEpochMs = Date.now();
  await chrome.storage.local.set({ lastSyncEpochMs });
  return { ok: true, lastSyncEpochMs };
}

async function request(path, options) {
  let response;
  try {
    response = await fetch(SERVER_BASE + path, options);
  } catch (e) {
    return { error: e && e.message ? e.message : "connection refused" };
  }
  let data = {};
  try {
    data = await response.json();
  } catch (e) {
    data = {};
  }
  return { response, data };
}
