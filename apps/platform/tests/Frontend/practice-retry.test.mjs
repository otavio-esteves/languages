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

function player(fetch, cues = [], initial = {}) {
  const module = { exports: {} };
  const require = (name) => {
    if (name === 'vue') return { ...vue, onMounted() {}, onBeforeUnmount() {} };
    if (name === '../lib/textTrail') return trail;
    if (name === '../lib/uiSounds') return { feedbackCue, playUiSound: async cue => { cues.push(cue); } };
    if (name === '@inertiajs/vue3' || name.endsWith('.vue')) return {};
    throw new Error(`Unexpected dependency: ${name}`);
  };
  Function('require', 'module', 'exports', 'fetch', 'document', compiled)(require, module, module.exports, fetch, { cookie: '', querySelector: () => null });
  return module.exports.default.setup({
    mission, manifest: { package_hash: 'a'.repeat(64), is_preview: true },
    progress: { day: 0, cursor: 2, completed: false }, ...initial,
  }, { expose() {} });
}

function humanReview(attemptId, changes = {}) {
  return {
    ...JSON.parse(readFileSync(new URL('../../../../contracts/fixtures/writing-review.json', import.meta.url), 'utf8')),
    attempt_id: attemptId, ...changes,
  };
}

test('a lost writing review acknowledgement retries the same saved attempt without adding learning events', async () => {
  const saved = [];
  const requests = [];
  const cues = [];
  const page = player(async (url, request) => {
    if (url.endsWith('/writing-reviews')) {
      const input = JSON.parse(request.body); requests.push(input);
      if (requests.length === 1) throw new Error('Lost review acknowledgement');
      return { ok: true, json: async () => humanReview(input.attempt_id, { status: 'pending', revision: 0, feedback: null, reviewed_at: null, dimensions: [], source: 'not_assessed' }) };
    }
    const event = JSON.parse(request.body).events[0]; saved.push(event);
    return { ok: true, json: async () => ({ results: [{ id: event.id, status: 'accepted' }] }) };
  }, cues);
  await page.submit('A possible alternative outside the rules');
  const count = page.events.value.length;
  const sounds = [...cues];
  assert.equal(await page.requestWritingReview(), false);
  assert.equal(page.currentWritingReview.value, undefined);
  assert.equal(await page.requestWritingReview(), true);
  assert.equal(page.currentWritingReview.value.status, 'pending');
  await page.requestWritingReview();
  assert.equal(requests.length, 2);
  assert.deepEqual(requests[0], requests[1]);
  assert.equal(requests[0].attempt_id, saved.find(event => event.modality === 'writing').id);
  assert.equal(page.events.value.length, count);
  assert.equal(page.recorded.value, true);
  assert.deepEqual(cues, sounds);
});

test('explicit review refresh receives revised human feedback while preserving automatic history and progress', async () => {
  const saved = [];
  const cues = [];
  let revision = 1;
  const page = player(async (url, request) => {
    const attempt = saved.find(event => event.modality === 'writing');
    if (url.includes('/learning-sessions/')) return { ok: true, json: async () => ({ session_id: attempt.session_id, events: [{ id: attempt.id, human_review: humanReview(attempt.id, { revision }), assessment: { supported: true, support_pending: false } }] }) };
    if (url.endsWith('/writing-reviews')) return { ok: true, json: async () => humanReview(attempt.id, { status: 'pending', revision: 0, feedback: null, reviewed_at: null, dimensions: [], source: 'not_assessed' }) };
    const event = JSON.parse(request.body).events[0]; saved.push(event);
    return { ok: true, json: async () => ({ results: [{ id: event.id, status: 'accepted' }] }) };
  }, cues);
  await page.submit('A possible alternative outside the rules');
  await page.requestWritingReview();
  const events = JSON.stringify(page.events.value);
  const sounds = [...cues];
  const completed = [...page.completedLessons.value];
  assert.equal(await page.refreshWritingReview(), true);
  assert.equal(page.currentWritingReview.value.source, 'human_review');
  assert.equal(page.currentWritingReview.value.revision, 1);
  assert.equal(page.currentWritingReview.value.retention_confirmed, false);
  assert.equal(page.reviewIsSupported.value, true);
  revision = 2;
  assert.equal(await page.refreshWritingReview(), true);
  assert.equal(page.currentWritingReview.value.revision, 2);
  assert.equal(page.assessment.value.outcome, 'unverified');
  assert.equal(JSON.stringify(page.events.value), events);
  assert.deepEqual(page.completedLessons.value, completed);
  assert.deepEqual(cues, sounds);
});

test('an unavailable human review leaves the saved answer and the practice available', async () => {
  const page = player(async (url, request) => {
    if (url.endsWith('/writing-reviews')) return { ok: false, json: async () => ({ message: 'Revisão indisponível.' }) };
    const event = JSON.parse(request.body).events[0];
    return { ok: true, json: async () => ({ results: [{ id: event.id, status: 'accepted' }] }) };
  });
  await page.submit('water');
  const events = JSON.stringify(page.events.value);
  assert.equal(await page.requestWritingReview(), false);
  assert.equal(page.message.value, 'Revisão indisponível.');
  assert.equal(page.busy.value, false);
  assert.equal(page.recorded.value, true);
  assert.equal(page.submittedResponse.value, 'water');
  assert.equal(JSON.stringify(page.events.value), events);
});

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

test('lesson progress follows confirmed unique answers and survives returning to the trail', async () => {
  const expanded = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  let fail = true;
  const page = player(async () => {
    if (fail) { fail = false; throw new Error('Lost acknowledgement'); }
    return { ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) };
  }, [], { mission: expanded, progress: { day: 0, cursor: 1, completed: false }, answeredActivityIds: [expanded.activities[2].id] });
  assert.equal(page.lessonQuestions.value.length, 6);
  assert.equal(page.answeredQuestions.value, 1);
  await page.submit(expanded.activities[1].content.options[0]);
  assert.equal(page.answeredQuestions.value, 1);
  await page.retryPending();
  assert.equal(page.answeredQuestions.value, 2);
  page.reset();
  await page.submit(expanded.activities[1].content.options[0]);
  assert.equal(page.answeredQuestions.value, 2);
  page.backToTrail();
  assert.equal(page.answeredQuestions.value, 2);
  assert.deepEqual(page.completedLessons.value, []);
  await page.begin(9);
  assert.equal(page.answeredQuestions.value, 0);
  await page.begin(1);
  assert.equal(page.answeredQuestions.value, 2);
});

test('day celebration requires the last lesson save to be confirmed and does not repeat on replay', async () => {
  const expanded = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  let fail = true;
  const cues = [];
  const page = player(async () => ({ ok: !fail, json: async () => ({ completed_lessons: ['day-01:0', 'day-01:1', 'day-01:2'], completed: false }) }), cues,
    { mission: expanded, progress: { day: 0, cursor: 23, completed: false, completed_lessons: ['day-01:0', 'day-01:1'] } });
  assert.equal(await page.finishDay(), false);
  assert.equal(page.celebrationDay.value, null);
  assert.deepEqual(cues, []);
  fail = false;
  assert.equal(await page.finishDay(), true);
  assert.equal(page.celebrationDay.value, 'day-01');
  assert.equal(page.completedLessons.value.length, 3);
  assert.deepEqual(cues, ['complete']);
  await page.begin(17);
  assert.equal(page.celebrationDay.value, null);
  assert.equal(page.answeredQuestions.value, 0);
  page.backToTrail();
  assert.equal(page.completedLessons.value.length, 3);
});

test('confirmed questions resume after reload without repeating the initial model', async () => {
  const expanded = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  const page = player(async () => { throw new Error('No model exposure is needed for this resume'); }, [],
    { mission: expanded, progress: { day: 0, cursor: 0, completed: false, completed_lessons: [] }, answeredActivityIds: expanded.activities.slice(1, 4).map(activity => activity.id) });
  assert.equal(await page.begin(0), true);
  assert.equal(page.index.value, 4);
  assert.equal(page.answeredQuestions.value, 3);
  assert.equal(page.events.value.length, 0);
});

test('unsent work is guarded and only a confirmed response releases its draft', async () => {
  let fail = true;
  const page = player(async () => { if (fail) throw new Error('Offline'); return { ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) }; });
  assert.equal(page.hasUnsavedWork.value, false);
  page.answer.value = 'water';
  assert.equal(page.hasUnsavedWork.value, true);
  page.backToTrail();
  await page.begin(6);
  assert.equal(page.hasUnsavedWork.value, true);
  await page.begin(2);
  await page.submit('water');
  assert.equal(page.hasPending.value, true);
  assert.equal(page.hasUnsavedWork.value, true);
  fail = false;
  await page.retryPending();
  assert.equal(page.hasUnsavedWork.value, false);
});

test('returning to a replay keeps its new question count while preserving the completed lesson', async () => {
  const expanded = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  const page = player(async () => ({ ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) }), [],
    { mission: expanded, progress: { day: 0, cursor: 1, completed: false, completed_lessons: ['day-01:0'] }, answeredActivityIds: expanded.activities.slice(1, 7).map(activity => activity.id) });
  await page.begin(1);
  assert.equal(page.answeredQuestions.value, 0);
  await page.submit(expanded.activities[1].expected.accepted[0]);
  assert.equal(page.answeredQuestions.value, 1);
  page.backToTrail();
  await page.begin(1);
  assert.equal(page.answeredQuestions.value, 1);
  assert.deepEqual(page.completedLessons.value, ['day-01:0']);
});

test('queued duplicate submits do not create a second confirmed response', async () => {
  let requests = 0;
  const page = player(async () => { requests++; return { ok: true, json: async () => ({ results: [{ status: 'accepted' }] }) }; });
  await Promise.all([page.submit('water'), page.submit('water')]);
  assert.equal(requests, 1);
  assert.equal(page.events.value.length, 1);
});
