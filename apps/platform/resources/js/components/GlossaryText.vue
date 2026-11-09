<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue';
import type { Gloss } from '../lib/textTrail';
const props = defineProps<{ text: string; glossary?: Record<string, Gloss>; lookup: (entry: Gloss) => Promise<boolean> }>();
const root = ref<HTMLElement | null>(null), active = ref<number | null>(null), opened = ref<number | null>(null), loading = ref(false);
const id = useId();
const focused = ref<number | null>(null);
const tooltipStyle = ref<Record<string, string>>({});
const pieces = computed(() => props.text.split(/([A-Za-z]+(?:['’‘ʼ][A-Za-z]+)*)/g));
const entry = (word: string) => props.glossary?.[word.toLowerCase().replace(/[’‘ʼ]/g, "'")];
let timer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
const pending = new Map<string, Promise<boolean>>();
async function show(index: number) {
  clearTimeout(timer); active.value = index; opened.value = null;
  const meaning = entry(pieces.value[index]);
  if (!meaning) return;
  loading.value = true;
  let request = pending.get(meaning.support_id);
  if (!request) {
    request = props.lookup(meaning).catch(() => false).finally(() => pending.delete(meaning.support_id));
    pending.set(meaning.support_id, request);
  }
  await nextTick();
  positionTooltip();
  const allowed = await request;
  if (!disposed && active.value === index) { opened.value = allowed ? index : null; loading.value = false;
    await nextTick();
    positionTooltip();
  }
}
function positionTooltip() {
  const index = active.value;
  if (index === null) return;
  const word = root.value?.querySelector<HTMLElement>(`[data-word-index="${index}"]`);
  const tooltip = root.value?.querySelector<HTMLElement>('[role="tooltip"]');
  if (word && tooltip) {
    const bounds = word.getBoundingClientRect(), tip = tooltip.getBoundingClientRect();
    tooltipStyle.value = { left: `${Math.max(8, Math.min(innerWidth - tip.width - 8, bounds.left))}px`, top: `${bounds.top >= tip.height + 12 ? bounds.top - tip.height - 8 : bounds.bottom + 8}px` };
  }
}
function hover(index: number, event: PointerEvent) { if (event.pointerType === 'mouse') { clearTimeout(timer); timer = setTimeout(() => show(index), 160); } }
function keepOpen() { clearTimeout(timer); }
function close() { clearTimeout(timer); active.value = null; opened.value = null; loading.value = false; }
function focusWord(index: number) { focused.value = index; show(index); }
function blurWord() { focused.value = null; close(); }
function leave() { if (focused.value !== null && active.value === focused.value) return; clearTimeout(timer); timer = setTimeout(close, 160); }
function outside(event: Event) { if (!root.value?.contains(event.target as Node)) close(); }
onMounted(() => { document.addEventListener('pointerdown', outside); window.addEventListener('scroll', positionTooltip, true); window.addEventListener('resize', positionTooltip); });
onBeforeUnmount(() => { disposed = true; clearTimeout(timer); document.removeEventListener('pointerdown', outside); window.removeEventListener('scroll', positionTooltip, true); window.removeEventListener('resize', positionTooltip); });
</script>
<template>
  <span ref="root" class="glossary-text" lang="en" @keydown.esc.stop="close">
    <template v-for="(piece, position) in pieces" :key="position">
      <span v-if="entry(piece)" class="gloss-token" @pointerenter="hover(position, $event)" @pointerleave="leave">
        <button type="button" class="gloss-word" :data-word-index="position" :aria-busy="active === position && loading" :aria-describedby="opened === position ? `${id}-${position}` : undefined" :aria-label="`${piece}: consultar significado`" @focus="focusWord(position)" @blur="blurWord" @click="show(position)">{{ piece }}</button>
        <span v-if="active === position && (loading || opened === position)" :id="`${id}-${position}`" class="word-meaning" :style="tooltipStyle" role="tooltip" lang="pt-BR" @pointerenter="keepOpen" @pointerleave="leave">{{ opened === position ? entry(piece)?.meaning : 'Consultando…' }}</span>
      </span><template v-else>{{ piece }}</template>
    </template>
  </span>
</template>
