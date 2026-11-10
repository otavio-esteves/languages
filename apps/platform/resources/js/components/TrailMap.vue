<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import SessionActions from './SessionActions.vue';
import BrandMark from './BrandMark.vue';
import NotebookArt from './NotebookArt.vue';
import { completedInDay, dayLessons, lessonKey, type TrailPhase } from '../lib/textTrail';
const props = defineProps<{ phases: TrailPhase[]; day: number; cursor: number; completedLessons: string[]; busy?: boolean; celebrate?: boolean; answeredActivityIds?: string[]; draftCursors?: number[] }>();
const emit = defineEmits<{ start: [cursor: number]; day: [day: number] }>();
const current = computed(() => props.phases[props.day]);
const lessons = computed(() => dayLessons(current.value));
const count = computed(() => completedInDay(current.value, props.completedLessons));
const done = (index: number) => props.completedLessons.includes(lessonKey(current.value, index));
const resuming = (index: number) => !done(index) && lessons.value[index].steps.some(step => props.answeredActivityIds?.includes(step.activityId || '') || props.draftCursors?.includes(step.index));
const available = (index: number) => index === 0 || done(index - 1) || done(index);
const enabled = (index: number) => !props.busy && available(index);
const nextLesson = computed(() => lessons.value.findIndex((_, index) => !done(index) && available(index)));
const questionCount = (index: number) => lessons.value[index].steps.filter(step => step.question).length;
const picker = ref<HTMLDialogElement | null>(null), wheel = ref<HTMLElement | null>(null), candidate = ref(0);
const daySearch = ref('');
const searchText = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
const visibleDays = computed(() => props.phases.map((phase, index) => ({ phase, index })).filter(({ phase, index }) => {
  const query = searchText(daySearch.value);
  return !query || (/^\d+$/.test(query) ? index + 1 === Number(query) : searchText(`${phase.title} ${phase.level || ''}`).includes(query));
}));
watch(daySearch, async query => { await nextTick(); if (visibleDays.value.length) center(query.trim() ? visibleDays.value[0].index : props.day); });
function center(day: number) {
  candidate.value = Math.max(0, Math.min(props.phases.length - 1, day));
  const option = wheel.value?.children[visibleDays.value.findIndex(item => item.index === candidate.value)] as HTMLElement | undefined;
  wheel.value?.scrollTo({ top: option ? option.offsetTop + option.offsetHeight / 2 - wheel.value.clientHeight / 2 : 0, behavior: 'instant' });
}
async function openPicker() { daySearch.value = ''; candidate.value = props.day; picker.value?.showModal(); await nextTick(); center(props.day); wheel.value?.focus(); }
function onScroll() {
  if (!wheel.value) return;
  const middle = wheel.value.scrollTop + wheel.value.clientHeight / 2;
  const bounds = Array.from(wheel.value.children) as HTMLElement[];
  if (!bounds.length) return;
  const nearest = bounds.reduce((best, row, index) => Math.abs(row.offsetTop + row.offsetHeight / 2 - middle) < Math.abs(bounds[best].offsetTop + bounds[best].offsetHeight / 2 - middle) ? index : best, 0);
  candidate.value = visibleDays.value[nearest].index;
}
function selectDay() { if (!props.busy && visibleDays.value.some(item => item.index === candidate.value)) { emit('day', candidate.value); picker.value?.close(); } }
function keydown(event: KeyboardEvent) {
  if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter'].includes(event.key)) event.preventDefault();
  if (!visibleDays.value.length) return;
  const position = visibleDays.value.findIndex(item => item.index === candidate.value);
  if (event.key === 'ArrowDown') center(visibleDays.value[Math.min(position + 1, visibleDays.value.length - 1)].index);
  if (event.key === 'ArrowUp') center(visibleDays.value[Math.max(0, position - 1)].index);
  if (event.key === 'Home') center(visibleDays.value[0].index);
  if (event.key === 'End') center(visibleDays.value.at(-1)!.index);
  if (event.key === 'Enter') selectDay();
}
function start(index: number) {
  const lesson = lessons.value[index];
  emit('start', !done(index) && lesson.steps.some(step => step.index === props.cursor) ? props.cursor : lesson.steps[0].index);
}
</script>
<template>
  <div class="daily-cards">
    <header class="app-topbar">
      <BrandMark class="topbar-brand" /><SessionActions class="topbar-actions" />
      <nav class="day-navigation topbar-center" aria-label="Navegar entre dias">
        <button class="icon-button day-arrow" aria-label="Dia anterior" :disabled="busy || day === 0" @click="emit('day', day - 1)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg></button>
        <button class="day-heading" aria-haspopup="dialog" :aria-label="`Escolher dia, Dia ${day + 1} de ${phases.length}, conteúdo ${current.level || 'inicial'}`" :disabled="busy" @click="openPicker"><span class="day-label">Dia {{ day + 1 }} <span class="chevron" aria-hidden="true">⌄</span></span><span class="level-badge" title="Referência editorial; não certifica o nível do aluno">{{ current.level || 'Inicial' }}</span></button>
        <button class="icon-button day-arrow" aria-label="Próximo dia" :disabled="busy || day === phases.length - 1" @click="emit('day', day + 1)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg></button>
      </nav>
      <progress class="topbar-progress" :value="count" :max="lessons.length" :aria-label="`Progresso do Dia ${day + 1}`" :aria-valuetext="`${count} de ${lessons.length} lições concluídas`" />
    </header>
    <div class="daily-body">
      <div class="day-overview">
        <div class="chapter-label"><span class="chapter-rule" />SEU CADERNO DE INGLÊS</div>
        <h1>{{ current.title }}</h1>
        <p v-if="current.description" class="day-description">{{ current.description }}</p>
        <div class="daily-meta"><span v-if="current.estimatedMinutes"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2"/></svg>{{ current.estimatedMinutes.min }}–{{ current.estimatedMinutes.max }} min · no seu ritmo</span><span v-else>Leitura e escrita · no seu ritmo</span></div>
        <NotebookArt />
        <p class="day-footnote">Pequenas missões. Um inglês cada vez mais seu.</p>
      </div>
      <div class="lessons-panel">
      <div class="lessons-heading"><span>As lições deste dia</span><span>{{ count }} de {{ lessons.length }} lições concluídas</span></div>
      <section v-if="count === lessons.length" class="day-celebration" :class="{ celebrating: celebrate }" aria-labelledby="day-complete-title"><div class="celebration-mark" aria-hidden="true">✦<i v-if="celebrate" v-for="piece in 12" :key="piece" :style="{ '--piece': piece }" /></div><h2 id="day-complete-title" tabindex="-1">Mandou bem!</h2><p>Dia {{ day + 1 }} concluído.</p><button v-if="day + 1 < phases.length" class="primary-action" @click="emit('day', day + 1)">Ver Dia {{ day + 2 }} <span aria-hidden="true">→</span></button><button v-else class="secondary" @click="start(0)">Rever primeira lição</button></section>
      <div class="daily-lessons" aria-label="Lições do dia">
        <button v-for="(lesson, index) in lessons" :key="lessonKey(current, index)" class="lesson-card" :class="{ complete: done(index), active: available(index) && !done(index) }" :disabled="!enabled(index)" :aria-label="`Lição ${index + 1}, ${lesson.steps[0].label}${done(index) ? ', concluída, praticar novamente' : !available(index) ? `, disponível depois da Lição ${index}` : resuming(index) ? ', continuar' : ', começar'}`" @click="start(index)">
          <span class="lesson-symbol" aria-hidden="true"><svg v-if="done(index)" viewBox="0 0 24 24"><path d="m5 12 4 4 10-10" /></svg><svg v-else-if="!available(index)" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg><span v-else>{{ String(index + 1).padStart(2, '0') }}</span></span>
          <span class="lesson-copy"><span class="lesson-number">Lição {{ index + 1 }}<span v-if="index === nextLesson" class="next-label">{{ resuming(index) ? 'EM ANDAMENTO' : 'SUA PRÓXIMA MISSÃO' }}</span></span><strong>{{ lesson.steps[0].label }}</strong><span v-if="questionCount(index)" class="lesson-detail">{{ questionCount(index) }} questões · leitura e escrita</span><small :class="{ 'lesson-start': index === nextLesson }">{{ done(index) ? 'Concluída · praticar novamente' : !available(index) ? `Depois da Lição ${index}` : resuming(index) ? 'Continuar lição' : 'Começar lição' }}<span v-if="index === nextLesson" aria-hidden="true">↗</span></small></span>
          <span v-if="done(index)" class="card-chevron" aria-hidden="true">↻</span>
        </button>
      </div>
      <p class="trail-footer"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h12a2 2 0 0 1 2 2v14H7a3 3 0 0 1-3-3V5a1 1 0 0 1 1-1Z"/><path d="M7 4v16m3-11h6m-6 4h4"/></svg>Uma lição por vez. Você escolhe quando continuar.</p>
      </div>
    </div>
    <dialog ref="picker" class="day-dialog" aria-labelledby="day-picker-title" @click="($event.target === picker) && picker?.close()">
      <div class="picker-top"><div><h2 id="day-picker-title">Escolha um dia</h2><p class="muted">{{ phases.length }} dias disponíveis</p></div><button class="icon-button" aria-label="Fechar seleção de dias" @click="picker?.close()">×</button></div>
      <div class="day-search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></svg><input v-model="daySearch" type="search" aria-label="Encontrar dia ou assunto" placeholder="Dia ou assunto…" @keydown.enter.prevent="selectDay" @keydown.down.prevent="wheel?.focus()"></div>
      <div v-show="visibleDays.length" ref="wheel" class="day-wheel" role="listbox" aria-label="Dias disponíveis" tabindex="0" :aria-activedescendant="visibleDays.length ? `day-option-${candidate}` : undefined" @scroll="onScroll" @keydown="keydown">
        <div v-for="{ phase, index } in visibleDays" :id="`day-option-${index}`" :key="phase.id" role="option" class="day-option" :class="{ selected: candidate === index }" :aria-selected="candidate === index" @click="center(index)"><span class="day-option-copy"><strong>Dia {{ index + 1 }}</strong><small>{{ phase.title }}</small></span><span class="level-badge">{{ phase.level || 'Inicial' }}</span></div>
      </div>
      <p v-if="!visibleDays.length" class="picker-empty" role="status">Nenhum dia encontrado. Tente outro assunto ou número.</p>
      <button class="picker-confirm primary-action" :disabled="busy || !visibleDays.length" @click="selectDay">{{ visibleDays.length ? `Ir para o Dia ${candidate + 1}` : 'Nenhum dia encontrado' }} <span v-if="visibleDays.length" aria-hidden="true">→</span></button>
    </dialog>
  </div>
</template>
