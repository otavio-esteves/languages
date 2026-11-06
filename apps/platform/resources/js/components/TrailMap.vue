<script setup lang="ts">
import { computed } from 'vue';
import { dayLessons, type TrailPhase } from '../lib/textTrail';
const props = defineProps<{ phases: TrailPhase[]; cursor: number; finished?: boolean; busy?: boolean }>();
const day = computed(() => Math.max(0, props.phases.findIndex(item => item.steps.some(step => step.index === props.cursor))));
const lessons = computed(() => dayLessons(props.phases[day.value]));
const done = (steps: TrailPhase['steps']) => props.finished || steps.every(step => step.index < props.cursor);
const count = computed(() => lessons.value.filter(lesson => done(lesson.steps)).length);
defineEmits<{ start: [] }>();
</script>
<template>
  <div class="daily-cards">
    <progress class="daily-progress" :value="count" :max="lessons.length" aria-label="Lições concluídas no dia" />
    <h1>Dia {{ day + 1 }}</h1>
    <div class="daily-lessons">
      <button v-for="(lesson, index) in lessons" :key="index" class="lesson-card" :class="{ complete: done(lesson.steps), active: !finished && lesson.steps.some(step => step.index === cursor) }" :disabled="busy || finished || !lesson.steps.some(step => step.index === cursor)" :aria-label="`Lição ${index + 1}${done(lesson.steps) ? ', concluída' : ''}`" @click="$emit('start')">Lição {{ index + 1 }}</button>
    </div>
  </div>
</template>
