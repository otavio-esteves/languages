<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue';
import { Link, router } from '@inertiajs/vue3';
import BrandMark from '../components/BrandMark.vue';
import SessionActions from '../components/SessionActions.vue';
import ManualReviewPanel from '../components/ManualReviewPanel.vue';
import { initialReviewDimensions, mergeReviewHistory, reviewCsrfToken, reviewDate, reviewDimensionName, reviewDimensionLabels, type ReviewDimension, type ReviewPagination, type ReviewHistoryItem, type ReviewHistoryPagination, type WritingReviewDecision, type WritingReviewItem } from '../lib/writingReviews';
const props = defineProps<{ reviews: WritingReviewItem[]; filters: { status: string }; pagination: ReviewPagination }>();
type Draft = { baseRevision: number; feedback: string; dimensions: ReviewDimension[]; busy: boolean; error: string; conflict: boolean; pending: WritingReviewDecision | null };
const drafts = reactive<Record<string, Draft>>({});
function draftFor(review: WritingReviewItem): Draft {
  return drafts[review.id] ??= { baseRevision: review.revision, feedback: '', dimensions: initialReviewDimensions(review.rubric), busy: false, error: '', conflict: false, pending: null };
}
function locked(review: WritingReviewItem) { const draft = draftFor(review); return draft.busy || draft.conflict || draft.pending !== null; }
function reload(id?: string, discardDraft = false) { router.reload({ only: ['reviews', 'pagination'], onSuccess: () => {
  if (!id) return;
  const draft = drafts[id], current = props.reviews.find(review => review.id === id);
  if (discardDraft) { delete drafts[id]; return; }
  if (draft && current && draft.baseRevision !== current.revision) {
    if (draft.pending) draft.error = 'Há uma decisão mais recente. Reenvie a mesma decisão para recuperar a confirmação ou verificar o conflito.';
    else { draft.conflict = true; draft.error = 'A revisão mudou enquanto você escrevia. Seu rascunho foi preservado; confira a decisão atual antes de descartá-lo e iniciar outra revisão.'; }
  }
} }); }
type HistoryState = { revision: number; items: ReviewHistoryItem[]; pagination: ReviewHistoryPagination; busy: boolean; error: string };
const histories = reactive<Record<string, HistoryState>>({});
function historyFor(review: WritingReviewItem): HistoryState {
  if (!histories[review.id] || histories[review.id].revision !== review.revision) histories[review.id] = {
    revision: review.revision, items: mergeReviewHistory([], review.history), busy: false, error: '',
    pagination: review.history_pagination || { current_page: 1, last_page: 1, total: review.history_total ?? review.history.length },
  };
  return histories[review.id];
}
async function loadMoreHistory(review: WritingReviewItem) {
  const history = historyFor(review);
  if (history.busy || history.pagination.current_page >= history.pagination.last_page) return;
  history.busy = true; history.error = '';
  try {
    const response = await fetch(`/reviews/${encodeURIComponent(review.id)}/history?page=${history.pagination.current_page + 1}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error('Não foi possível carregar o histórico. Tente novamente.');
    const result = await response.json() as { data: ReviewHistoryItem[]; pagination: ReviewHistoryPagination };
    if (history.revision !== review.revision) return;
    history.items = mergeReviewHistory(history.items, result.data); history.pagination = result.pagination;
  } catch { history.error = 'Não foi possível carregar o histórico. Seu rascunho continua preservado; tente novamente.'; }
  finally { history.busy = false; }
}
function navigate(status: string, page = 1) { router.get('/reviews', { status, page }, { preserveScroll: false }); }
async function submit(review: WritingReviewItem) {
  const draft = draftFor(review);
  if (draft.busy || draft.conflict) return;
  draft.error = '';
  if (!draft.pending) {
    if (draft.baseRevision !== review.revision) { draft.conflict = true; draft.error = 'A revisão mudou enquanto você escrevia. Confira a decisão atual antes de descartar seu rascunho e iniciar outra revisão.'; return; }
    if (!draft.feedback.trim() || draft.dimensions.some(dimension => !dimension.reason.trim())) { draft.error = 'Preencha o retorno e a justificativa de cada dimensão.'; return; }
    draft.pending = { decision_id: crypto.randomUUID(), expected_revision: draft.baseRevision, feedback: draft.feedback.trim(), dimensions: draft.dimensions.map(dimension => ({ ...dimension, reason: dimension.reason.trim() })) };
  }
  draft.busy = true;
  try {
    const response = await fetch(`/reviews/${encodeURIComponent(review.id)}/decisions`, { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-XSRF-TOKEN': reviewCsrfToken() }, body: JSON.stringify(draft.pending), signal: AbortSignal.timeout(15000) });
    if (response.status === 409) { draft.conflict = true; draft.error = 'A revisão mudou ou este envio entrou em conflito. Recarregue o registro e confira a decisão atual antes de editar novamente.'; return; }
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null;
      if (response.status === 422 || response.status === 400) draft.pending = null;
      throw new Error(error?.message || (response.status === 401 || response.status === 403 ? 'Sua sessão ou permissão não permite registrar esta revisão. Recarregue a página para verificar o acesso.' : 'A confirmação do registro não foi recebida. Reenvie a mesma decisão.'));
    }
    router.reload({ only: ['reviews', 'pagination'], onSuccess: () => { delete drafts[review.id]; }, onError: () => { draft.error = 'A decisão foi recebida, mas não foi possível atualizar a página. Reenvie para recuperar a confirmação.'; } });
  } catch (error) { draft.error = error instanceof DOMException && ['TimeoutError', 'AbortError'].includes(error.name) ? 'A confirmação não chegou a tempo. Reenvie a mesma decisão; o texto e o identificador foram preservados.' : error instanceof Error ? error.message : 'A confirmação não foi recebida. Reenvie a mesma decisão.'; }
  finally { draft.busy = false; }
}
function hasUnsavedWork() { return Object.values(drafts).some(draft => draft.pending !== null || draft.feedback.trim() || draft.dimensions.some(dimension => dimension.result !== 'not_observed' && dimension.reason.trim())); }
function beforeUnload(event: BeforeUnloadEvent) { if (hasUnsavedWork()) { event.preventDefault(); event.returnValue = ''; } }
let removeGuard: (() => void) | undefined;
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload);
  removeGuard = router.on('before', event => {
    const visit = event.detail.visit;
    if ((visit.url.pathname !== window.location.pathname || visit.url.search !== window.location.search || visit.method !== 'get') && hasUnsavedWork()
      && !window.confirm('Há uma revisão em edição ou sem confirmação. Sair descartará este estado local. Deseja sair?')) event.preventDefault();
  });
});
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); removeGuard?.(); });
</script>
<template>
  <div class="reviews-page"><a class="skip-link" href="#writing-reviews">Ir para revisões</a>
    <header class="reviews-top"><BrandMark /><SessionActions /></header>
    <main id="writing-reviews"><Link href="/" class="back-link">← Voltar ao início</Link><div class="page-title"><div><h1>Revisão de respostas escritas</h1><p>Julgue a resposta no contexto da atividade e da rubrica apresentada.</p></div><Link href="/writing-feedback" class="secondary button-link">Minhas revisões</Link></div>
      <nav class="filters" aria-label="Filtrar revisões"><button v-for="filter in [{ id: 'pending', label: 'Pendentes' }, { id: 'reviewed', label: 'Revisadas' }, { id: 'all', label: 'Todas' }]" :key="filter.id" class="secondary" :aria-pressed="filters.status === filter.id" @click="navigate(filter.id)">{{ filter.label }}</button></nav>
      <p v-if="!reviews.length" class="empty-review">Nenhuma revisão nesta seleção.</p>
      <article v-for="review in reviews" :key="review.id" class="review-card" :aria-labelledby="`review-title-${review.id}`">
        <div class="review-heading"><h2 :id="`review-title-${review.id}`">{{ review.activity_label || 'Resposta escrita' }}</h2><span class="review-meta">{{ review.package_id }} · versão {{ review.package_version }}</span></div>
        <p class="prompt">{{ review.prompt }}</p><div class="response"><span>Resposta enviada</span><p lang="en">{{ review.response }}</p></div>
        <section class="context" :aria-label="`Contexto da resposta ${review.activity_label || ''}`"><h3>Contexto registrado</h3><p>{{ review.support_context.support_pending ? 'Há referência de apoio ainda sem confirmação; contexto provisório.' : review.support_context.supported ? 'Há apoio registrado nesta tentativa.' : 'Não há apoio conhecido registrado para esta tentativa.' }}</p><p class="review-meta">{{ review.is_preview ? 'Prévia editorial, sem validação de aprendizagem.' : 'Produção escrita registrada.' }} Rubrica {{ review.rubric.id }} · versão {{ review.rubric.version }}.</p><details><summary>Comparação automática de treino</summary><p>{{ review.automatic_assessment.message }}</p><p class="review-meta">{{ review.automatic_assessment.outcome === 'unverified' ? 'Forma não reconhecida pela regra; não é reprovação.' : 'Este retorno automático permanece separado do julgamento humano.' }}</p></details></section>
        <ManualReviewPanel :review="review" :supported="review.support_context.supported" :support-pending="review.support_context.support_pending" :busy="draftFor(review).busy" @refresh="reload(review.id)" />
        <details v-if="historyFor(review).pagination.total" class="history"><summary>Histórico de decisões ({{ historyFor(review).pagination.total }})</summary><p class="review-meta">Mostrando {{ historyFor(review).items.length }} de {{ historyFor(review).pagination.total }} decisões.</p><section v-for="decision in historyFor(review).items" :key="decision.id || decision.decision_id || decision.revision"><h3>Revisão {{ decision.revision }} · {{ reviewDate(decision.reviewed_at || decision.assessed_at) }}</h3><p>{{ decision.feedback }}</p><ul><li v-for="dimension in decision.dimensions" :key="dimension.id"><strong>{{ reviewDimensionName(dimension.id) }} · {{ reviewDimensionLabels[dimension.result] }}</strong><p>{{ dimension.reason }}</p></li></ul></section><p v-if="historyFor(review).error" role="alert" class="notice">{{ historyFor(review).error }}</p><button v-if="historyFor(review).pagination.current_page < historyFor(review).pagination.last_page" type="button" class="secondary" :disabled="historyFor(review).busy" @click="loadMoreHistory(review)">{{ historyFor(review).busy ? 'Carregando…' : 'Carregar mais histórico' }}</button></details>
        <form :aria-busy="draftFor(review).busy" @submit.prevent="submit(review)">
          <h3>{{ review.status === 'reviewed' ? 'Registrar nova revisão' : 'Avaliar esta resposta' }}</h3><p class="review-meta">Nenhuma dimensão começa como atendida. Registre somente o que esta produção permite observar.</p>
          <fieldset v-for="(dimension, index) in review.rubric.dimensions" :key="dimension.id" :disabled="locked(review)"><legend>{{ reviewDimensionName(dimension.id) }}{{ dimension.essential ? ' · essencial na rubrica' : '' }}</legend><p>{{ dimension.criterion }}</p><p v-if="!dimension.applicable" class="review-meta">Esta dimensão não é observada em uma resposta escrita e permanece “Não observada”.</p><label :for="`result-${review.id}-${index}`">Observação</label><select :id="`result-${review.id}-${index}`" v-model="draftFor(review).dimensions[index].result" :disabled="!dimension.applicable"><option v-for="(label, value) in reviewDimensionLabels" :key="value" :value="value">{{ label }}</option></select><label :for="`reason-${review.id}-${index}`">Justificativa</label><textarea :id="`reason-${review.id}-${index}`" v-model="draftFor(review).dimensions[index].reason" rows="2" maxlength="2000" :readonly="!dimension.applicable" required /></fieldset>
          <label :for="`feedback-${review.id}`">Retorno para a pessoa</label><textarea :id="`feedback-${review.id}`" v-model="draftFor(review).feedback" rows="4" maxlength="2000" :disabled="locked(review)" placeholder="Explique o que foi observado e uma orientação útil, quando pertinente." required />
          <p v-if="draftFor(review).error" role="alert" class="notice">{{ draftFor(review).error }}</p><p v-if="draftFor(review).pending && !draftFor(review).conflict" class="review-meta" role="status">O envio mantém a mesma decisão e justificativas até recuperar a confirmação.</p><div class="form-actions"><button v-if="draftFor(review).conflict" type="button" class="secondary" @click="reload(review.id, true)">Descartar rascunho e recarregar revisão atual</button><button v-else class="primary-action" :disabled="draftFor(review).busy">{{ draftFor(review).busy ? 'Registrando…' : draftFor(review).pending ? 'Reenviar mesma decisão' : review.status === 'reviewed' ? 'Registrar nova revisão' : 'Registrar revisão humana' }}</button></div>
        </form>
      </article>
      <nav v-if="pagination.last_page > 1" class="pages" aria-label="Páginas de revisões"><button class="secondary" :disabled="pagination.current_page <= 1" @click="navigate(props.filters.status, pagination.current_page - 1)">Anterior</button><span>Página {{ pagination.current_page }} de {{ pagination.last_page }}</span><button class="secondary" :disabled="pagination.current_page >= pagination.last_page" @click="navigate(props.filters.status, pagination.current_page + 1)">Próxima</button></nav>
    </main>
  </div>
</template>
<style scoped>
.reviews-page { max-width:1040px; margin:auto; padding:24px; }
.reviews-top,.review-heading,.page-title,.pages,.form-actions { display:flex; align-items:center; flex-wrap:wrap; justify-content:space-between; gap:16px; }
.reviews-top { margin-bottom:32px; }
.back-link { display:inline-block; padding-block:12px; color:var(--accent-ink); }
.page-title { align-items:flex-start; margin:18px 0 26px; }
.page-title p { margin-top:10px; color:var(--muted); }
.filters { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:24px; }
.filters [aria-pressed="true"] { background:var(--accent-soft); color:var(--accent-ink); border-color:var(--accent); }
.review-card { display:grid; gap:22px; margin-bottom:30px; padding:26px; border:1px solid var(--line); border-radius:18px; background:var(--surface); overflow-wrap:anywhere; }
.review-card h3 { margin:0; font-size:1rem; font-weight:600; }
.review-meta { color:var(--muted); font-size:.8rem; }
.prompt { font-size:1.05rem; }
.response { padding:16px; border-block:1px solid var(--line); }
.response span { color:var(--muted); font-size:.8rem; }
.response p,.history p { white-space:pre-wrap; }
.response p { margin-top:8px; }
.context { display:grid; gap:10px; }
.context details p { margin-top:10px; }
summary { cursor:pointer; padding-block:12px; min-height:44px; color:var(--accent-ink); }
.history section { display:grid; gap:10px; padding:16px 0; border-top:1px solid var(--line); }
.history ul { padding-left:22px; margin:0; }
.history li { padding-block:8px; }
.history li p { margin-top:4px; }
form { display:grid; gap:12px; }
fieldset { display:grid; gap:10px; min-width:0; margin:0; padding:16px; border:1px solid var(--line); border-radius:12px; }
legend { padding-inline:6px; font-weight:600; }
label { font-size:.85rem; }
select,textarea { min-width:0; width:100%; padding:12px; border:1px solid var(--line); border-radius:10px; background:var(--bg); color:var(--text); font:inherit; }
select { min-height:48px; }
textarea { resize:vertical; }
textarea:read-only { color:var(--muted); }
.form-actions { justify-content:flex-end; }
.empty-review { margin-block:28px; padding:24px; border:1px solid var(--line); border-radius:14px; }
@media(max-width:500px) { .reviews-page { padding:18px; } .review-card { padding:16px; } .form-actions button { width:100%; } fieldset { padding:12px; } }
</style>
