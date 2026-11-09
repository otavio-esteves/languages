<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Link } from '@inertiajs/vue3';
import { darkTheme, toggleTheme } from '../lib/theme';
import { soundsEnabled, toggleSounds } from '../lib/uiSounds';
const props = withDefaults(defineProps<{ allowLogout?: boolean }>(), { allowLogout: true });
const menu = ref<HTMLDetailsElement | null>(null);
function close(event: Event) { if (!menu.value?.contains(event.target as Node)) menu.value?.removeAttribute('open'); }
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape' && menu.value?.open) {
    event.preventDefault(); menu.value.open = false; menu.value.querySelector('summary')?.focus();
  }
}
onMounted(() => { document.addEventListener('pointerdown', close); document.addEventListener('keydown', escape); });
onBeforeUnmount(() => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', escape); });
</script>
<template>
  <div class="session-actions">
    <details ref="menu" class="preferences-menu">
      <summary class="icon-button preferences-trigger" aria-label="Preferências" title="Preferências">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3"/><circle cx="15" cy="17" r="3"/></svg>
      </summary>
      <div class="preferences-panel">
        <p class="menu-title">Preferências</p>
        <button type="button" class="preference-row" :aria-pressed="darkTheme" @click="toggleTheme">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z"/></svg>
          <span>Tema escuro</span><span class="switch" :class="{ on: darkTheme }" aria-hidden="true" />
        </button>
        <button type="button" class="preference-row" :aria-pressed="soundsEnabled" @click="toggleSounds">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 5 6 9H3v6h3l5 4Z"/><path v-if="soundsEnabled" d="M15 8a6 6 0 0 1 0 8"/><path v-else d="m16 9 5 6m0-6-5 6"/></svg>
          <span>Efeitos sonoros</span><span class="switch" :class="{ on: soundsEnabled }" aria-hidden="true" />
        </button>
        <Link v-if="props.allowLogout" href="/logout" method="post" as="button" class="preference-row logout-row" aria-label="Sair da conta"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 4H5v16h5m4-12 4 4-4 4m-5-4h9" /></svg><span>Sair da conta</span></Link>
      </div>
    </details>
  </div>
</template>
