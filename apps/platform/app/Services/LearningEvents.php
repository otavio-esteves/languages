<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Facades\DB;
use Opis\JsonSchema\Validator;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

class LearningEvents
{
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

    public function session(User $user, string $id): array
    {
        $events = DB::table('learning_events')->where('user_id', $user->id)->where('session_id', $id)->orderBy('local_sequence')->get();

        return ['session_id' => $id, 'events' => $events->map(function ($row) use ($events) {
            $input = json_decode($row->payload, true);
            $assessment = null;
            if ($row->kind === 'attempt') {
                $snapshot = DB::table('content_snapshots')->where('hash', $row->package_hash)->first();
                $package = json_decode($snapshot->document, true);
                $activity = collect($package['activities'])->firstWhere('id', $input['activity_id']);
                $supported = $events->contains(function ($e) use ($input) {
                    if ($e->kind !== 'exposure') {
                        return false;
                    }
                    $p = json_decode($e->payload, true);
                    if ($e->device_id === $input['device_id'] && $p['local_sequence'] > $input['local_sequence']) {
                        return false;
                    }

                    return $p['kind'] !== 'training_use' && in_array($input['modality'], $p['affected_modalities'], true) && count(array_intersect($input['target_ids'], $p['target_ids'])) > 0;
                });
                $known = $events->where('kind', 'exposure')->pluck('event_id')->all();
                $supportPending = count(array_diff($input['support_event_ids'], $known)) > 0;
                $supported = $supported || $supportPending;
                $technicalContext = $events->contains(function ($e) use ($input) {
                    if ($e->kind !== 'attempt') {
                        return false;
                    }
                    $p = json_decode($e->payload, true);
                    if ($e->device_id === $input['device_id'] && $p['local_sequence'] > $input['local_sequence']) {
                        return false;
                    }

                    return $p['technical_status'] !== 'valid' && $p['modality'] === $input['modality'] && count(array_intersect($p['target_ids'], $input['target_ids'])) > 0;
                });
                $comparison = TextAnswers::compare($activity, $input['response']);
                if ($input['technical_status'] !== 'valid' || $technicalContext) {
                    $comparison = ['outcome' => 'technical_issue', 'message' => 'Falha técnica registrada; nenhuma conclusão linguística.', 'example' => null, 'rule_version' => null];
                }
                $assessment = array_merge($comparison, ['supported' => $supported, 'support_pending' => $supportPending, 'is_preview' => (bool) $row->is_preview, 'retention_confirmed' => false, 'source' => $comparison['outcome'] === 'unverified' ? 'not_assessed' : 'automatic_rule']);
            }

            return ['id' => $row->event_id, 'kind' => $row->kind, 'assessment' => $assessment];
        })->values()->all()];
    }
}
