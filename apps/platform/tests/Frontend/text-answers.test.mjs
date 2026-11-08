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

test('day progress counts completed lessons without treating navigation as completion', async () => {
  const { phases, completedInDay, legacyLessons } = await import('../../resources/js/lib/textTrail.ts');
  const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v4.json', import.meta.url), 'utf8'));
  const days = phases(mission);
  assert.deepEqual(days.map(day => day.level), ['A1', 'A1', 'A1']);
  const done = ['sentences:0', 'words:0', 'words:1'];
  assert.deepEqual(days.map(day => completedInDay(day, done)), [2, 0, 1]);
  assert.deepEqual(legacyLessons(days, 3), ['words:0', 'words:1', 'words:2']);
  assert.deepEqual(days.map(day => completedInDay(day, [...done, 'words:2'])), [3, 0, 1]);
});

test('expanded curriculum retains all 53 days, duration estimates and explicit lesson boundaries', async () => {
  const { phases, dayLessons, completedInDay } = await import('../../resources/js/lib/textTrail.ts');
  const mission = JSON.parse(readFileSync(new URL('../../../../content/pt-BR_en/missions/order-a-drink.v5.json', import.meta.url), 'utf8'));
  const days = phases(mission);
  assert.equal(days.length, 53);
  assert.deepEqual(days.map(day => day.id), Array.from({ length: 53 }, (_, index) => `day-${String(index + 1).padStart(2, '0')}`));
  for (const day of days) {
    assert.deepEqual(day.estimatedMinutes, { min: 10, max: 15 });
    assert.deepEqual(dayLessons(day).map(lesson => lesson.steps.length), [8, 8, 8]);
    for (const lesson of dayLessons(day)) {
      assert.equal(mission.activities[lesson.steps.at(-1).index].type, 'summary');
    }
  }
  assert.deepEqual(days.slice(40).map(day => day.level), Array(13).fill('A2'));
  assert.deepEqual(days.map(day => completedInDay(day, ['day-53:2'])), [...Array(52).fill(0), 1]);
  for (const activity of mission.activities.filter(activity => activity.expected.comparison)) {
    for (const answer of activity.expected.comparison.accepted) assert.equal(compareAnswer(activity, answer).outcome, 'correct', activity.id);
    for (const item of activity.expected.comparison.corrections) assert.equal(compareAnswer(activity, item.response).outcome, 'incorrect', activity.id);
    assert.equal(compareAnswer(activity, 'An alternative outside the authored variants.').outcome, 'unverified');
  }
});
