// Run from the sanitized Site checkout; never reads a personal trip file.
import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';
const read = path => readFileSync(path, 'utf8');
let page = read('planner/index.html');
page = page.replace('/*__PLANNER_CORE__*/', () => read('planner/core.js'))
  .replace('/*__PLANNER_UI__*/', () => read('planner/ui.js'));
// JSON and version are filled by the server from D1, never embedded in Git/builds.
page = page.replace('<script type="application/json" id="seed">', '<script>window.PlannerHosted={version:__SHARED_VERSION__};</script><script type="application/json" id="seed">');
page = page.replace('<button id="open">', '<button id="reload-shared">Reload shared project</button><button id="backup">Download JSON backup…</button><button id="history">Saved versions…</button><button id="open">');
page = page.replace('keeps the whole workspace in a local project JSON, including research and the exchange baseline.', 'writes the shared project, including research and the exchange baseline. Desktop and mobile use this same project.')
  .replace('Browser drafts provide recovery; the project JSON is the durable copy.', 'Every shared save retains a JSON version. Download JSON backup keeps a local copy. Browser drafts provide recovery for unsaved edits.');
page = page.replace('<script type="application/json" id="seed">', `<dialog id="shared-conflict"><h2>Shared project changed</h2><p>Your edits are kept on this device. Download a backup before loading the latest shared version.</p><div class="actions"><button id="conflict-backup">Download my draft</button><button id="conflict-reload">Load latest</button><button id="conflict-close">Keep editing</button></div></dialog>
<dialog id="backups"><div class="detail-label"><h2>Saved versions</h2><button data-close-dialog>×</button></div><div id="backup-list"></div></dialog><script type="application/json" id="seed">`);
const runtime = read('worker.mjs');
mkdirSync('dist/server', {recursive: true});
writeFileSync('dist/server/index.js', read('planner/core.js') + '\n' + runtime + '\nexport default createWorker(' + JSON.stringify(page) + ');\n');
