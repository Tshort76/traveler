import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import '../core.js';
import {createWorker} from './worker.mjs';

const demo = JSON.parse(readFileSync(new URL('../../../schema/examples/demo.trip.json', import.meta.url)));
const original = globalThis.PlannerCore.openProject(demo);
function fixture(seed = original) {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('./schema.sql', import.meta.url), 'utf8'));
  const env = {INITIAL_PROJECT: JSON.stringify(seed), DB: {prepare(sql) {
    const statement = db.prepare(sql);
    let args = [];
    const query = {
      bind(...values) { args = values; return query; },
      async first() { return statement.get(...args) || null; },
      async all() { return {results: statement.all(...args)}; },
      async run() { return {meta: statement.run(...args)}; },
    };
    return query;
  }}};
  const worker = createWorker('<script id="seed">__PLANNER_PROJECT__</script>version=__SHARED_VERSION__');
  return {db, env, request: (path = '/api/project', options) => worker.fetch(new Request('https://planner.test' + path, options), env)};
}
const put = (project, version = 1, headers = {}) => ({method: 'PUT', headers: {'Content-Type': 'application/json', ...headers}, body: JSON.stringify({version, project})});

test('initial project is stored once; later bootstrap data cannot replace it', async () => {
  const f = fixture();
  const first = await (await f.request()).json();
  assert.equal(first.version, 1); assert.deepEqual(first.project, original);
  f.env.INITIAL_PROJECT = '{}';
  assert.deepEqual(await (await f.request()).json(), first);
  f.db.close();
});

test('competing saves: one succeeds, one conflicts; backups preserve both saved states', async () => {
  const f = fixture(); await f.request();
  const a = structuredClone(original), b = structuredClone(original);
  a.trip.title = 'Desktop'; b.trip.title = 'Mobile';
  const responses = await Promise.all([f.request('/api/project', put(a)), f.request('/api/project', put(b))]);
  assert.deepEqual(responses.map(r => r.status).sort(), [200, 409]);
  const saved = await (await f.request()).json();
  assert.equal(saved.version, 2); assert.equal(saved.project.trip.title, 'Desktop');
  assert.deepEqual(await (await f.request('/api/backups/1')).json(), original);
  assert.deepEqual(await (await f.request('/api/backups/2')).json(), a);
  assert.equal((await (await f.request('/api/backups')).json()).length, 2);
  assert.equal((await f.request('/api/project', put(b, 2))).status, 200);
  assert.equal((await (await f.request()).json()).project.trip.title, 'Mobile');
  f.db.close();
});

test('invalid input and cross-origin writes do not modify durable storage', async () => {
  const f = fixture(); await f.request();
  for (const version of [-1, null, '1', 1.5]) assert.equal((await f.request('/api/project', put(original, version))).status, 400);
  assert.equal((await f.request('/api/project', put(original, 0))).status, 409);
  assert.equal((await f.request('/api/project', put({}, 1))).status, 400);
  assert.equal((await f.request('/api/project', put(original, 1, {Origin: 'https://elsewhere.test'}))).status, 403);
  assert.equal((await f.request('/api/project', put(original, 1, {'Sec-Fetch-Site': 'cross-site'}))).status, 403);
  assert.equal((await (await f.request()).json()).version, 1);
  f.db.close();
});

test('HTML escapes hostile project strings and cannot be cached', async () => {
  const seed = structuredClone(original); seed.trip.title = '</script><script>alert(1)</script>';
  const f = fixture(seed), response = await f.request('/');
  assert.equal(response.headers.get('Cache-Control'), 'private, no-store');
  assert.ok(!(await response.text()).includes(seed.trip.title));
  assert.equal((await f.request('/missing')).status, 404);
  f.db.close();
});

test('storage errors are reported as failures rather than saved', async () => {
  const f = fixture(); await f.request(); f.db.close();
  assert.equal((await f.request('/api/project', put(original))).status, 503);
});

test('authenticated service bootstrap inserts version 1 only in an empty database', async () => {
  const f = fixture(); delete f.env.INITIAL_PROJECT;
  assert.equal((await f.request()).status, 503);
  assert.equal((await f.request('/api/project', put(original, 0))).status, 200);
  assert.equal((await f.request('/api/project', put(original, 0))).status, 409);
  assert.deepEqual((await (await f.request()).json()).project, original);
  f.db.close();
});
