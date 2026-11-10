<script setup lang="ts">
import { reviewDate, reviewDimensionName, reviewDimensionLabels, type WritingReviewSummary } from '../lib/writingReviews';
defineProps<{ review: WritingReviewSummary; supported?: boolean; supportPending?: boolean; busy?: boolean }>();
defineEmits<{ refresh: [] }>();
</script>
<template>
  <section class="manual-review" aria-label="Revisão da resposta escrita" :aria-busy="busy">
    <div class="review-heading"><h2 tabindex="-1">{{ review.status === 'pending' ? 'Revisão solicitada' : 'Revisão humana' }}</h2><span v-if="review.is_preview" class="preview-label">Prévia editorial</span></div>
    <template v-if="review.status === 'pending'"><p>A resposta aguarda avaliação humana. A prática pode continuar.</p><p class="review-meta">Solicitada em {{ reviewDate(review.requested_at) }}. Ainda não há uma conclusão humana.</p></template>
    <template v-else><p class="review-feedback">{{ review.feedback }}</p><dl v-if="review.dimensions.length" class="review-dimensions"><div v-for="dimension in review.dimensions" :key="dimension.id"><dt>{{ reviewDimensionName(dimension.id) }} · {{ reviewDimensionLabels[dimension.result] }}</dt><dd>{{ dimension.reason }}</dd></div></dl><p class="review-meta">{{ reviewDate(review.reviewed_at) }} · revisão {{ review.revision }}</p></template>
    <p v-if="supportPending" class="support-note">Há apoio ainda sem confirmação. O contexto permanece provisório.</p><p v-else-if="supported" class="support-note">Há contexto de apoio registrado para esta tentativa.</p>
    <p class="review-meta">Esta revisão se refere à resposta escrita. Não confirma domínio, escuta, fala ou retenção posterior.</p>
    <button type="button" class="secondary" :disabled="busy" @click="$emit('refresh')">{{ busy ? 'Atualizando…' : 'Atualizar revisão' }}</button>
  </section>
</template>
<style scoped>
.manual-review { display:grid; gap:14px; padding:22px; border:1px solid var(--line); border-radius:16px; background:var(--accent-soft); overflow-wrap:anywhere; }
.review-heading { display:flex; flex-wrap:wrap; align-items:baseline; gap:10px; justify-content:space-between; }
.review-heading h2 { font-size:1.1rem; }
.preview-label,.review-meta { color:var(--muted); font-size:.8rem; }
.preview-label { padding:4px 8px; border:1px solid var(--line); border-radius:6px; background:var(--surface); }
.review-feedback,.review-dimensions dd { white-space:pre-wrap; }
.review-dimensions { display:grid; gap:12px; margin:0; }
.review-dimensions dt { font-weight:600; font-size:.9rem; }
.review-dimensions dd { margin:5px 0 0; font-size:.9rem; }
.support-note { padding:10px; background:var(--surface); border-radius:8px; font-size:.85rem; }
.manual-review button { justify-self:start; }
@media(max-width:400px) { .manual-review { padding:16px; } .manual-review button { width:100%; } }
</style>
