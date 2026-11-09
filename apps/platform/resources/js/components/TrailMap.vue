<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import SessionActions from './SessionActions.vue';
import BrandMark from './BrandMark.vue';
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
const picker = ref<HTMLDialogElement | null>(null), wheel = ref<HTMLElement | null>(null), candidate = ref(0);
function center(day: number) {
  candidate.value = Math.max(0, Math.min(props.phases.length - 1, day));
  const option = wheel.value?.children[candidate.value] as HTMLElement | undefined;
  wheel.value?.scrollTo({ top: option ? option.offsetTop + option.offsetHeight / 2 - wheel.value.clientHeight / 2 : 0, behavior: 'instant' });
}
async function openPicker() { candidate.value = props.day; picker.value?.showModal(); await nextTick(); center(props.day); wheel.value?.focus(); }
function onScroll() {
  if (!wheel.value) return;
  const middle = wheel.value.scrollTop + wheel.value.clientHeight / 2;
  const bounds = Array.from(wheel.value.children) as HTMLElement[];
  candidate.value = bounds.reduce((best, row, index) => Math.abs(row.offsetTop + row.offsetHeight / 2 - middle) < Math.abs(bounds[best].offsetTop + bounds[best].offsetHeight / 2 - middle) ? index : best, 0);
}
function selectDay() { if (!props.busy) { emit('day', candidate.value); picker.value?.close(); } }
function keydown(event: KeyboardEvent) {
  if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter'].includes(event.key)) event.preventDefault();
  if (event.key === 'ArrowDown') center(candidate.value + 1);
  if (event.key === 'ArrowUp') center(candidate.value - 1);
  if (event.key === 'Home') center(0);
  if (event.key === 'End') center(props.phases.length - 1);
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
        <button class="day-heading" aria-haspopup="dialog" :aria-label="`Escolher dia, Dia ${day + 1} de ${phases.length}, conteúdo ${current.level || 'inicial'}`" :disabled="busy" @click="openPicker"><span>Dia {{ day + 1 }} <span class="chevron" aria-hidden="true">⌄</span></span><span class="level-badge" title="Referência editorial; não certifica o nível do aluno">{{ current.level || 'Inicial' }}</span></button>
        <button class="icon-button day-arrow" aria-label="Próximo dia" :disabled="busy || day === phases.length - 1" @click="emit('day', day + 1)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg></button>
      </nav>
      <progress class="topbar-progress" :value="count" :max="lessons.length" :aria-label="`Progresso do Dia ${day + 1}`" :aria-valuetext="`${count} de ${lessons.length} lições concluídas`" />
    </header>
    <div class="daily-body">
      <div class="day-overview"><h1>{{ current.title }}</h1><div class="daily-meta"><span>{{ count }} de {{ lessons.length }} lições concluídas</span><span v-if="current.estimatedMinutes">{{ current.estimatedMinutes.min }}–{{ current.estimatedMinutes.max }} min</span></div></div>
      <section v-if="count === lessons.length" class="day-celebration" :class="{ celebrating: celebrate }" aria-labelledby="day-complete-title"><div class="celebration-mark" aria-hidden="true">✦<i v-if="celebrate" v-for="piece in 12" :key="piece" :style="{ '--piece': piece }" /></div><h2 id="day-complete-title" tabindex="-1">Mandou bem!</h2><p>Dia {{ day + 1 }} concluído.</p><button v-if="day + 1 < phases.length" class="primary-action" @click="emit('day', day + 1)">Ver Dia {{ day + 2 }} <span aria-hidden="true">→</span></button><button v-else class="secondary" @click="start(0)">Rever primeira lição</button></section>
      <div class="daily-lessons" aria-label="Lições do dia">
        <button v-for="(lesson, index) in lessons" :key="lessonKey(current, index)" class="lesson-card" :class="{ complete: done(index), active: available(index) && !done(index) }" :disabled="!enabled(index)" :aria-label="`Lição ${index + 1}, ${lesson.steps[0].label}${done(index) ? ', concluída, praticar novamente' : !available(index) ? `, disponível depois da Lição ${index}` : resuming(index) ? ', continuar' : ', começar'}`" @click="start(index)">
          <span class="lesson-symbol" aria-hidden="true"><svg v-if="done(index)" viewBox="0 0 24 24"><path d="m5 12 4 4 10-10" /></svg><svg v-else-if="!available(index)" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg><span v-else>{{ String(index + 1).padStart(2, '0') }}</span></span>
          <span class="lesson-copy"><span class="lesson-number">Lição {{ index + 1 }}</span><strong>{{ lesson.steps[0].label }}</strong><small>{{ done(index) ? 'Concluída · praticar novamente' : !available(index) ? `Depois da Lição ${index}` : resuming(index) ? 'Continuar' : 'Começar' }}</small></span>
          <span v-if="available(index)" class="card-chevron" aria-hidden="true">→</span>
        </button>
      </div>
    </div>
    <dialog ref="picker" class="day-dialog" aria-labelledby="day-picker-title" @click="($event.target === picker) && picker?.close()">
      <div class="picker-top"><div><h2 id="day-picker-title">Escolha um dia</h2><p class="muted">{{ phases.length }} dias disponíveis</p></div><button class="icon-button" aria-label="Fechar seleção de dias" @click="picker?.close()">×</button></div>
      <div ref="wheel" class="day-wheel" role="listbox" aria-label="Dias disponíveis" tabindex="0" :aria-activedescendant="`day-option-${candidate}`" @scroll="onScroll" @keydown="keydown">
        <div v-for="(phase, index) in phases" :id="`day-option-${index}`" :key="phase.id" role="option" class="day-option" :class="{ selected: candidate === index }" :aria-selected="candidate === index" @click="center(index)"><span class="day-option-copy"><strong>Dia {{ index + 1 }}</strong><small>{{ phase.title }}</small></span><span class="level-badge">{{ phase.level || 'Inicial' }}</span></div>
      </div>
      <button class="picker-confirm primary-action" :disabled="busy" @click="selectDay">Ir para o Dia {{ candidate + 1 }} <span aria-hidden="true">→</span></button>
    </dialog>
  </div>
</template>
