import { render } from "preact";
import "./style.css";
import { isTauri, memoryBackend, tauriBackend } from "./data/backend";
import { Store } from "./data/store";
import { App } from "./ui/App";
import { applyTheme } from "./ui/state";

applyTheme();

// The app keeps its own context menu (pages in the tree); the browser's only where you type.
window.addEventListener("contextmenu", (e) => {
  const t = e.target as HTMLElement;
  if (!t.closest("textarea, input, [contenteditable]")) e.preventDefault();
});

async function start() {
  try {
    await Store.init(isTauri() ? tauriBackend : memoryBackend());
  } catch (e) {
    console.error("[notas] could not open the pages", e);
    document.getElementById("app")!.innerHTML = `<p style="padding:40px;font-family:sans-serif">No se pudieron abrir tus notas: ${String(e)}</p>`;
    return;
  }
  render(<App />, document.getElementById("app")!);
}

void start();
