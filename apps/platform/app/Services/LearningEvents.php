<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Facades\DB;
use Opis\JsonSchema\Validator;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

class LearningEvents
{
    public function answeredActivities(User $user, array $hashes): array
    {
        if ($hashes === []) {
            return [];
        }

        return DB::table('learning_events')->where('user_id', $user->id)
            ->whereIn('package_hash', $hashes)->where('kind', 'attempt')
            ->where('payload->technical_status', 'valid')
            ->selectRaw("package_hash, payload->>'activity_id' as activity_id")->distinct()->get()
            ->groupBy('package_hash')->map(fn ($rows) => $rows->pluck('activity_id')->all())->all();
    }

    private function canonical(mixed $value): mixed
    {
        if (! is_array($value)) {
            return $value;
        }
        if (! array_is_list($value)) {
            ksort($value, SORT_STRING);
        }

        return array_map(fn ($v) => $this->canonical($v), $value);
    }

    public function receive(User $user, string $kind, array $items): array
    {
        return array_map(function ($input) use ($user, $kind) {
            $id = is_array($input) ? ($input['id'] ?? null) : null;
            try {
                if (! is_array($input)) {
                    throw new \InvalidArgumentException('Evento inválido.');
                }
                $schema = json_decode(file_get_contents(config('learning.contracts_path').'/'.$kind.'-event.schema.json'));
                if (! (new Validator)->validate(json_decode(json_encode($input)), $schema)->isValid()) {
                    throw new \InvalidArgumentException('Evento incompatível com o contrato.');
                }
                $snapshot = app(LearningContent::class)->snapshot($user, $input['package_hash']);
                $package = json_decode($snapshot->document, true);
                if ($package['id'] !== $input['package_id'] || $package['version'] !== $input['package_version']) {
                    throw new \InvalidArgumentException('Versão do conteúdo não corresponde ao evento.');
                }
                $activity = collect($package['activities'])->firstWhere('id', $input['activity_id']);
                if (! $activity || array_diff($input['target_ids'], $activity['target_ids'])) {
                    throw new \InvalidArgumentException('Atividade ou alvo incompatível.');
                }
                if (array_diff($activity['target_ids'], $input['target_ids'])) {
                    throw new \InvalidArgumentException('Alvos declarados não correspondem à atividade.');
                }
                if ($kind === 'attempt' && $input['technical_status'] === 'valid' && $activity['type'] === 'information_question' && ! in_array($input['response'], $activity['content']['options'] ?? [], true)) {
                    throw new \InvalidArgumentException('A resposta não corresponde às opções desta tarefa fechada.');
                }
                if ($kind === 'attempt' && $input['modality'] !== $activity['modality']) {
                    throw new \InvalidArgumentException('Modalidade incompatível.');
                }
                if ($kind === 'exposure' && $input['kind'] !== 'training_use') {
                    $support = collect($activity['supports'])->firstWhere('id', $input['support_id']);
                    if (! $support || $support['kind'] !== $input['kind'] || $support['reveals_answer'] !== $input['reveals_answer'] || (array_diff($input['affected_modalities'], $support['affected_modalities']) || array_diff($support['affected_modalities'], $input['affected_modalities']))) {
                        throw new \InvalidArgumentException('Apoio incompatível com a atividade.');
                    }
                }
                if ($kind === 'attempt' && $input['audio_reference'] !== null && ! preg_match('/^local-only:[a-f0-9-]{36}$/', $input['audio_reference'])) {
                    throw new \InvalidArgumentException('Gravações locais não são enviadas neste lote.');
                }
                $digest = hash('sha256', json_encode($this->canonical($input), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
                $result = DB::transaction(function () use ($user, $kind, $input, $snapshot, $digest) {
                    $existing = DB::table('learning_events')->where('user_id', $user->id)->where('event_id', $input['id'])->first();
                    if ($existing) {
                        return ['id' => $input['id'], 'status' => $existing->payload_hash === $digest && $existing->kind === $kind ? 'duplicate' : 'conflict'];
                    }
                    DB::table('learning_events')->insert(['user_id' => $user->id, 'event_id' => $input['id'], 'session_id' => $input['session_id'], 'device_id' => $input['device_id'], 'local_sequence' => $input['local_sequence'], 'kind' => $kind, 'package_hash' => $input['package_hash'], 'payload_hash' => $digest, 'is_preview' => $snapshot->is_preview, 'payload' => json_encode($input), 'created_at' => now(), 'updated_at' => now()]);

                    return ['id' => $input['id'], 'status' => 'accepted'];
                });

                return $result;
            } catch (UniqueConstraintViolationException) {
                $existing = DB::table('learning_events')->where('user_id', $user->id)->where('event_id', $id)->first();

                return ['id' => $id, 'status' => $existing && $existing->kind === $kind && $existing->payload_hash === ($digest ?? null) ? 'duplicate' : 'conflict'];
            } catch (\InvalidArgumentException $e) {
                return ['id' => $id, 'status' => 'rejected', 'message' => $e->getMessage()];
            } catch (HttpExceptionInterface) {
                return ['id' => $id, 'status' => 'rejected', 'message' => 'Conteúdo indisponível para esta conta.'];
            }
        }, $items);
    }

    private function indexContext(array &$index, array $event, array $modalities): void
    {
        foreach ($modalities as $modality) {
            foreach ($event['target_ids'] as $target) {
                $first = $index[$event['package_hash']][$modality][$target][$event['device_id']] ?? PHP_INT_MAX;
                $index[$event['package_hash']][$modality][$target][$event['device_id']] = min($first, $event['local_sequence']);
            }
        }
    }

    private function hasContext(array $index, array $attempt): bool
    {
        foreach ($attempt['target_ids'] as $target) {
            foreach ($index[$attempt['package_hash']][$attempt['modality']][$target] ?? [] as $device => $sequence) {
                // Only a sequence on the same device proves that context came later.
                if ($device !== $attempt['device_id'] || $sequence <= $attempt['local_sequence']) {
                    return true;
                }
            }
        }

        return false;
    }

    public function session(User $user, string $id): array
    {
        $events = DB::table('learning_events')->where('user_id', $user->id)->where('session_id', $id)->orderBy('local_sequence')->get();
        $inputs = [];
        $known = [];
        $supports = [];
        $technical = [];
        foreach ($events as $row) {
            $input = $inputs[$row->event_id] = json_decode($row->payload, true);
            if ($row->kind === 'exposure') {
                $known[$row->event_id] = true;
                if ($input['kind'] !== 'training_use') {
                    $this->indexContext($supports, $input, $input['affected_modalities']);
                }
            } elseif ($input['technical_status'] !== 'valid') {
                $this->indexContext($technical, $input, [$input['modality']]);
            }
        }
        // A page reload starts a new client session, but does not erase prior support.
        // Keep package/account boundaries and server order; later unrelated exposures
        // must not rewrite an earlier attempt as supported.
        $priorSupports = [];
        $externalSupports = DB::table('learning_events')->where('user_id', $user->id)
            ->where('kind', 'exposure')->where('session_id', '!=', $id)
            ->whereIn('package_hash', $events->pluck('package_hash')->unique()->all())
            ->orderBy('id')->get(['id', 'payload']);
        foreach ($externalSupports as $support) {
            $payload = json_decode($support->payload, true);
            if ($payload['kind'] === 'training_use') {
                continue;
            }
            foreach ($payload['affected_modalities'] as $modality) {
                foreach ($payload['target_ids'] as $target) {
                    $priorSupports[$payload['package_hash']][$modality][$target] ??= $support->id;
                }
            }
        }
        $activities = [];
        $hashes = $events->where('kind', 'attempt')->pluck('package_hash')->unique()->all();
        if ($hashes !== []) {
            foreach (DB::table('content_snapshots')->whereIn('hash', $hashes)->get(['hash', 'document']) as $snapshot) {
                $activities[$snapshot->hash] = array_column(json_decode($snapshot->document, true)['activities'], null, 'id');
            }
        }

        return ['session_id' => $id, 'events' => $events->map(function ($row) use ($inputs, $known, $supports, $technical, $activities, $priorSupports) {
            $input = $inputs[$row->event_id];
            $assessment = null;
            if ($row->kind === 'attempt') {
                $activity = $activities[$row->package_hash][$input['activity_id']];
                $supportPending = count(array_diff_key(array_fill_keys($input['support_event_ids'], true), $known)) > 0;
                $supported = $this->hasContext($supports, $input) || $supportPending;
                foreach ($input['target_ids'] as $target) {
                    if (($priorSupports[$input['package_hash']][$input['modality']][$target] ?? PHP_INT_MAX) < $row->id) {
                        $supported = true;
                    }
                }
                $comparison = TextAnswers::compare($activity, $input['response']);
                if ($input['technical_status'] !== 'valid' || $this->hasContext($technical, $input)) {
                    $comparison = ['outcome' => 'technical_issue', 'message' => 'Falha técnica registrada; nenhuma conclusão linguística.', 'example' => null, 'rule_version' => null];
                }
                $assessment = array_merge($comparison, ['supported' => $supported, 'support_pending' => $supportPending, 'is_preview' => (bool) $row->is_preview, 'retention_confirmed' => false, 'source' => $comparison['outcome'] === 'unverified' ? 'not_assessed' : 'automatic_rule']);
            }

            return ['id' => $row->event_id, 'kind' => $row->kind, 'assessment' => $assessment];
        })->values()->all()];
    }
}
