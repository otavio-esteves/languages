<script setup lang="ts">
import { soundsEnabled, toggleSounds } from '../lib/uiSounds';
</script>
<template>
  <button class="icon-button sound-toggle" :aria-label="soundsEnabled ? 'Silenciar efeitos sonoros' : 'Ativar efeitos sonoros'" :aria-pressed="soundsEnabled" :title="soundsEnabled ? 'Efeitos sonoros ligados' : 'Efeitos sonoros desligados'" @click="toggleSounds">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 5 6 9H3v6h3l5 4Z"/><path v-if="soundsEnabled" d="M15 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/><path v-else d="m16 9 5 6m0-6-5 6"/></svg>
  </button>
</template>
