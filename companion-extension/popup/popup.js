const statusEl = document.getElementById("status");
const lastSyncEl = document.getElementById("last-sync");
const buttonEl = document.getElementById("sync");

function renderLastSync(epochMs) {
  lastSyncEl.textContent = epochMs ? "Last synced: " + new Date(epochMs).toLocaleString() : "";
}

chrome.storage.local.get("lastSyncEpochMs", (stored) => {
  renderLastSync(stored.lastSyncEpochMs);
});

buttonEl.addEventListener("click", () => {
  statusEl.textContent = "Syncing…";
  statusEl.className = "";
  buttonEl.disabled = true;
  chrome.runtime.sendMessage({ type: "SYNC" }, (response) => {
    buttonEl.disabled = false;
    if (chrome.runtime.lastError) {
      statusEl.textContent = chrome.runtime.lastError.message;
      statusEl.className = "error";
      return;
    }
    if (response && response.ok) {
      statusEl.textContent = "Echo Music Desktop Synced Successfully!";
      statusEl.className = "success";
      renderLastSync(response.lastSyncEpochMs);
    } else {
      statusEl.textContent = (response && response.message) || "Sync failed.";
      statusEl.className = "error";
    }
  });
});
