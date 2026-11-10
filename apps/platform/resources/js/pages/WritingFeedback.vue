<script setup lang="ts">
import { ref } from 'vue';
import { Link, router } from '@inertiajs/vue3';
import BrandMark from '../components/BrandMark.vue';
import SessionActions from '../components/SessionActions.vue';
import ManualReviewPanel from '../components/ManualReviewPanel.vue';
import type { ReviewPagination, WritingFeedbackItem } from '../lib/writingReviews';
defineProps<{ reviews: WritingFeedbackItem[]; pagination: ReviewPagination }>();
const busy = ref(false), message = ref('');
function refresh() { if (busy.value) return; busy.value = true; message.value = ''; router.reload({ only: ['reviews', 'pagination'], onError: () => { message.value = 'Não foi possível atualizar. Tente novamente.'; }, onFinish: () => { busy.value = false; } }); }
function page(value: number) { router.get('/writing-feedback', { page: value }, { preserveScroll: false }); }
</script>
<template>
  <div class="feedback-page"><a class="skip-link" href="#writing-feedback">Ir para resultados das revisões</a><header class="feedback-top"><BrandMark /><SessionActions /></header><main id="writing-feedback"><Link href="/" class="back-link">← Voltar ao início</Link><div class="page-title"><div><h1>Revisões da sua escrita</h1><p>Consulte os pedidos e o retorno de cada resposta.</p></div><button class="secondary" :disabled="busy" @click="refresh">{{ busy ? 'Atualizando…' : 'Atualizar resultados' }}</button></div><p v-if="message" role="alert" class="notice">{{ message }}</p><p v-if="!reviews.length" class="empty-review">Você ainda não solicitou revisão de respostas escritas.</p><article v-for="review in reviews" :key="review.id" class="feedback-card"><h2>{{ review.activity_label || 'Resposta escrita' }}</h2><p class="prompt">{{ review.prompt }}</p><div class="response"><span>Sua resposta</span><p lang="en">{{ review.response }}</p></div><ManualReviewPanel :review="review" :busy="busy" @refresh="refresh" /></article><nav v-if="pagination.last_page > 1" class="pages" aria-label="Páginas de revisões"><button class="secondary" :disabled="pagination.current_page <= 1 || busy" @click="page(pagination.current_page - 1)">Anterior</button><span>Página {{ pagination.current_page }} de {{ pagination.last_page }}</span><button class="secondary" :disabled="pagination.current_page >= pagination.last_page || busy" @click="page(pagination.current_page + 1)">Próxima</button></nav></main></div>
</template>
<style scoped>
.feedback-page { max-width:1000px; margin:auto; padding:24px; }
.feedback-top,.page-title,.pages { display:flex; flex-wrap:wrap; gap:18px; align-items:center; justify-content:space-between; }
.feedback-top { margin-bottom:36px; }
.back-link { color:var(--accent-ink); display:inline-block; padding-block:12px; }
.page-title { margin:18px 0 28px; align-items:flex-start; }
.page-title p,.prompt { color:var(--muted); margin-top:10px; }
.feedback-card { display:grid; gap:18px; margin-bottom:24px; padding:24px; border:1px solid var(--line); border-radius:18px; background:var(--surface); overflow-wrap:anywhere; }
.response { padding:16px; border-block:1px solid var(--line); }
.response span { font-size:.8rem; color:var(--muted); }
.response p { white-space:pre-wrap; margin-top:8px; }
.empty-review { margin:30px 0; padding:24px; border:1px solid var(--line); border-radius:16px; }
.pages span { color:var(--muted); font-size:.85rem; }
@media(max-width:400px) { .feedback-page { padding:18px; } .feedback-card { padding:16px; } .page-title button { width:100%; } }
</style>
