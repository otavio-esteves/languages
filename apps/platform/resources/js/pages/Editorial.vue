<script setup lang="ts">
import { computed, ref } from 'vue';
import { router } from '@inertiajs/vue3';
import TrailMap from '../components/TrailMap.vue';
import { legacyLessons, phases, type Mission } from '../lib/textTrail';
type Package = Mission & { package_hash?: string; progress: { day: number; cursor?: number; completed: boolean; completed_lessons?: string[] } };
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
    <TrailMap v-if="selected" :key="selected.package_hash || `${selected.id}:${selected.version}`" :phases="phases(selected)" :day="day" :cursor="selected.progress.cursor || 0" :completed-lessons="selected.progress.completed_lessons ?? legacyLessons(phases(selected), selected.progress.cursor || 0, selected.progress.completed)" @day="day = $event" @start="start" />
    <div v-if="alternatives.length" class="saved-practices">
      <p class="muted">Você pode continuar uma prática salva ou abrir a trilha atual. Cada percurso mantém suas respostas.</p>
      <button v-for="pkg in alternatives" :key="pkg.package_hash" class="secondary" @click="selectPackage(pkg)">{{ isCurrent(pkg) ? 'Abrir trilha atual' : 'Retomar prática salva' }}</button>
    </div>
  </main>
</template>
