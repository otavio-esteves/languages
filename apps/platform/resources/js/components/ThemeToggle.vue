<script setup lang="ts">
import { darkTheme, toggleTheme } from '../lib/theme';
</script>
<template>
  <button class="theme-toggle icon-button" :aria-label="darkTheme ? 'Ativar tema claro' : 'Ativar tema escuro'" :title="darkTheme ? 'Tema claro' : 'Tema escuro'" @click="toggleTheme">
    <svg v-if="darkTheme" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>
    <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z"/></svg>
  </button>
</template>
