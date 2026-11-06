import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compareAnswer } from '../../resources/js/lib/textTrail.ts';

const fixture = JSON.parse(readFileSync(new URL('../../../../contracts/fixtures/text-answer-cases.json', import.meta.url), 'utf8'));
for (const item of fixture.cases) {
  test(item.id, () => assert.deepEqual(compareAnswer(item.activity, item.response), item.result));
}

test('each day has three complete lesson groups without losing activities', async () => {
  const { phases, dayLessons, lessonIndex } = await import('../../resources/js/lib/textTrail.ts');
  const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v3.json', import.meta.url), 'utf8'));
  for (const day of phases(mission)) {
    const lessons = dayLessons(day);
    assert.equal(lessons.length, 3);
    assert.deepEqual(lessons.flatMap(lesson => lesson.steps), day.steps);
    assert.equal(lessonIndex(day, lessons[0].steps.at(-1).index), 0);
    assert.equal(lessonIndex(day, lessons[1].steps[0].index), 1);
  }
});
