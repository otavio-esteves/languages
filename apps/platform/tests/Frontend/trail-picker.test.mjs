import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import * as trail from '../../resources/js/lib/textTrail.ts';

function picker() {
  const filename = new URL('../../resources/js/components/TrailMap.vue', import.meta.url);
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename: filename.pathname });
  const script = compileScript(descriptor, { id: 'trail-picker-test' });
  const compiled = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const module = { exports: {} };
  Function('require', 'module', 'exports', compiled)((name) => {
    if (name === 'vue') return vue;
    if (name === '../lib/textTrail') return trail;
    if (name.endsWith('.vue')) return {};
    throw new Error(`Unexpected dependency: ${name}`);
  }, module, module.exports);
  const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  const emitted = [];
  const page = module.exports.default.setup({ phases: trail.phases(mission), day: 0, cursor: 0, completedLessons: [] }, { expose() {}, emit: (...args) => emitted.push(args) });
  return { page, emitted };
}

test('confirming a distant day after scroll events selects that day rather than an intermediate row', () => {
  const { page, emitted } = picker();
  page.wheel.value = {
    children: Array.from({ length: 53 }, (_, index) => ({ offsetTop: 72 + 72 * index, offsetHeight: 72 })),
    clientHeight: 216, scrollTop: 0,
    scrollTo({ top, behavior }) {
      // A smooth jump emits events while passing intermediate rows.
      this.scrollTop = behavior === 'smooth' ? (this.scrollTop + top) / 2 : top;
    },
  };
  const choose = (work, expected) => {
    work(); page.onScroll(); page.selectDay();
    assert.deepEqual(emitted.at(-1), ['day', expected]);
  };
  choose(() => page.keydown({ key: 'End', preventDefault() {} }), 52);
  choose(() => page.keydown({ key: 'Home', preventDefault() {} }), 0);
  choose(() => page.center(26), 26);
  choose(() => page.keydown({ key: 'ArrowDown', preventDefault() {} }), 27);
});


test('day search keeps real day indices, accepts unaccented topics and handles no matches', async () => {
  const { page, emitted } = picker();
  page.daySearch.value = '27';
  await vue.nextTick(); await vue.nextTick();
  assert.deepEqual(page.visibleDays.value.map(item => item.index), [26]);
  page.selectDay();
  assert.deepEqual(emitted.at(-1), ['day', 26]);
  page.daySearch.value = 'cafe';
  await vue.nextTick(); await vue.nextTick();
  assert.ok(page.visibleDays.value.length > 0);
  assert.ok(page.visibleDays.value.some(item => item.phase.title.includes('café')));
  page.keydown({ key: 'End', preventDefault() {} });
  page.selectDay();
  assert.deepEqual(emitted.at(-1), ['day', page.visibleDays.value.at(-1).index]);
  const count = emitted.length;
  page.daySearch.value = 'an unavailable topic';
  await vue.nextTick(); await vue.nextTick();
  assert.equal(page.visibleDays.value.length, 0);
  page.keydown({ key: 'Home', preventDefault() {} });
  page.selectDay();
  assert.equal(emitted.length, count);
  page.daySearch.value = '';
  await vue.nextTick(); await vue.nextTick();
  assert.equal(page.candidate.value, 0);
  assert.equal(page.visibleDays.value.length, 53);
});
