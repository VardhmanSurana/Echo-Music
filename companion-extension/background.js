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
  "__Secure-1PAPISID",
  "__Secure-3PAPISID",
  "__Secure-1PSID",
  "__Secure-3PSID",
  "VISITOR_INFO1_LIVE",
  "PREF",
  "YSC",
]);

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "CHECK_STATUS") {
    checkOverallStatus()
      .then((status) => sendResponse(status))
      .catch((err) => sendResponse({ desktopConnected: false, ytLoggedIn: false, error: String(err) }));
    return true;
  }

  if (message && message.type === "SYNC") {
    syncToDesktop()
      .then((result) => sendResponse(result))
      .catch((error) =>
        sendResponse({ ok: false, message: error && error.message ? error.message : String(error) })
      );
    return true;
  }
});

async function checkOverallStatus() {
  const [cookies, desktopStatus] = await Promise.all([
    collectYouTubeCookies(),
    getDesktopStatus(),
  ]);

  const hasAuthCookies = Boolean(
    cookies["SAPISID"] ||
    cookies["__Secure-3PSAPISID"] ||
    cookies["LOGIN_INFO"] ||
    cookies["SSID"]
  );

  return {
    desktopConnected: desktopStatus.connected,
    desktopSynced: desktopStatus.synced,
    lastSyncEpochMs: desktopStatus.lastSyncEpochMs,
    accountName: desktopStatus.accountName,
    ytLoggedIn: hasAuthCookies,
    cookieCount: Object.keys(cookies).length,
  };
}

async function getDesktopStatus() {
  const res = await request("/auth/status", { method: "GET" });
  if (res.error || !res.response?.ok) {
    return { connected: false, synced: false };
  }
  return {
    connected: true,
    synced: Boolean(res.data?.synced),
    lastSyncEpochMs: res.data?.lastSyncEpochMs,
    accountName: res.data?.accountName,
  };
}

async function collectYouTubeCookies() {
  const domains = ["music.youtube.com", ".youtube.com", "youtube.com"];
  const cookies = {};

  for (const domain of domains) {
    try {
      const list = await chrome.cookies.getAll({ domain });
      for (const c of list) {
        if (c.value && (IMPORTANT_COOKIES.has(c.name) || c.name.startsWith("__Secure-"))) {
          cookies[c.name] = c.value;
        }
      }
    } catch {
      // Continue searching other domains
    }
  }

  return cookies;
}

async function extractSessionTokensFromTab() {
  try {
    const tabs = await chrome.tabs.query({ url: "*://music.youtube.com/*" });
    if (!tabs || tabs.length === 0) return { visitorData: null, dataSyncId: null };

    // Find active tab or first available tab
    const targetTab = tabs.find((t) => t.active) || tabs[0];
    if (!targetTab?.id) return { visitorData: null, dataSyncId: null };

    const results = await chrome.scripting.executeScript({
      target: { tabId: targetTab.id },
      func: () => {
        const cfg = window.yt?.config_ || window.ytcfg?.data_ || {};
        return {
          visitorData: cfg.VISITOR_DATA || null,
          dataSyncId: cfg.DATASYNC_ID || null,
        };
      },
    });

    if (results && results[0]?.result) {
      return results[0].result;
    }
  } catch (e) {
    console.warn("Could not extract yt session tokens from active tab:", e);
  }
  return { visitorData: null, dataSyncId: null };
}

async function syncToDesktop() {
  const cookies = await collectYouTubeCookies();

  const hasEssentialAuth = Boolean(
    cookies["SAPISID"] ||
    cookies["__Secure-3PSAPISID"] ||
    cookies["LOGIN_INFO"] ||
    cookies["SSID"] ||
    cookies["HSID"]
  );

  if (!hasEssentialAuth) {
    return {
      ok: false,
      message: "No YouTube Music login cookies found. Sign in at music.youtube.com first.",
    };
  }

  // Attempt to extract live visitorData and dataSyncId from open music.youtube.com tab
  const sessionTokens = await extractSessionTokensFromTab();

  const handshake = await request("/auth/handshake", { method: "GET" });
  if (handshake.error) {
    return {
      ok: false,
      message:
        "Echo Music Desktop is not running. Launch the desktop app first (" +
        handshake.error +
        ").",
    };
  }

  const syncPayload = {
    token: handshake.data.token,
    cookies,
    visitorData: sessionTokens.visitorData,
    dataSyncId: sessionTokens.dataSyncId,
    userAgent: navigator.userAgent,
  };

  const body = await request("/auth/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(syncPayload),
  });

  if (body.error) {
    return {
      ok: false,
      message:
        "Could not communicate with Echo Music Desktop (" +
        body.error +
        ").",
    };
  }

  if (!body.response?.ok || body.data?.status !== "ok") {
    const reason = body.data?.reason || ("HTTP " + body.response?.status);
    return { ok: false, message: "Sync rejected by Echo Music Desktop: " + reason };
  }

  const lastSyncEpochMs = Date.now();
  await chrome.storage.local.set({
    lastSyncEpochMs,
    accountName: body.data?.accountName || null,
  });

  return {
    ok: true,
    lastSyncEpochMs,
    accountName: body.data?.accountName,
    cookieCount: Object.keys(cookies).length,
  };
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
  } catch {
    data = {};
  }
  return { response, data };
}

