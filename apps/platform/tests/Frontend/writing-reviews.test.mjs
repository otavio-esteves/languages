import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import * as reviews from '../../resources/js/lib/writingReviews.ts';

function setup() {
  const filename = new URL('../../resources/js/pages/WritingReviews.vue', import.meta.url);
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename: filename.pathname });
  const code = ts.transpileModule(compileScript(descriptor, { id: 'writing-reviews-test' }).content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const reloads = [];
  const module = { exports: {} };
  Function('require', 'module', 'exports', code)(name => {
    if (name === 'vue') return { ...vue, onMounted() {}, onBeforeUnmount() {} };
    if (name === '@inertiajs/vue3') return { router: { reload: options => reloads.push(options) } };
    if (name === '../lib/writingReviews') return reviews;
    if (name.endsWith('.vue')) return {};
    throw new Error(name);
  }, module, module.exports);
  const review = { id: 'synthetic-review', revision: 0, history: [], rubric: { dimensions: [{ id: 'meaning', applicable: true }, { id: 'reading', applicable: false }] } };
  const page = module.exports.default.setup({ reviews: [review], filters: { status: 'pending' }, pagination: { current_page: 1, last_page: 1 } }, { expose() {} });
  return { page, review, reloads };
}

function editable(page, review) {
  const draft = page.draftFor(review);
  assert.equal(draft.dimensions[0].result, 'abstained');
  assert.equal(draft.dimensions[1].result, 'not_observed');
  draft.feedback = 'A mensagem comunica o pedido.';
  draft.dimensions[0].reason = 'A bebida solicitada está identificada.';
  return draft;
}

test('lost decision acknowledgement resends the same ID, revision and immutable judgement', async t => {
  const { page, review, reloads } = setup();
  const draft = editable(page, review);
  t.mock.method(globalThis, 'fetch', async (_url, request) => { sent.push(JSON.parse(request.body)); if (sent.length === 1) throw new Error('Lost acknowledgement'); return { ok: true, status: 200 }; });
  const sent = [];
  const originalDocument = globalThis.document;
  globalThis.document = { cookie: 'XSRF-TOKEN=synthetic' };
  t.after(() => { if (originalDocument === undefined) delete globalThis.document; else globalThis.document = originalDocument; });
  await page.submit(review);
  assert.equal(reloads.length, 0);
  assert.ok(draft.pending);
  page.reload(review.id);
  reloads[0].onSuccess();
  assert.ok(page.draftFor(review).pending);
  reloads.length = 0;
  draft.feedback = 'A local edit cannot change an unconfirmed decision';
  draft.dimensions[0].result = 'met';
  await page.submit(review);
  assert.deepEqual(sent[1], sent[0]);
  assert.equal(sent[1].expected_revision, 0);
  assert.equal(sent[1].dimensions[0].result, 'abstained');
  assert.equal(sent[1].dimensions[1].result, 'not_observed');
  assert.equal(reloads.length, 1);
});

test('a concurrent revision conflict stops further writes until explicit reload', async t => {
  const { page, review, reloads } = setup();
  const draft = editable(page, review);
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return { ok: false, status: 409 }; });
  const originalDocument = globalThis.document;
  globalThis.document = { cookie: '' };
  t.after(() => { if (originalDocument === undefined) delete globalThis.document; else globalThis.document = originalDocument; });
  await page.submit(review);
  assert.equal(draft.conflict, true);
  assert.equal(reloads.length, 0);
  await page.submit(review);
  assert.equal(calls, 1);
  page.reload(review.id, true);
  assert.equal(reloads.length, 1);
  reloads[0].onSuccess();
  assert.equal(page.draftFor(review).pending, null);
});

test('timeout releases the form but preserves the exact decision for recovery', async t => {
  const { page, review } = setup();
  const draft = editable(page, review), sent = [], timeouts = [];
  t.mock.method(AbortSignal, 'timeout', milliseconds => { timeouts.push(milliseconds); return new AbortController().signal; });
  t.mock.method(globalThis, 'fetch', async (_url, request) => { sent.push(JSON.parse(request.body)); assert.ok(request.signal instanceof AbortSignal); if (sent.length === 1) throw new DOMException('Timed out', 'TimeoutError'); return { ok: true, status: 200 }; });
  const originalDocument = globalThis.document;
  globalThis.document = { cookie: '' };
  t.after(() => { if (originalDocument === undefined) delete globalThis.document; else globalThis.document = originalDocument; });
  await page.submit(review);
  assert.equal(draft.busy, false);
  assert.ok(draft.pending);
  assert.match(draft.error, /não chegou a tempo/);
  await page.submit(review);
  assert.deepEqual(sent[0], sent[1]);
  assert.deepEqual(timeouts, [15000, 15000]);
});

test('refresh never silently rebases an older draft onto a concurrent decision', async t => {
  const { page, review, reloads } = setup();
  const draft = editable(page, review), feedback = draft.feedback;
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return { ok: true, status: 200 }; });
  assert.equal(draft.baseRevision, 0);
  review.revision = 1;
  page.reload(review.id);
  reloads[0].onSuccess();
  assert.equal(draft.conflict, true);
  assert.equal(draft.feedback, feedback);
  assert.equal(draft.baseRevision, 0);
  await page.submit(review);
  assert.equal(calls, 0);
  page.reload(review.id, true);
  reloads[1].onSuccess();
  assert.equal(page.draftFor(review).baseRevision, 1);
  assert.equal(page.draftFor(review).feedback, '');
});

test('a newer page revision cannot change the revision captured before submit', async t => {
  const { page, review } = setup();
  const draft = editable(page, review);
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return { ok: true, status: 200 }; });
  review.revision = 1;
  await page.submit(review);
  assert.equal(calls, 0);
  assert.equal(draft.baseRevision, 0);
  assert.equal(draft.conflict, true);
});

test('history errors and overlapping pages keep the editing draft and deduplicate decisions', async t => {
  const { page, review } = setup();
  const decision = revision => ({ id: `decision-${revision}`, revision, feedback: 'Synthetic feedback', dimensions: [] });
  review.revision = 3;
  review.history = [decision(3), decision(2)];
  review.history_total = 3;
  review.history_pagination = { current_page: 1, last_page: 2, total: 3 };
  const draft = editable(page, review), text = draft.feedback;
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async url => { calls++; assert.equal(url, '/reviews/synthetic-review/history?page=2'); if (calls === 1) throw new Error('Temporary network failure'); return { ok: true, json: async () => ({ data: [decision(2), decision(1)], pagination: { current_page: 2, last_page: 2, total: 3 } }) }; });
  await page.loadMoreHistory(review);
  assert.equal(page.historyFor(review).busy, false);
  assert.ok(page.historyFor(review).error);
  assert.equal(page.historyFor(review).pagination.current_page, 1);
  await page.loadMoreHistory(review);
  assert.deepEqual(page.historyFor(review).items.map(item => item.revision), [3, 2, 1]);
  assert.equal(page.historyFor(review).error, '');
  assert.equal(draft.feedback, text);
  assert.equal(draft.baseRevision, 3);
  assert.equal(draft.pending, null);
});
