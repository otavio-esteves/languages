<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import ThemeToggle from './ThemeToggle.vue';
import { completedInDay, dayLessons, lessonKey, type TrailPhase } from '../lib/textTrail';
const props = defineProps<{ phases: TrailPhase[]; day: number; cursor: number; completedLessons: string[]; busy?: boolean }>();
const emit = defineEmits<{ start: [cursor: number]; day: [day: number] }>();
const current = computed(() => props.phases[props.day]);
const lessons = computed(() => dayLessons(current.value));
const count = computed(() => completedInDay(current.value, props.completedLessons));
const done = (index: number) => props.completedLessons.includes(lessonKey(current.value, index));
const enabled = (index: number) => !props.busy && (index === 0 || done(index - 1) || done(index));
const picker = ref<HTMLDialogElement | null>(null), wheel = ref<HTMLElement | null>(null), candidate = ref(0);
function center(day: number, smooth = false) {
  candidate.value = Math.max(0, Math.min(props.phases.length - 1, day));
  const option = wheel.value?.children[candidate.value] as HTMLElement | undefined;
  wheel.value?.scrollTo({ top: option ? option.offsetTop - 72 : 0, behavior: smooth && !matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'instant' });
}
async function openPicker() { candidate.value = props.day; picker.value?.showModal(); await nextTick(); center(props.day); wheel.value?.focus(); }
function onScroll() {
  if (!wheel.value) return;
  const middle = wheel.value.scrollTop + wheel.value.clientHeight / 2;
  const bounds = Array.from(wheel.value.children) as HTMLElement[];
  candidate.value = bounds.reduce((best, row, index) => Math.abs(row.offsetTop + row.offsetHeight / 2 - middle) < Math.abs(bounds[best].offsetTop + bounds[best].offsetHeight / 2 - middle) ? index : best, 0);
}
function selectDay() { emit('day', candidate.value); picker.value?.close(); }
function keydown(event: KeyboardEvent) {
  if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter'].includes(event.key)) event.preventDefault();
  if (event.key === 'ArrowDown') center(candidate.value + 1, true);
  if (event.key === 'ArrowUp') center(candidate.value - 1, true);
  if (event.key === 'Home') center(0, true);
  if (event.key === 'End') center(props.phases.length - 1, true);
  if (event.key === 'Enter') selectDay();
}
function start(index: number) {
  const lesson = lessons.value[index];
  emit('start', !done(index) && lesson.steps.some(step => step.index === props.cursor) ? props.cursor : lesson.steps[0].index);
}
</script>
<template>
  <div class="daily-cards">
    <div class="trail-brand"><span class="brand">idiomas<span class="brand-dot">.</span></span><ThemeToggle /></div>
    <nav class="day-navigation" aria-label="Navegar entre dias">
      <button class="icon-button day-arrow" aria-label="Dia anterior" :disabled="busy || day === 0" @click="emit('day', day - 1)">‹</button>
      <button class="day-heading" aria-haspopup="dialog" :disabled="busy" @click="openPicker"><span>Dia {{ day + 1 }} <span class="chevron">⌄</span></span><small>{{ current.title }}</small></button>
      <button class="icon-button day-arrow" aria-label="Próximo dia" :disabled="busy || day === phases.length - 1" @click="emit('day', day + 1)">›</button>
    </nav>
    <div class="daily-meta"><span class="level-badge" title="Referência do conteúdo; não certifica o nível do aluno">{{ current.level || 'Inicial' }}</span><span>{{ count }} de {{ lessons.length }} lições concluídas</span></div>
    <progress class="daily-progress" :value="count" :max="lessons.length" :aria-label="`Progresso do Dia ${day + 1}`" :aria-valuetext="`${count} de ${lessons.length} lições concluídas`" />
    <div class="daily-lessons">
      <button v-for="(lesson, index) in lessons" :key="lessonKey(current, index)" class="lesson-card" :class="{ complete: done(index), active: enabled(index) && !done(index) }" :disabled="!enabled(index)" :aria-label="`Lição ${index + 1}${done(index) ? ', concluída, praticar novamente' : ''}`" @click="start(index)">
        <span class="lesson-symbol" aria-hidden="true">{{ done(index) ? '✓' : enabled(index) ? '★' : '•' }}</span>
        <span class="lesson-copy"><strong>Lição {{ index + 1 }}</strong><small>{{ done(index) ? 'Praticar novamente' : lesson.steps[0].label }}</small></span><span class="card-chevron" aria-hidden="true">›</span>
      </button>
    </div>
    <p v-if="count === lessons.length" class="day-celebration" role="status">Dia concluído. Continue no seu ritmo!</p>
    <dialog ref="picker" class="day-dialog" aria-labelledby="day-picker-title" @click="($event.target === picker) && picker?.close()">
      <div class="picker-top"><h2 id="day-picker-title">Escolha um dia</h2><button class="icon-button" aria-label="Fechar seleção de dias" @click="picker?.close()">×</button></div>
      <p class="muted">{{ phases[candidate]?.title }} · conteúdo {{ phases[candidate]?.level || 'inicial' }}</p>
      <div ref="wheel" class="day-wheel" role="listbox" aria-label="Dias disponíveis" tabindex="0" :aria-activedescendant="`day-option-${candidate}`" @scroll="onScroll" @keydown="keydown">
        <div v-for="(phase, index) in phases" :id="`day-option-${index}`" :key="phase.id" role="option" class="day-option" :class="{ selected: candidate === index }" :aria-selected="candidate === index" @click="center(index, true)"><span>Dia {{ index + 1 }}</span><span class="level-badge">{{ phase.level || 'Inicial' }}</span></div>
      </div>
      <button class="picker-confirm" @click="selectDay">Ir para o Dia {{ candidate + 1 }}</button>
    </dialog>
  </div>
</template>
