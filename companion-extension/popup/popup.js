const desktopDot = document.getElementById("desktop-dot");
const desktopText = document.getElementById("desktop-text");
const ytDot = document.getElementById("yt-dot");
const ytText = document.getElementById("yt-text");
const accountRow = document.getElementById("account-row");
const accountName = document.getElementById("account-name");
const messageBox = document.getElementById("message-box");
const syncBtn = document.getElementById("sync-btn");
const openYtmBtn = document.getElementById("open-ytm-btn");
const lastSyncText = document.getElementById("last-sync-text");

function showMessage(text, type = "info") {
  messageBox.textContent = text;
  messageBox.className = type;
}

function clearMessage() {
  messageBox.textContent = "";
  messageBox.className = "";
}

function updateLastSync(epochMs) {
  if (epochMs) {
    const formatted = new Date(epochMs).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    lastSyncText.textContent = `Last synced: Today at ${formatted}`;
  } else {
    lastSyncText.textContent = "";
  }
}

async function refreshStatus() {
  clearMessage();
  chrome.runtime.sendMessage({ type: "CHECK_STATUS" }, (res) => {
    if (chrome.runtime.lastError || !res) {
      desktopDot.className = "indicator-dot offline";
      desktopText.textContent = "Offline";
      ytDot.className = "indicator-dot offline";
      ytText.textContent = "Unknown";
      return;
    }

    // Desktop App status
    if (res.desktopConnected) {
      desktopDot.className = "indicator-dot online";
      desktopText.textContent = res.desktopSynced ? "Connected & Synced" : "Running";
    } else {
      desktopDot.className = "indicator-dot offline";
      desktopText.textContent = "Not Running";
    }

    // YouTube Music status
    if (res.ytLoggedIn) {
      ytDot.className = "indicator-dot online";
      ytText.textContent = `Ready (${res.cookieCount} cookies)`;
    } else {
      ytDot.className = "indicator-dot warning";
      ytText.textContent = "Not Signed In";
    }

    // Account display
    if (res.accountName) {
      accountRow.style.display = "flex";
      accountName.textContent = res.accountName;
    } else {
      accountRow.style.display = "none";
    }

    updateLastSync(res.lastSyncEpochMs);
  });
}

syncBtn.addEventListener("click", () => {
  syncBtn.disabled = true;
  showMessage("Syncing session tokens to Echo Music…", "info");

  chrome.runtime.sendMessage({ type: "SYNC" }, (response) => {
    syncBtn.disabled = false;

    if (chrome.runtime.lastError) {
      showMessage(chrome.runtime.lastError.message, "error");
      return;
    }

    if (response && response.ok) {
      const msg = response.accountName
        ? `Successfully synced ${response.accountName}'s account!`
        : `Successfully synced ${response.cookieCount} session tokens!`;
      showMessage(msg, "success");
      updateLastSync(response.lastSyncEpochMs);
      refreshStatus();
    } else {
      const error = (response && response.message) || "Sync failed.";
      showMessage(error, "error");
    }
  });
});

openYtmBtn.addEventListener("click", () => {
  chrome.tabs.create({ url: "https://music.youtube.com" });
});

// Initial run
refreshStatus();

