<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { feedbackCue, playUiSound } from '../lib/uiSounds';
import { Link } from '@inertiajs/vue3';
import BrandMark from '../components/BrandMark.vue';
import TrailMap from '../components/TrailMap.vue';
import GlossaryText from '../components/GlossaryText.vue';
import SessionActions from '../components/SessionActions.vue';
import { compareAnswer, dayLessons, lessonIndex, lessonKey, legacyLessons, completedInDay, isTextOnly, phaseId, phases, type Activity, type Mission, type Support, type Gloss, type ComparisonResult } from '../lib/textTrail';
const props = defineProps<{ mission: Mission; manifest: { package_hash: string; is_preview: boolean }; progress?: { day: number; cursor?: number; completed: boolean; completed_lessons?: string[] } }>();
const initialDay = phases(props.mission)[props.progress?.day || 0];
const requestedCursor = typeof window !== 'undefined' ? Number(new URLSearchParams(window.location.search).get('cursor')) : NaN;
const initialCursor = typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('cursor') && Number.isInteger(requestedCursor) && props.mission.activities[requestedCursor] ? requestedCursor : (props.progress?.cursor ?? initialDay?.steps[0]?.index ?? 0);
const selectedDay = ref(Math.max(0, phases(props.mission).findIndex(day => day.steps.some(step => step.index === initialCursor))));
if (typeof window !== 'undefined') {
  const query = new URLSearchParams(window.location.search);
  const day = Number(query.get('day'));
  if (query.has('day') && Number.isInteger(day) && phases(props.mission)[day]) selectedDay.value = day;
}
const completedLessons = ref(props.progress?.completed_lessons ?? legacyLessons(phases(props.mission), props.progress?.cursor || 0, props.progress?.completed));
const index = ref(initialCursor), answer = ref(''), selected = ref<string | null>(null), revealed = ref<string[]>([]);
const busy = ref(false), message = ref(''), feedback = ref(''), recorded = ref(false), finished = ref(false), showTrail = ref(true);
const promptHeading = ref<HTMLElement | null>(null);
const completed = ref(props.progress?.completed || false);
const session = crypto.randomUUID(), device = crypto.randomUUID();
let sequence = 0;
const events = ref<{ id: string; kind: string; payload: Record<string, unknown>; received: boolean }[]>([]);
const drafts = new Map<string, { answer: string; selected: string | null }>();
const lessonPositions = new Map<string, number>();
const activity = computed(() => props.mission.activities[index.value]);
const trail = computed(() => phases(props.mission));
const currentPhase = computed(() => trail.value.find(phase => phase.id === phaseId(activity.value))!);
const textOnly = computed(() => isTextOnly(props.mission));
const dayPosition = computed(() => currentPhase.value.steps.findIndex(step => step.index === index.value));
const hasPending = computed(() => events.value.some(event => !event.received));
const correct = ref(false);
const assessment = ref<ComparisonResult | null>(null), submittedResponse = ref('');
watch(selected, (value, previous) => { if (value !== null && value !== previous) void playUiSound('tap'); });
const manualSupports = computed(() => activity.value.supports.filter(support => !support.id.startsWith('gloss-') && support.kind !== 'solution'));
let actionQueue: Promise<unknown> = Promise.resolve();
let pendingActions = 0;
function base(target: Activity = activity.value) {
  return { schema_version: '1.0.0', id: crypto.randomUUID(), session_id: session, device_id: device, local_sequence: ++sequence, occurred_at: new Date().toISOString(), time_quality: 'uncertain', activity_id: target.id, package_id: props.mission.id, package_version: props.mission.version, package_hash: props.manifest.package_hash, target_ids: target.target_ids };
}
async function send(kind: string, payload: Record<string, unknown>) {
  const existing = events.value.find(event => !event.received);
  if (existing && (existing.kind !== kind || existing.payload.activity_id !== payload.activity_id || existing.payload.response !== payload.response || existing.payload.support_id !== payload.support_id)) throw new Error('Reenvie primeiro o registro pendente.');
  const event = existing || { id: payload.id as string, kind, payload, received: false };
  if (!existing) events.value.push(event);
  const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
  const response = await fetch(`/editorial/${kind}s/batch`, { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-XSRF-TOKEN': cookie ? decodeURIComponent(cookie.split('=').slice(1).join('=')) : '' }, body: JSON.stringify({ events: [event.payload] }) });
  if (!response.ok) throw new Error('O registro não foi confirmado. Mantenha esta página aberta e tente novamente.');
  const result = await response.json();
  if (!['accepted', 'duplicate'].includes(result.results?.[0]?.status)) throw new Error(result.results?.[0]?.message || 'Registro não aceito.');
  events.value.find(item => item.id === event.id)!.received = true;
  return event.payload;
}
function action(work: () => Promise<void>): Promise<boolean> {
  pendingActions++; busy.value = true;
  const task = actionQueue.then(async () => {
    message.value = '';
    try { await work(); return true; } catch (error) { message.value = error instanceof Error ? error.message : 'Não foi possível concluir.'; return false; }
    finally { pendingActions--; busy.value = pendingActions > 0; }
  });
  actionQueue = task;
  return task;
}
async function expose(target: Activity = activity.value, support?: Support) {
  await send('exposure', { ...base(target), support_id: support?.id || `activity-${target.id}`, kind: support?.kind || 'training_use', affected_modalities: support?.affected_modalities || [target.modality], reveals_answer: support?.reveals_answer || false });
}
async function focusPrompt() { await nextTick(); promptHeading.value?.focus(); }
async function focusCurrentStep() { await nextTick(); document.querySelector<HTMLButtonElement>('.lesson-card.active')?.focus(); }
function syncLocation(practicing: boolean) {
  if (typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  url.searchParams.set('day', String(selectedDay.value));
  if (practicing) { url.searchParams.set('cursor', String(index.value)); url.searchParams.set('start', '1'); }
  else { url.searchParams.delete('cursor'); url.searchParams.delete('start'); }
  window.history.replaceState(window.history.state, '', url);
}
function draftKey() { return `${props.manifest.package_hash}:${activity.value.id}`; }
function positionKey(cursor: number) {
  const phase = trail.value.find(day => day.steps.some(step => step.index === cursor))!;
  return `${props.manifest.package_hash}:${lessonKey(phase, lessonIndex(phase, cursor))}`;
}
function stashDraft() {
  if (recorded.value) { drafts.delete(draftKey()); lessonPositions.delete(positionKey(index.value)); }
  else {
    drafts.set(draftKey(), { answer: answer.value, selected: selected.value });
    if (['written_recall', 'communicative_task', 'information_question'].includes(activity.value.type)) lessonPositions.set(positionKey(index.value), index.value);
  }
}
function enterActivity(cursor: number) {
  stashDraft(); index.value = cursor; clearActivity();
  const draft = drafts.get(draftKey());
  if (draft) { answer.value = draft.answer; selected.value = draft.selected; }
}
function backToTrail() { stashDraft(); showTrail.value = true; syncLocation(false); focusCurrentStep(); }
function begin(cursor = index.value, interactive = true) { return action(async () => { cursor = lessonPositions.get(positionKey(cursor)) ?? cursor; if (cursor !== index.value) enterActivity(cursor); if (['dialogue', 'explanation'].includes(activity.value.type)) await expose(); showTrail.value = false; syncLocation(true); if (interactive) void playUiSound('tap'); await focusPrompt(); }); }
function support(value: Support) { return action(async () => { if (!revealed.value.includes(value.id)) { await expose(activity.value, value); revealed.value.push(value.id); } }); }
async function lookup(entry: Gloss): Promise<boolean> {
  const target = activity.value;
  const declaration = target.supports.find(item => item.id === entry.support_id);
  if (!declaration) return false;
  return action(async () => {
    if (activity.value.id !== target.id) throw new Error('Retome a atividade para consultar o significado.');
    if (!revealed.value.includes(entry.support_id)) { await expose(target, declaration); revealed.value.push(entry.support_id); }
  });
}
async function presentFeedback() {
  const result = compareAnswer(activity.value, submittedResponse.value);
  if (result.example) {
    const solution = activity.value.supports.find(item => item.id === 'comparison-example' && item.kind === 'solution');
    if (solution && !revealed.value.includes(solution.id)) { await expose(activity.value, solution); revealed.value.push(solution.id); }
  }
  const firstPresentation = assessment.value === null;
  assessment.value = result;
  correct.value = result.outcome === 'correct';
  feedback.value = result.message;
  if (firstPresentation) void playUiSound(feedbackCue(result.outcome));
}
function chooseOption(event: MouseEvent, option: string) {
  if (!busy.value && !(event.target as Element).closest('button, [role="tooltip"]')) selected.value = option;
}
async function restoreAttempt(payload: Record<string, unknown>) {
  if (payload.activity_id !== activity.value.id) throw new Error('Retome a atividade da resposta registrada.');
  recorded.value = true; submittedResponse.value = payload.response as string;
  await presentFeedback();
}
function submit(value: string) { return action(async () => {
  if (recorded.value) return;
  const supports = events.value.filter(event => event.kind === 'exposure' && event.payload.kind !== 'training_use' && (event.payload.affected_modalities as string[]).includes(activity.value.modality) && (event.payload.target_ids as string[]).some(target => activity.value.target_ids.includes(target))).map(event => event.id);
  const payload = await send('attempt', { ...base(), modality: activity.value.modality, support_event_ids: supports, technical_status: 'valid', response: value, audio_reference: null, assessment_status: activity.value.type === 'information_question' || activity.value.expected.comparison ? 'pending' : 'not_requested' });
  await restoreAttempt(payload);
}); }
function clearActivity() { answer.value = ''; selected.value = null; revealed.value = []; recorded.value = false; feedback.value = ''; correct.value = false; assessment.value = null; submittedResponse.value = ''; }
function reset() { drafts.delete(draftKey()); lessonPositions.delete(positionKey(index.value)); clearActivity(); void playUiSound('tap'); }
async function saveDay(day: number, cursor: number, lesson: string) {
  const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
  const response = await fetch('/editorial/day-progress', { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-XSRF-TOKEN': cookie ? decodeURIComponent(cookie.split('=').slice(1).join('=')) : '' }, body: JSON.stringify({ package_hash: props.manifest.package_hash, day, completed: false, cursor, lesson }) });
  if (!response.ok) throw new Error('Não foi possível salvar a lição. Tente continuar novamente.');
  const saved = await response.json();
  completedLessons.value = saved.completed_lessons;
  completed.value = saved.completed;
  void playUiSound('complete');
}
function selectDay(day: number) { if (!busy.value && !hasPending.value && trail.value[day]) { selectedDay.value = day; syncLocation(false); } }
function finishDay() { return action(async () => {
  await saveDay(trail.value.indexOf(currentPhase.value), index.value, lessonKey(currentPhase.value, lessonIndex(currentPhase.value, index.value)));
  showTrail.value = true; syncLocation(false); await focusCurrentStep();
}); }
async function move() {
  const next = props.mission.activities[index.value + 1];
  if (!next) return;
  const oldPhase = currentPhase.value;
  const changedPhase = phaseId(next) !== phaseId(activity.value);
  const changedLesson = changedPhase || lessonIndex(oldPhase, index.value) !== lessonIndex(oldPhase, index.value + 1);
  if (changedLesson) await saveDay(trail.value.findIndex(day => day.id === phaseId(next)), index.value + 1, lessonKey(oldPhase, lessonIndex(oldPhase, index.value)));
  if (!changedLesson && ['dialogue', 'explanation'].includes(next.type)) await expose(next);
  enterActivity(index.value + 1); showTrail.value = changedLesson;
  if (!changedLesson) void playUiSound('tap');
  syncLocation(!changedLesson);
  if (changedLesson) await focusCurrentStep(); else await focusPrompt();
}
function next() { action(move); }
function passive() { action(async () => { if (activity.value.type === 'purpose') await expose(); await move(); }); }
function retryPending() {
  const event = events.value.find(item => !item.received);
  if (event) return action(async () => {
    const payload = await send(event.kind, event.payload);
    if (event.kind === 'attempt') await restoreAttempt(payload);
    else if (payload.activity_id === activity.value.id) {
      const supportId = payload.support_id as string;
      if (activity.value.supports.some(support => support.id === supportId) && !revealed.value.includes(supportId)) revealed.value.push(supportId);
      if (recorded.value && !feedback.value) await presentFeedback();
    }
    message.value = 'Registro confirmado.';
  });
}
onMounted(() => { if (textOnly.value && new URLSearchParams(window.location.search).get('start') === '1') begin(index.value, false); });
</script>
<template>
  <header v-if="!showTrail || finished" class="site-header"><BrandMark /><SessionActions /><span class="header-context">Leitura e escrita</span><button v-if="!showTrail && !finished" class="quiet" :disabled="busy" @click="backToTrail">Voltar à trilha</button><Link v-else href="/editorial" class="quiet exit-link">Voltar à revisão</Link></header>
  <main v-if="!textOnly" class="lesson-main"><h1>Continue na trilha de texto.</h1><p>Esta versão foi guardada no histórico. A versão atual está na revisão.</p><Link href="/editorial" class="button-link">Abrir trilha atual</Link></main>
  <main v-else-if="showTrail && !finished" class="day-only">
    <TrailMap :phases="trail" :day="selectedDay" :cursor="index" :completed-lessons="completedLessons" :busy="busy || hasPending" @day="selectDay" @start="begin" />
    <p v-if="message" class="notice" role="alert">{{ message }}</p>
    <button v-if="hasPending" class="secondary" :disabled="busy" @click="retryPending">Reenviar registro pendente</button>
  </main>
  <main v-else class="lesson-main" :aria-busy="busy">
    <template v-if="!finished">
      <div class="lesson-progress"><progress :value="completedInDay(currentPhase, completedLessons)" :max="dayLessons(currentPhase).length" aria-label="Atividades percorridas" /><span>{{ dayPosition + 1 }} / {{ currentPhase.steps.length }}</span></div>
      <p class="eyebrow">DIA {{ trail.indexOf(currentPhase) + 1 }} · {{ currentPhase.title }}</p>
      <span class="exercise-mode">{{ ['written_recall', 'communicative_task'].includes(activity.type) ? 'Sua vez de escrever' : activity.type === 'information_question' ? 'Encontre o sentido' : activity.type === 'summary' ? 'Hora de guardar' : 'Leia e descubra' }}</span>
      <h1 :key="activity.id" ref="promptHeading" class="exercise-prompt" tabindex="-1">{{ activity.prompt }}</h1>
      <div v-if="activity.content.messages" class="text-dialogue" aria-label="Conversa escrita"><div v-for="(turn, position) in activity.content.messages" :key="position" class="dialogue-turn" :class="{ customer: position % 2 === 0 }"><span>{{ turn.speaker }}</span><p><GlossaryText :text="turn.text" :glossary="activity.content.glossary" :lookup="lookup" /></p></div></div>
      <div v-if="activity.content.vocabulary" class="vocabulary-list"><div v-for="item in activity.content.vocabulary" :key="item.word"><GlossaryText :text="item.word" :glossary="activity.content.glossary" :lookup="lookup" /><span>{{ item.meaning }}</span></div></div>
      <p v-if="activity.content.study_text" class="study-text"><GlossaryText :text="activity.content.study_text" :glossary="activity.content.glossary" :lookup="lookup" /></p>
      <p v-if="activity.content.pt_BR_note" class="explanation-note">{{ activity.content.pt_BR_note }}</p>
      <p v-for="example in activity.content.examples" :key="example" class="example-phrase"><GlossaryText :text="example" :glossary="activity.content.glossary" :lookup="lookup" /></p>
      <div v-if="manualSupports.length" class="supports"><div v-for="item in manualSupports" :key="item.id"><p v-if="revealed.includes(item.id)" class="revealed-support">{{ item.content }}</p><button v-else class="support-button" :disabled="busy" @click="support(item)">Consultar apoio</button></div></div>
      <form v-if="activity.type === 'information_question' && !recorded" class="choice-form" @submit.prevent="selected !== null && submit(selected)"><fieldset><legend class="sr-only">Escolha uma resposta</legend><div v-for="(option, optionIndex) in activity.content.options" :key="option" class="choice" :class="{ selected: selected === option }" @click="chooseOption($event, option)"><input v-model="selected" type="radio" :aria-label="option" :disabled="busy" name="response" :value="option" required><span class="choice-index" aria-hidden="true">{{ String.fromCharCode(65 + optionIndex) }}</span><span><GlossaryText v-if="activity.content.options_language === 'en'" :text="option" :glossary="activity.content.glossary" :lookup="lookup" /><template v-else>{{ option }}</template></span></div></fieldset><button :disabled="busy || selected === null">Conferir</button></form>
      <form v-if="['written_recall', 'communicative_task'].includes(activity.type) && !recorded" @submit.prevent="submit(answer)"><label for="response">Sua mensagem</label><textarea id="response" v-model="answer" rows="5" maxlength="10000" placeholder="Escreva em inglês…" lang="en" required /><button :disabled="busy || !answer.trim()">Registrar resposta</button></form>
      <div v-if="recorded" class="feedback-panel" :class="`feedback-${assessment?.outcome || 'pending'}`" role="status"><div class="feedback-heading"><span class="feedback-glyph" aria-hidden="true">{{ correct ? '✓' : assessment?.outcome === 'incorrect' ? '↺' : '…' }}</span><strong>{{ correct ? 'Resposta aceita' : assessment?.outcome === 'incorrect' ? 'Vamos ajustar' : 'Vamos conferir' }}</strong></div><p>{{ feedback }}</p><p v-if="submittedResponse" class="submitted-response">Você escreveu: <GlossaryText :text="submittedResponse" :glossary="activity.content.glossary" :lookup="lookup" /></p><div v-if="assessment?.example" class="comparison-example"><span>Um exemplo possível</span><GlossaryText :text="assessment.example" :glossary="activity.content.glossary" :lookup="lookup" /></div><p v-if="revealed.length" class="muted">O apoio consultado ficou registrado.</p></div>
      <div v-if="recorded && feedback" class="exercise-actions"><button class="quiet" :disabled="busy" @click="reset">Tentar de novo</button><button :disabled="busy" @click="next">Continuar</button></div>
      <div v-if="['purpose', 'dialogue', 'explanation'].includes(activity.type)" class="exercise-actions"><button :disabled="busy" @click="passive">Continuar</button></div>
      <div v-if="activity.type === 'summary'" class="summary-panel"><div class="summary-stamp" aria-hidden="true">✦</div><p>Você praticou leitura e escrita nesta lição. As respostas previstas receberam correção automática de treino; formas não reconhecidas continuam sem confirmação independente.</p><p class="muted">Esta sessão não verifica escuta, fala ou retenção posterior.</p><button :disabled="busy || hasPending" @click="finishDay">Concluir lição</button></div>
      <p v-if="message" class="notice" role="alert">{{ message }}</p><button v-if="hasPending" class="secondary" :disabled="busy" @click="retryPending">Reenviar registro pendente</button>
      <p v-if="busy" class="muted" role="status">Salvando…</p>
    </template>
    <div v-else class="finished-state"><span class="finish-mark" aria-hidden="true">✓</span><p class="eyebrow">PERCURSO CONCLUÍDO</p><h1>Uma conversa de cada vez.</h1><p>Você chegou ao fim desta prática de texto.</p><button class="secondary" @click="finished = false; showTrail = true">Ver meu percurso</button><Link href="/editorial" class="quiet exit-link">Voltar à revisão</Link></div>
  </main>
</template>
