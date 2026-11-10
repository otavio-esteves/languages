<?php

namespace App\Services;

use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/** Written practice feedback only: no evidence, mastery, publication or retention projection. */
class WritingReviews
{
    public function request(User $owner, string $attemptId): array
    {
        $event = DB::table('learning_events')->where('user_id', $owner->id)->where('event_id', $attemptId)->first();
        abort_unless($event, 404);
        $snapshot = app(LearningContent::class)->snapshot($owner, $event->package_hash);
        $package = json_decode($snapshot->document, true);
        $this->eligible($event, $package);
        DB::table('writing_review_requests')->insertOrIgnore([
            'id' => (string) Str::uuid(), 'user_id' => $owner->id, 'learning_event_id' => $event->id, 'requested_at' => now(),
        ]);
        $request = DB::table('writing_review_requests')->where('learning_event_id', $event->id)->first();
        $decisions = $this->decisions(collect([$request]));
        $this->deliver(collect([$request]), collect([$event])->keyBy('id'), $decisions);

        return $this->summary($request, $event, $package, $decisions[$request->id] ?? collect());
    }

    private function eligible(object $event, array $package): array
    {
        $payload = json_decode($event->payload, true);
        $activity = collect($package['activities'])->firstWhere('id', $payload['activity_id']);
        if ($event->kind !== 'attempt' || ($payload['modality'] ?? null) !== 'writing'
            || ($payload['technical_status'] ?? null) !== 'valid'
            || ! in_array($activity['type'] ?? null, ['written_recall', 'communicative_task'], true)
            || ($activity['modality'] ?? null) !== 'writing' || ! is_string($payload['response'] ?? null)
            || preg_match('/^[\p{Z}\s]*$/u', $payload['response']) === 1) {
            throw ValidationException::withMessages(['attempt_id' => 'A revisão exige uma tentativa escrita tecnicamente válida.']);
        }
        $this->rubric($package);

        return $activity;
    }

    private function rubric(array $package): array
    {
        $rubric = $package['rubric'] ?? [];
        $applicable = match (($rubric['id'] ?? '').':'.($rubric['version'] ?? '')) {
            'everyday-text:1' => ['meaning', 'form'],
            'drink-request-text:3' => ['choice', 'meaning', 'form'],
            default => throw ValidationException::withMessages(['attempt_id' => 'A rubrica deste snapshot ainda não possui escopo de revisão escrita suportado.']),
        };
        $expected = $rubric['id'] === 'everyday-text' ? ['meaning', 'reading', 'form'] : ['choice', 'meaning', 'form'];
        if (count($rubric['dimensions'] ?? []) !== count($expected) || array_diff($expected, array_column($rubric['dimensions'] ?? [], 'id'))) {
            throw ValidationException::withMessages(['attempt_id' => 'As dimensões da rubrica não correspondem ao escopo de revisão escrita.']);
        }

        return ['id' => $rubric['id'], 'version' => $rubric['version'], 'dimensions' => array_map(fn ($dimension) => array_merge($dimension, ['applicable' => in_array($dimension['id'], $applicable, true)]), $rubric['dimensions'])];
    }

    private function decisions(Collection $requests, bool $history = false): Collection
    {
        if ($requests->isEmpty()) {
            return collect();
        }
        $query = DB::table('writing_review_decisions')->whereIn('request_id', $requests->pluck('id'));
        if ($history) {
            $ranked = $query->selectRaw('*, row_number() over (partition by request_id order by revision desc) as review_position, count(*) over (partition by request_id) as history_total');

            return DB::query()->fromSub($ranked, 'ranked')->where('review_position', '<=', 10)->orderByDesc('revision')->get()->groupBy('request_id');
        }
        $latest = $query->selectRaw('request_id, max(revision) as latest_revision')->groupBy('request_id');

        return DB::table('writing_review_decisions as decisions')->joinSub($latest, 'latest', function ($join) {
            $join->on('latest.request_id', '=', 'decisions.request_id')->on('latest.latest_revision', '=', 'decisions.revision');
        })->select('decisions.*')->get()->groupBy('request_id');
    }

    /** Delivery means feedback was made available, not that the owner read it. */
    private function deliver(Collection $requests, Collection $events, Collection $decisions): void
    {
        $candidates = [];
        foreach ($requests as $request) {
            $decision = ($decisions[$request->id] ?? collect())->first();
            if ($decision) {
                $event = $events[$request->learning_event_id];
                $candidates[] = ['decision_id' => $decision->id, 'user_id' => $request->user_id, 'package_hash' => $event->package_hash, 'target_ids' => json_encode(json_decode($event->payload, true)['target_ids']), 'modality' => 'writing'];
            }
        }
        if ($candidates === []) {
            return;
        }
        DB::transaction(function () use ($candidates) {
            $known = DB::table('writing_review_deliveries')->whereIn('decision_id', array_column($candidates, 'decision_id'))->pluck('decision_id')->flip();
            $pending = array_values(array_filter($candidates, fn ($row) => ! isset($known[$row['decision_id']])));
            if ($pending === []) {
                return;
            }
            $frontiers = DB::table('learning_events')->whereIn('user_id', array_column($pending, 'user_id'))->selectRaw('user_id, max(id) as frontier')->groupBy('user_id')->pluck('frontier', 'user_id');
            DB::table('writing_review_deliveries')->insertOrIgnore(array_map(fn ($row) => $row + ['delivered_at' => now(), 'last_event_id' => $frontiers[$row['user_id']] ?? 0], $pending));
        });
    }

    public function feedbackContext(User $owner, array $hashes): array
    {
        if ($hashes === []) {
            return [];
        }
        $rows = DB::table('writing_review_deliveries')->crossJoin(DB::raw('jsonb_array_elements_text(target_ids) as target(value)'))
            ->where('user_id', $owner->id)->where('modality', 'writing')->whereIn('package_hash', $hashes)
            ->selectRaw('package_hash, target.value as target_id, min(last_event_id) as frontier')->groupBy('package_hash', 'target.value')->get();
        $result = [];
        foreach ($rows as $row) {
            $result[$row->package_hash][$row->target_id] = (int) $row->frontier;
        }

        return $result;
    }

    private function historyEntry(object $decision): array
    {
        return ['id' => $decision->id, 'revision' => $decision->revision, 'supersedes_id' => $decision->supersedes_id,
            'reviewer_id' => $decision->reviewer_id, 'reviewed_at' => $this->iso($decision->reviewed_at),
            'feedback' => $decision->feedback, 'dimensions' => json_decode($decision->dimensions, true)];
    }

    public function history(User $actor, string $id, int $number): array
    {
        abort_unless($actor->is_reviewer, 403);
        $request = DB::table('writing_review_requests')->where('id', $id)->first();
        abort_unless($request, 404);
        $event = DB::table('learning_events')->where('id', $request->learning_event_id)->first();
        abort_if($event->user_id === $actor->id || ($event->is_preview && ! $actor->is_editor), 403);
        $page = DB::table('writing_review_decisions')->where('request_id', $id)->orderByDesc('revision')->paginate(10, ['*'], 'page', $number);

        return ['data' => collect($page->items())->map(fn ($decision) => $this->historyEntry($decision))->all(), 'pagination' => ['current_page' => $page->currentPage(), 'last_page' => $page->lastPage(), 'total' => $page->total()]];
    }

    private function iso(?string $value): ?string
    {
        return $value === null ? null : CarbonImmutable::parse($value)->toIso8601String();
    }

    private function summary(object $request, object $event, array $package, Collection $history): array
    {
        $latest = $history->first();

        return [
            'id' => $request->id, 'attempt_id' => $event->event_id, 'status' => $latest ? 'reviewed' : 'pending',
            'revision' => $latest?->revision ?? 0, 'is_preview' => (bool) $event->is_preview,
            'requested_at' => $this->iso($request->requested_at), 'reviewed_at' => $this->iso($latest?->reviewed_at),
            'feedback' => $latest?->feedback, 'rubric_id' => $package['rubric']['id'], 'rubric_version' => $package['rubric']['version'],
            'scope_version' => 'writing-review-v1', 'dimensions' => $latest ? json_decode($latest->dimensions, true) : [],
            'source' => $latest ? 'human_review' : 'not_assessed', 'retention_confirmed' => false,
        ];
    }

    /** One batched query for requests and at most one for decisions, never one per attempt. */
    public function forEvents(Collection $events, array $packages, bool $deliver = false): array
    {
        if ($events->isEmpty()) {
            return [];
        }
        $requests = DB::table('writing_review_requests')->whereIn('learning_event_id', $events->pluck('id'))->get();
        $decisions = $this->decisions($requests);
        $indexed = $events->keyBy('id');
        if ($deliver) {
            $this->deliver($requests, $indexed, $decisions);
        }
        $result = [];
        foreach ($requests as $request) {
            $event = $indexed[$request->learning_event_id];
            $result[$event->event_id] = $this->summary($request, $event, $packages[$event->package_hash], $decisions[$request->id] ?? collect());
        }

        return $result;
    }

    public function listing(User $actor, bool $reviewer = false, string $status = 'all', int $perPage = 20): array
    {
        abort_if($reviewer && ! $actor->is_reviewer, 403);
        $query = DB::table('writing_review_requests as requests')->join('learning_events as events', 'events.id', '=', 'requests.learning_event_id');
        if ($reviewer) {
            // Reviewing one's own practice would not be independent review.
            $query->where('requests.user_id', '!=', $actor->id);
            if (! $actor->is_editor) {
                $query->where('events.is_preview', false);
            }
        } else {
            $query->where('requests.user_id', $actor->id);
            if (! $actor->is_editor) {
                $query->where('events.is_preview', false);
            }
        }
        $exists = fn ($subquery) => $subquery->selectRaw('1')->from('writing_review_decisions as decisions')->whereColumn('decisions.request_id', 'requests.id');
        if ($status === 'pending') {
            $query->whereNotExists($exists);
        } elseif ($status === 'reviewed') {
            $query->whereExists($exists);
        }
        $page = $query->orderBy('requests.requested_at')->orderBy('requests.id')->select('requests.*')->paginate(min(50, max(1, $perPage)));
        $requests = collect($page->items());
        $events = $requests->isEmpty() ? collect() : DB::table('learning_events')->whereIn('id', $requests->pluck('learning_event_id'))->get()->keyBy('id');
        $packages = $events->isEmpty() ? [] : DB::table('content_snapshots')->whereIn('hash', $events->pluck('package_hash')->unique())->get()->mapWithKeys(fn ($snapshot) => [$snapshot->hash => json_decode($snapshot->document, true)])->all();
        $decisions = $this->decisions($requests, $reviewer);
        if (! $reviewer) {
            $this->deliver($requests, $events, $decisions);
        }
        $sessions = [];
        $data = $requests->map(function ($request) use ($events, $packages, $decisions, $reviewer, &$sessions) {
            $event = $events[$request->learning_event_id];
            $package = $packages[$event->package_hash];
            $payload = json_decode($event->payload, true);
            $activity = collect($package['activities'])->firstWhere('id', $payload['activity_id']);
            $history = $decisions[$request->id] ?? collect();
            $summary = $this->summary($request, $event, $package, $history) + [
                'prompt' => $activity['prompt'], 'response' => $payload['response'], 'activity_id' => $activity['id'],
                'activity_label' => $activity['content']['label'] ?? 'Atividade',
                'package_id' => $package['id'], 'package_version' => $package['version'], 'package_hash' => $event->package_hash,
            ];
            if ($reviewer) {
                $key = $event->user_id.':'.$event->session_id;
                $sessions[$key] ??= app(LearningEvents::class)->session(User::findOrFail($event->user_id), $event->session_id, false);
                $assessment = collect($sessions[$key]['events'])->firstWhere('id', $event->event_id)['assessment'];
                $summary += [
                    'rubric' => $this->rubric($package), 'automatic_assessment' => $assessment,
                    'support_context' => ['supported' => $assessment['supported'], 'support_pending' => $assessment['support_pending'], 'human_feedback_available' => $assessment['human_feedback_available']],
                    'history' => $history->map(fn ($decision) => $this->historyEntry($decision))->values()->all(),
                    'history_total' => $history->first()?->history_total ?? 0,
                    'history_pagination' => ['current_page' => 1, 'last_page' => max(1, (int) ceil(($history->first()?->history_total ?? 0) / 10)), 'total' => $history->first()?->history_total ?? 0],
                ];
            }

            return $summary;
        })->all();

        return ['data' => $data, 'pagination' => ['current_page' => $page->currentPage(), 'last_page' => $page->lastPage()]];
    }

    public function decide(User $actor, string $id, array $input): array
    {
        abort_unless($actor->is_reviewer, 403);
        if (array_diff(array_keys($input), ['decision_id', 'expected_revision', 'feedback', 'dimensions'])) {
            throw ValidationException::withMessages(['decision' => 'Campos não previstos no contrato de revisão.']);
        }
        $input = Validator::make($input, [
            'decision_id' => 'required|uuid', 'expected_revision' => 'required|integer|min:0',
            'feedback' => 'required|string|max:2000', 'dimensions' => 'required|array|min:1|max:20',
            'dimensions.*' => 'required|array:id,result,reason', 'dimensions.*.id' => 'required|string|max:100',
            'dimensions.*.result' => 'required|in:met,not_met,not_observed,abstained', 'dimensions.*.reason' => 'required|string|max:2000',
        ])->validate();
        $input['feedback'] = preg_replace('/^[\p{Z}\s]+|[\p{Z}\s]+$/u', '', $input['feedback']);
        $input['expected_revision'] = (int) $input['expected_revision'];
        foreach ($input['dimensions'] as &$dimension) {
            $dimension['reason'] = preg_replace('/^[\p{Z}\s]+|[\p{Z}\s]+$/u', '', $dimension['reason']);
            if ($dimension['reason'] === '') {
                throw ValidationException::withMessages(['dimensions' => 'Justifique cada dimensão.']);
            }
        }
        unset($dimension);
        if ($input['feedback'] === '') {
            throw ValidationException::withMessages(['feedback' => 'Informe um comentário escrito.']);
        }

        try {
            return DB::transaction(function () use ($actor, $id, $input) {
                $request = DB::table('writing_review_requests')->where('id', $id)->lockForUpdate()->first();
                abort_unless($request, 404);
                $event = DB::table('learning_events')->where('id', $request->learning_event_id)->first();
                abort_if($event->user_id === $actor->id, 403, 'Não é permitido revisar a própria resposta.');
                abort_if($event->is_preview && ! $actor->is_editor, 403);
                $snapshot = DB::table('content_snapshots')->where('hash', $event->package_hash)->first();
                $package = json_decode($snapshot->document, true);
                $this->eligible($event, $package);
                $rubric = $this->rubric($package);
                $dimensions = collect($input['dimensions'])->keyBy('id');
                $ids = array_column($rubric['dimensions'], 'id');
                if (count($input['dimensions']) !== count($ids) || $dimensions->count() !== count($ids) || array_diff($dimensions->keys()->all(), $ids)) {
                    throw ValidationException::withMessages(['dimensions' => 'Informe cada dimensão da rubrica exatamente uma vez.']);
                }
                foreach ($rubric['dimensions'] as $dimension) {
                    if (! $dimension['applicable'] && $dimensions[$dimension['id']]['result'] !== 'not_observed') {
                        throw ValidationException::withMessages(['dimensions' => 'Uma revisão escrita não pode avaliar leitura ou outra modalidade.']);
                    }
                }
                $ordered = $dimensions->sortKeys()->map(fn ($dimension) => ['id' => $dimension['id'], 'result' => $dimension['result'], 'reason' => $dimension['reason']])->values()->all();
                $payload = ['request_id' => $id, 'reviewer_id' => $actor->id, 'expected_revision' => $input['expected_revision'], 'feedback' => $input['feedback'], 'dimensions' => $ordered];
                $hash = hash('sha256', json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
                $duplicate = DB::table('writing_review_decisions')->where('id', $input['decision_id'])->first();
                if ($duplicate) {
                    abort_unless($duplicate->payload_hash === $hash, 409, 'Identificador de decisão já usado com outro conteúdo.');
                } else {
                    $latest = DB::table('writing_review_decisions')->where('request_id', $id)->orderByDesc('revision')->first();
                    abort_unless(($latest?->revision ?? 0) === $input['expected_revision'], 409, 'A revisão mudou. Atualize a fila antes de decidir.');
                    DB::table('writing_review_decisions')->insert([
                        'id' => $input['decision_id'], 'request_id' => $id, 'reviewer_id' => $actor->id,
                        'revision' => ($latest?->revision ?? 0) + 1, 'supersedes_id' => $latest?->id, 'payload_hash' => $hash,
                        'feedback' => $input['feedback'], 'dimensions' => json_encode($ordered), 'reviewed_at' => now(),
                    ]);
                }
                $history = DB::table('writing_review_decisions')->where('request_id', $id)->orderByDesc('revision')->limit(1)->get();

                return $this->summary($request, $event, $package, $history);
            });
        } catch (UniqueConstraintViolationException) {
            abort(409, 'Identificador de decisão ou revisão já utilizado. Atualize a fila.');
        }
    }
}
