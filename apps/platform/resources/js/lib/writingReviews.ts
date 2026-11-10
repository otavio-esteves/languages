export type ReviewDimensionResult = 'met' | 'not_met' | 'not_observed' | 'abstained';
export type ReviewDimension = { id: string; result: ReviewDimensionResult; reason: string };
export type WritingReviewSummary = {
  id: string; attempt_id: string; status: 'pending' | 'reviewed'; revision: number;
  is_preview: boolean; requested_at: string; reviewed_at: string | null; feedback: string | null;
  rubric_id: string; rubric_version: number; scope_version: 'writing-review-v1';
  dimensions: ReviewDimension[]; source: 'not_assessed' | 'human_review'; retention_confirmed: false;
};
export type ReviewActivity = { prompt: string; response: string; activity_id: string; activity_label: string; package_id: string; package_version: number; package_hash: string };
export type WritingFeedbackItem = WritingReviewSummary & ReviewActivity;
export type ReviewRubricDimension = { id: string; essential: boolean; criterion: string; applicable: boolean };
export type ReviewHistoryItem = { id?: string; decision_id?: string; revision: number; reviewed_at?: string | null; assessed_at?: string; feedback: string; dimensions: ReviewDimension[] };
export type WritingReviewItem = WritingFeedbackItem & {
  rubric: { id: string; version: number; dimensions: ReviewRubricDimension[] };
  automatic_assessment: { outcome: string; message: string; source?: string };
  support_context: { supported: boolean; support_pending: boolean }; history: ReviewHistoryItem[];
  history_total?: number; history_pagination?: ReviewHistoryPagination;
};
export type ReviewPagination = { current_page: number; last_page: number };
export type ReviewHistoryPagination = ReviewPagination & { total: number };
export type WritingReviewDecision = { decision_id: string; expected_revision: number; feedback: string; dimensions: ReviewDimension[] };
export const reviewDimensionLabels: Record<ReviewDimensionResult, string> = {
  met: 'Atendida nesta resposta', not_met: 'Não atendida nesta resposta', not_observed: 'Não observada', abstained: 'Sem conclusão',
};
export function reviewDimensionName(id: string): string {
  return ({ meaning: 'Sentido da mensagem', reading: 'Compreensão de leitura', form: 'Forma linguística', choice: 'Escolha comunicada', 'item-and-size': 'Item e tamanho', repair: 'Pedido de esclarecimento', 'answer-followup': 'Resposta de continuidade', 'target-form': 'Expressão em prática', comprehensibility: 'Compreensibilidade' } as Record<string, string>)[id] || id;
}
export function reviewDate(value: string | null | undefined): string {
  if (!value || !Number.isFinite(Date.parse(value))) return 'Data não disponível';
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}
export function reviewCsrfToken(): string {
  const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : '';
}
export function initialReviewDimensions(rubric: { dimensions: ReviewRubricDimension[] }): ReviewDimension[] {
  return rubric.dimensions.map(dimension => ({ id: dimension.id, result: dimension.applicable ? 'abstained' : 'not_observed', reason: dimension.applicable ? '' : 'Esta dimensão não é observada na revisão de uma produção escrita.' }));
}
export function mergeReviewHistory(previous: ReviewHistoryItem[], incoming: ReviewHistoryItem[]): ReviewHistoryItem[] {
  const result = [...previous];
  for (const item of incoming) {
    const index = result.findIndex(existing => existing.revision === item.revision || (!!item.id && existing.id === item.id));
    if (index === -1) result.push(item); else result[index] = item;
  }
  return result.sort((a, b) => b.revision - a.revision);
}
