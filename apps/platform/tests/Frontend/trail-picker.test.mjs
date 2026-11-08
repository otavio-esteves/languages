import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import * as trail from '../../resources/js/lib/textTrail.ts';

test('confirming a distant day after scroll events selects that day rather than an intermediate row', () => {
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
