<script setup lang="ts">
import { computed, ref } from 'vue';
import { router } from '@inertiajs/vue3';
import TrailMap from '../components/TrailMap.vue';
import BrandMark from '../components/BrandMark.vue';
import SessionActions from '../components/SessionActions.vue';
import { legacyLessons, phases, type Mission, type TrailActivity } from '../lib/textTrail';
type Package = Omit<Mission, 'activities'> & { activities: TrailActivity[] } & { package_hash?: string; progress: { day: number; cursor?: number; completed: boolean; completed_lessons?: string[] }; answeredActivityIds?: string[] };
const props = defineProps<{ packages: Package[]; currentPackages?: Package[]; savedPackages?: Package[] }>();
const selected = ref(props.packages[0]);
const day = ref(selected.value?.progress.day || 0);
const alternatives = computed(() => [...(props.savedPackages || []), ...(props.currentPackages || [])].filter((pkg, index, all) => pkg.id === selected.value?.id && pkg.package_hash !== selected.value?.package_hash && all.findIndex(item => item.package_hash === pkg.package_hash) === index));
function selectPackage(pkg: Package) { selected.value = pkg; day.value = pkg.progress.day; }
function isCurrent(pkg: Package) { return props.currentPackages?.some(current => current.package_hash === pkg.package_hash); }
function start(cursor: number) {
  const pkg = selected.value;
  if (!pkg) return;
  const query = new URLSearchParams({ start: '1', cursor: String(cursor) });
  if (pkg.package_hash) query.set('snapshot', pkg.package_hash);
  router.visit(`/editorial/practice/${encodeURIComponent(pkg.id)}/${pkg.version}?${query}`);
}
</script>
<template>
  <main class="day-only">
    <TrailMap v-if="selected" :key="selected.package_hash || `${selected.id}:${selected.version}`" :phases="phases(selected)" :day="day" :cursor="selected.progress.cursor || 0" :answered-activity-ids="selected.answeredActivityIds" :completed-lessons="selected.progress.completed_lessons ?? legacyLessons(phases(selected), selected.progress.cursor || 0, selected.progress.completed)" @day="day = $event" @start="start" />
    <template v-if="!selected"><header class="app-topbar empty-topbar"><BrandMark class="topbar-brand" /><SessionActions class="topbar-actions" /></header><section class="empty-state"><span class="empty-symbol" aria-hidden="true">↗</span><h1>Sua trilha ainda não está disponível.</h1><p>Os materiais estão em revisão. Quando estiverem disponíveis, você poderá começar por aqui.</p></section></template>
    <details v-if="alternatives.length" class="saved-practices">
      <summary>Outras trilhas <span aria-hidden="true">⌄</span></summary>
      <p class="muted">Cada trilha mantém sua própria prática.</p>
      <button v-for="pkg in alternatives" :key="pkg.package_hash" class="version-choice secondary" @click="selectPackage(pkg)"><strong>{{ isCurrent(pkg) ? 'Trilha atual' : 'Prática salva' }} · {{ phases(pkg).length }} dias</strong><span>{{ isCurrent(pkg) ? 'Abrir percurso atualizado' : `Retomar no Dia ${pkg.progress.day + 1}` }}</span></button>
    </details>
  </main>
</template>
