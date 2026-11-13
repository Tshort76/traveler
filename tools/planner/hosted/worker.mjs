// The Site is owner-private. Sites dispatch authenticates every route, including
// service calls used to bootstrap/verify storage. Never publish this API publicly.
const headers = {'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff'};
const json = (value, status = 200, extra = {}) => new Response(JSON.stringify(value), {status, headers: {...headers, 'Content-Type': 'application/json', ...extra}});
const latestSQL = 'SELECT version, document, saved_at FROM project_revisions ORDER BY version DESC LIMIT 1';
export const saveSQL = `INSERT INTO project_revisions (version, document, saved_at)
  SELECT ?, ?, ? WHERE COALESCE((SELECT MAX(version) FROM project_revisions), 0) = ?`;

async function latest(env) {
  let row = await env.DB.prepare(latestSQL).first();
  if (!row && env.INITIAL_PROJECT) {
    const project = globalThis.PlannerCore.openProject(JSON.parse(env.INITIAL_PROJECT));
    await env.DB.prepare('INSERT OR IGNORE INTO project_revisions (version, document, saved_at) VALUES (1, ?, ?)')
      .bind(JSON.stringify(project), new Date().toISOString()).run();
    row = await env.DB.prepare(latestSQL).first();
  }
  return row;
}

export function createWorker(page) {
  return {async fetch(request, env) {
    try {
      const url = new URL(request.url), path = url.pathname;
      if (!env.DB) return json({error: 'Shared storage is unavailable'}, 503);
      if (request.method === 'PUT' && path === '/api/project') {
        // Browsers must send same-origin JSON; service access still passes Sites dispatch.
        if (request.headers.get('Origin') && request.headers.get('Origin') !== url.origin) return json({error: 'Origin rejected'}, 403);
        if (request.headers.get('Sec-Fetch-Site') === 'cross-site') return json({error: 'Origin rejected'}, 403);
        if (!request.headers.get('Content-Type')?.startsWith('application/json')) return json({error: 'JSON required'}, 415);
        if (Number(request.headers.get('Content-Length')) > 8 * 1024 * 1024) return json({error: 'Project too large'}, 413);
        const text = await request.text();
        if (text.length > 8 * 1024 * 1024) return json({error: 'Project too large'}, 413);
        let value, project;
        try {
          value = JSON.parse(text);
          if (!Number.isSafeInteger(value.version) || value.version < 0) throw Error('A saved version is required');
          project = globalThis.PlannerCore.openProject(value.project);
        } catch (error) { return json({error: error.message}, 400); }
        const savedAt = new Date().toISOString();
        const result = await env.DB.prepare(saveSQL).bind(value.version + 1, JSON.stringify(project), savedAt, value.version).run();
        if (result.meta.changes !== 1) return json({error: 'The shared project changed. Reload before saving.'}, 409);
        return json({version: value.version + 1, savedAt});
      }
      if (request.method !== 'GET' && request.method !== 'HEAD') return json({error: 'Method not allowed'}, 405);
      if (path === '/api/backups') {
        const {results} = await env.DB.prepare('SELECT version, saved_at AS savedAt FROM project_revisions ORDER BY version DESC LIMIT 100').all();
        return json(results);
      }
      const backup = path.match(/^\/api\/backups\/([1-9]\d*)$/);
      if (backup) {
        const row = await env.DB.prepare('SELECT document FROM project_revisions WHERE version = ?').bind(Number(backup[1])).first();
        if (!row) return json({error: 'Backup not found'}, 404);
        return new Response(row.document + '\n', {headers: {...headers, 'Content-Type': 'application/json', 'Content-Disposition': `attachment; filename="traveler-v${backup[1]}.planner.json"`}});
      }
      if (!['/', '/api/project'].includes(path)) return json({error: 'Not found'}, 404);
      const row = await latest(env);
      if (!row) return json({error: 'Initial project has not been configured'}, 503);
      const project = JSON.parse(row.document);
      if (path === '/api/project') return json({project, version: row.version, savedAt: row.saved_at});
      const safeProject = JSON.stringify(project).replace(/</g, '\\u003c');
      const html = page.replace('__PLANNER_PROJECT__', () => safeProject)
        .replace('__SHARED_VERSION__', () => String(row.version));
      return new Response(request.method === 'HEAD' ? null : html, {headers: {...headers, 'Content-Type': 'text/html; charset=utf-8', 'Referrer-Policy': 'no-referrer'}});
    } catch {
      return json({error: 'Storage request failed. Keep your draft and retry.'}, 503);
    }
  }};
}
