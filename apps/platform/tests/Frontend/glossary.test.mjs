import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';

const filename = new URL('../../resources/js/components/GlossaryText.vue', import.meta.url);
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename: filename.pathname });
const compiled = ts.transpileModule(compileScript(descriptor, { id: 'glossary-test' }).content, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;

function glossary(text, meanings, lookup = async () => true) {
  const module = { exports: {} };
  const require = name => {
    assert.equal(name, 'vue');
    return { ...vue, useId: () => 'glossary-test', onMounted() {}, onBeforeUnmount() {} };
  };
  Function('require', 'module', 'exports', compiled)(require, module, module.exports);
  return module.exports.default.setup({ text, glossary: meanings, lookup }, { expose() {} });
}

test('accented words and contractions remain whole and keep their authored support', () => {
  const cafe = { meaning: 'café, o estabelecimento', support_id: 'gloss-café' };
  const contraction = { meaning: 'eu gostaria', support_id: 'gloss-id' };
  const text = 'CAFÉ, cafe\u0301! I’d like tea.';
  const component = glossary(text, { café: cafe, "i'd": contraction });
  assert.equal(component.pieces.value.join(''), text);
  assert.deepEqual(component.pieces.value.filter(piece => component.entry(piece)), ['CAFÉ', 'cafe\u0301', 'I’d']);
  assert.equal(component.entry('CAFÉ'), cafe);
  assert.equal(component.entry('cafe\u0301'), cafe);
  assert.equal(component.entry('I’d'), contraction);
  assert.equal(component.entry('cafè'), undefined);
});

test('accented meanings wait for the support record and stay hidden after failure', async () => {
  const meaning = { meaning: 'café, o estabelecimento', support_id: 'gloss-café' };
  let confirm;
  const calls = [];
  const component = glossary('café', { café: meaning }, entry => {
    calls.push(entry);
    return new Promise(resolve => { confirm = resolve; });
  });
  const index = component.pieces.value.indexOf('café');
  const first = component.show(index);
  await vue.nextTick();
  assert.equal(component.loading.value, true);
  assert.equal(component.opened.value, null);
  assert.deepEqual(calls, [meaning]);
  confirm(false);
  await first;
  assert.equal(component.opened.value, null);
  const retry = component.show(index);
  confirm(true);
  await retry;
  assert.equal(component.opened.value, index);
});

test('all accented glossary entries in the current curriculum can be consulted', () => {
  const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  let checked = 0;
  for (const activity of mission.activities) {
    for (const [word, meaning] of Object.entries(activity.content.glossary || {})) {
      if (!/[^\x00-\x7F]/.test(word)) continue;
      const component = glossary(word.toUpperCase(), activity.content.glossary);
      const tokens = component.pieces.value.filter(piece => component.entry(piece));
      assert.deepEqual(tokens, [word.toUpperCase()], activity.id);
      assert.equal(component.entry(tokens[0]), meaning);
      checked++;
    }
  }
  assert.equal(checked, 18);
});
