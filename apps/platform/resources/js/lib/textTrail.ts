export type Support = { id: string; kind: string; content: string; affected_modalities: string[]; reveals_answer: boolean };
export type Activity = {
  id: string; type: string; modality: string; prompt: string; target_ids: string[]; supports: Support[];
  content: { phase_id?: string; phase_title?: string; phase_description?: string; phase_level?: string; day_estimated_minutes?: { min: number; max: number }; estimated_seconds?: number; label?: string; messages?: { speaker: string; text: string }[]; examples?: string[]; options?: string[]; pt_BR_note?: string; options_language?: string; study_text?: string; vocabulary?: { word: string; meaning: string }[]; glossary?: Record<string, Gloss> };
  expected: { accepted?: string[]; comparison?: Comparison }; feedback: string;
};
export type Mission = { id: string; version: number; title: string; assets?: unknown[]; activities: Activity[] };
export type TrailPhase = { id: string; title: string; description: string; level?: string; estimatedMinutes?: { min: number; max: number }; steps: { index: number; label: string }[] };
export function phaseId(activity: Activity): string { return activity.content.phase_id || 'practice'; }
export function phases(mission: Mission): TrailPhase[] {
  const result: TrailPhase[] = [];
  mission.activities.forEach((activity, index) => {
    const id = phaseId(activity);
    let phase = result.find(item => item.id === id);
    if (!phase) {
      phase = { id, title: activity.content.phase_title || 'Praticar', description: activity.content.phase_description || '', level: activity.content.phase_level, estimatedMinutes: activity.content.day_estimated_minutes, steps: [] };
      result.push(phase);
    }
    phase.steps.push({ index, label: activity.content.label || 'Atividade' });
  });
  return result;
}
export function isTextOnly(mission: Mission): boolean {
  return !mission.assets?.length && mission.activities.every(activity => ['reading', 'writing'].includes(activity.modality));
}

export type Comparison = { version: string; accepted: string[]; corrections: { response: string; message: string }[]; example: string };
export type Gloss = { meaning: string; support_id: string };
export type ComparisonResult = { outcome: string; message: string; example: string | null; rule_version: string | null };
export function normalizeAnswer(value: string): string {
  return value.toLowerCase().replace(/[’‘ʼ]/g, "'").replace(/[.,!?]/g, '').replace(/[\p{Z}\s]+/gu, ' ').trim();
}
export function compareAnswer(activity: Activity, response: string | null): ComparisonResult {
  const result: ComparisonResult = { outcome: 'unverified', message: 'Prática registrada. Respostas abertas precisam de avaliação independente.', example: null, rule_version: null };
  if (activity.type === 'information_question') {
    const correct = activity.expected.accepted?.includes(response || '');
    return { ...result, outcome: correct ? 'correct' : 'incorrect', message: correct ? 'Você identificou a informação. Isso registra reconhecimento nesta atividade.' : activity.modality === 'reading' ? 'Leia novamente e observe o sentido da mensagem.' : 'Ouça novamente e identifique a escolha comunicada.' };
  }
  const rule = activity.expected.comparison;
  if (!rule || rule.version !== 'text-variants-v1' || activity.modality !== 'writing') return result;
  result.rule_version = rule.version;
  const normalized = normalizeAnswer(response || '');
  if (normalized && rule.accepted.some(variant => normalizeAnswer(variant) === normalized)) return { ...result, outcome: 'correct', message: 'Resposta aceita nesta atividade.' };
  result.example = rule.example;
  const correction = rule.corrections.find(item => normalizeAnswer(item.response) === normalized);
  return { ...result, outcome: correction ? 'incorrect' : 'unverified', message: correction?.message || 'Ainda não confirmei esta forma. Compare com o exemplo; outra resposta também pode ser válida.' };
}

export function dayLessons(day: TrailPhase): { steps: TrailPhase['steps'] }[] {
  const count = Math.min(3, day.steps.length);
  return Array.from({ length: count }, (_, index) => ({ steps: day.steps.filter((_, position) => Math.floor(position * count / day.steps.length) === index) }));
}
export function lessonIndex(day: TrailPhase, cursor: number): number {
  return dayLessons(day).findIndex(lesson => lesson.steps.some(step => step.index === cursor));
}
export function lessonKey(day: TrailPhase, lesson: number): string { return `${day.id}:${lesson}`; }
export function legacyLessons(days: TrailPhase[], cursor: number, finished = false): string[] {
  return days.flatMap(day => dayLessons(day).flatMap((lesson, index) => finished || lesson.steps.every(step => step.index < cursor) ? [lessonKey(day, index)] : []));
}
export function completedInDay(day: TrailPhase, completed: string[]): number {
  return dayLessons(day).filter((_, index) => completed.includes(lessonKey(day, index))).length;
}
