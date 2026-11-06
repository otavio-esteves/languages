<script setup lang="ts">
import { router } from '@inertiajs/vue3';
import TrailMap from '../components/TrailMap.vue';
import { phases, type Mission } from '../lib/textTrail';
type Package = Mission & { progress: { day: number; cursor?: number; completed: boolean } };
defineProps<{ packages: Package[] }>();
</script>
<template>
  <main class="day-only">
    <TrailMap v-for="pkg in packages.slice(0, 1)" :key="`${pkg.id}:${pkg.version}`" :phases="phases(pkg)" :cursor="pkg.progress.cursor ?? (phases(pkg)[pkg.progress.day]?.steps[0].index || 0)" :finished="pkg.progress.completed" @start="router.visit(`/editorial/practice/${pkg.id}/${pkg.version}?start=1`)" />
  </main>
</template>
