<script setup lang="ts">
import { Link } from '@inertiajs/vue3';
import ThemeToggle from './ThemeToggle.vue';
</script>
<template>
  <div class="session-actions">
    <ThemeToggle />
    <Link href="/logout" method="post" as="button" class="quiet" aria-label="Sair da conta">Sair</Link>
  </div>
</template>
