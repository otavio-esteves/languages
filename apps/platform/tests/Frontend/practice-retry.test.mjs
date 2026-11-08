import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import * as trail from '../../resources/js/lib/textTrail.ts';
import { feedbackCue } from '../../resources/js/lib/uiSounds.ts';

const filename = new URL('../../resources/js/pages/PracticePreview.vue', import.meta.url);
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename: filename.pathname });
const script = compileScript(descriptor, { id: 'practice-retry-test' });
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v3.json', import.meta.url), 'utf8'));

function player(fetch, cues = []) {
  const module = { exports: {} };
  const require = (name) => {
    if (name === 'vue') return { ...vue, onMounted() {} };
    if (name === '../lib/textTrail') return trail;
    if (name === '../lib/uiSounds') return { feedbackCue, playUiSound: async cue => { cues.push(cue); } };
    if (name === '@inertiajs/vue3' || name.endsWith('.vue')) return {};
    throw new Error(`Unexpected dependency: ${name}`);
  };
  Function('require', 'module', 'exports', 'fetch', 'document', compiled)(require, module, module.exports, fetch, { cookie: '', querySelector: () => null });
  return module.exports.default.setup({
    mission, manifest: { package_hash: 'a'.repeat(64), is_preview: true },
    progress: { day: 0, cursor: 2, completed: false },
  }, { expose() {} });
}

test('lost attempt acknowledgement restores feedback without creating another attempt', async () => {
  const saved = new Map();
  let loseAcknowledgement = true;
  const page = player(async (_url, request) => {
    const event = JSON.parse(request.body).events[0];
    const duplicate = saved.has(event.id);
    saved.set(event.id, event);
    if (loseAcknowledgement) { loseAcknowledgement = false; throw new Error('Lost acknowledgement'); }
    return { ok: true, json: async () => ({ results: [{ id: event.id, status: duplicate ? 'duplicate' : 'accepted' }] }) };
  });
  assert.equal(await page.submit('water'), false);
  const original = page.events.value[0].payload;
  // A changed draft must not replace the response already saved by the server.
  page.answer.value = 'coffee';
  assert.equal(await page.retryPending(), true);
  assert.equal(page.recorded.value, true);
  assert.equal(page.submittedResponse.value, 'water');
  assert.equal(page.assessment.value.outcome, 'correct');
  assert.equal(page.hasPending.value, false);
  await page.submit('coffee');
  assert.equal(saved.size, 1);
  assert.deepEqual([...saved.values()][0], original);
  // A fresh attempt is allowed only after the explicit retry action.
  page.reset();
  await page.submit('water');
  assert.equal(saved.size, 2);
});

test('lost correction acknowledgement records one example and supports only the later retry', async () => {
  const cues = [];
  const saved = new Map();
  let loseExample = true;
  const page = player(async (url, request) => {
    const event = JSON.parse(request.body).events[0];
    const duplicate = saved.has(event.id);
    saved.set(event.id, event);
    if (url.includes('exposures') && loseExample) { loseExample = false; throw new Error('Lost example acknowledgement'); }
    return { ok: true, json: async () => ({ results: [{ id: event.id, status: duplicate ? 'duplicate' : 'accepted' }] }) };
  }, cues);
  assert.equal(await page.submit('coffee'), false);
  assert.equal(page.feedback.value, '');
  assert.deepEqual(cues, []);
  assert.equal(await page.retryPending(), true);
  assert.equal(page.assessment.value.outcome, 'incorrect');
  assert.equal(page.assessment.value.example, 'water');
  assert.deepEqual(cues, ['adjust']);
  assert.equal(page.hasPending.value, false);
  assert.equal(saved.size, 2);
  const [first, example] = [...saved.values()];
  assert.deepEqual(first.support_event_ids, []);
  page.reset();
  await page.submit('water');
  assert.equal(saved.size, 3);
  assert.deepEqual([...saved.values()][2].support_event_ids, [example.id]);
});

test('unknown answers use neutral audio and failed saves never celebrate completion', async () => {
  const cues = [];
  const page = player(async url => url.includes('day-progress') ? { ok: false } : { ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) }, cues);
  await page.submit('A possible alternative outside the rules');
  assert.equal(page.assessment.value.outcome, 'unverified');
  assert.deepEqual(cues, ['recorded']);
  assert.equal(await page.finishDay(), false);
  assert.deepEqual(cues, ['recorded']);
  assert.equal(feedbackCue('technical_issue'), 'recorded');
});

test('switching lessons keeps each unsent draft and explicit retry clears only that activity', async () => {
  const page = player(async () => { throw new Error('No network is needed to keep a draft'); });
  page.answer.value = 'My water draft';
  page.backToTrail();
  await page.begin(6);
  page.answer.value = 'My small tea draft';
  await page.begin(2);
  assert.equal(page.answer.value, 'My water draft');
  page.reset();
  await page.begin(6);
  assert.equal(page.answer.value, 'My small tea draft');
  await page.begin(2);
  assert.equal(page.answer.value, '');
  assert.equal(page.events.value.length, 0);
});

test('returning to a recorded activity does not turn the submitted response into an unsent draft', async () => {
  const page = player(async () => ({ ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) }));
  page.answer.value = 'water';
  await page.submit('water');
  await page.begin(6);
  await page.begin(2);
  assert.equal(page.answer.value, '');
  assert.equal(page.recorded.value, false);
  assert.equal(page.events.value.filter(event => event.kind === 'attempt').length, 1);
});

test('opening a multi-activity lesson resumes its unsent response without repeating the model', async () => {
  const page = player(async () => { throw new Error('The model must not be displayed again while resuming a draft'); });
  await page.begin(4);
  page.answer.value = 'A tea draft';
  await page.begin(2);
  assert.equal(await page.begin(3), true);
  assert.equal(page.index.value, 4);
  assert.equal(page.answer.value, 'A tea draft');
  assert.equal(page.events.value.length, 0);
});
