<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class SessionResultsTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private string $hash;

    private string $session;

    private string $device;

    private array $package;

    protected function setUp(): void
    {
        parent::setUp();
        $this->user = User::factory()->create(['is_editor' => true]);
        Sanctum::actingAs($this->user, ['catalog:read', 'practice:write']);
        $raw = file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v5.json');
        $this->hash = hash('sha256', $raw);
        $this->package = json_decode($raw, true);
        $this->session = (string) Str::uuid();
        $this->device = (string) Str::uuid();
        DB::table('content_snapshots')->insert(['hash' => $this->hash, 'package_id' => 'order-a-drink', 'version' => 5, 'is_preview' => true, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
    }

    private function attempt(array $activity, int $sequence, array $changes = []): array
    {
        return array_replace(['schema_version' => '1.0.0', 'id' => (string) Str::uuid(), 'session_id' => $this->session, 'device_id' => $this->device, 'local_sequence' => $sequence, 'occurred_at' => now()->toIso8601String(), 'time_quality' => 'uncertain', 'activity_id' => $activity['id'], 'package_id' => 'order-a-drink', 'package_version' => 5, 'package_hash' => $this->hash, 'target_ids' => $activity['target_ids'], 'modality' => $activity['modality'], 'support_event_ids' => [], 'technical_status' => 'valid', 'response' => $activity['expected']['comparison']['example'] ?? $activity['expected']['accepted'][0], 'audio_reference' => null, 'assessment_status' => 'pending'], $changes);
    }

    private function row(array $payload, string $kind = 'attempt'): array
    {
        return ['user_id' => $this->user->id, 'event_id' => $payload['id'], 'session_id' => $payload['session_id'], 'device_id' => $payload['device_id'], 'local_sequence' => $payload['local_sequence'], 'kind' => $kind, 'package_hash' => $payload['package_hash'], 'payload_hash' => hash('sha256', json_encode($payload)), 'is_preview' => true, 'payload' => json_encode($payload), 'created_at' => now(), 'updated_at' => now()];
    }

    public function test_long_session_loads_snapshots_in_one_query_and_keeps_every_result(): void
    {
        $activities = array_values(array_filter($this->package['activities'], fn ($a) => $a['modality'] === 'writing'));
        $rows = array_map(fn ($activity, $index) => $this->row($this->attempt($activity, $index + 1)), $activities, array_keys($activities));
        foreach (array_chunk($rows, 100) as $chunk) {
            DB::table('learning_events')->insert($chunk);
        }
        DB::enableQueryLog();
        DB::flushQueryLog();
        try {
            $response = $this->getJson('/api/v1/learning-sessions/'.$this->session)->assertOk()->assertJsonCount(636, 'events');
            $queries = DB::getQueryLog();
        } finally {
            DB::disableQueryLog();
            DB::flushQueryLog();
        }
        $this->assertCount(2, $queries, 'One events query and one snapshot query, regardless of attempt count.');
        $this->assertSame(array_column($rows, 'event_id'), array_column($response->json('events'), 'id'));
        foreach ($response->json('events') as $event) {
            $this->assertSame('correct', $event['assessment']['outcome']);
            $this->assertFalse($event['assessment']['supported']);
            $this->assertFalse($event['assessment']['retention_confirmed']);
        }
    }

    public function test_context_keeps_sequence_target_modality_and_missing_support_semantics(): void
    {
        $write = $this->package['activities'][3];
        $other = $this->package['activities'][4];
        $read = $this->package['activities'][1];
        $first = $this->attempt($write, 1);
        $support = array_intersect_key($this->attempt($write, 2), array_flip(['schema_version', 'id', 'session_id', 'device_id', 'local_sequence', 'occurred_at', 'time_quality', 'activity_id', 'package_id', 'package_version', 'package_hash', 'target_ids']));
        $support += ['support_id' => 'comparison-example', 'kind' => 'solution', 'affected_modalities' => ['writing'], 'reveals_answer' => true];
        $retry = $this->attempt($write, 3, ['support_event_ids' => [$support['id']]]);
        $unrelated = $this->attempt($other, 4);
        $failed = $this->attempt($write, 5, ['technical_status' => 'failed', 'response' => null]);
        $afterFailure = $this->attempt($write, 6);
        $reading = $this->attempt($read, 7);
        $missing = $this->attempt($other, 8, ['support_event_ids' => [(string) Str::uuid()]]);
        $laterSupport = array_replace($support, ['id' => (string) Str::uuid(), 'local_sequence' => 9]);
        DB::table('learning_events')->insert([$this->row($first), $this->row($support, 'exposure'), $this->row($retry), $this->row($unrelated), $this->row($failed), $this->row($afterFailure), $this->row($reading), $this->row($missing), $this->row($laterSupport, 'exposure')]);
        $this->getJson('/api/v1/learning-sessions/'.$this->session)->assertOk()
            ->assertJsonPath('events.0.assessment.supported', false)
            ->assertJsonPath('events.0.assessment.outcome', 'correct')
            ->assertJsonPath('events.2.assessment.supported', true)
            ->assertJsonPath('events.2.assessment.support_pending', false)
            ->assertJsonPath('events.2.assessment.outcome', 'correct')
            ->assertJsonPath('events.3.assessment.supported', false)
            ->assertJsonPath('events.3.assessment.outcome', 'correct')
            ->assertJsonPath('events.4.assessment.outcome', 'technical_issue')
            ->assertJsonPath('events.5.assessment.outcome', 'technical_issue')
            ->assertJsonPath('events.6.assessment.outcome', 'correct')
            ->assertJsonPath('events.6.assessment.supported', false)
            ->assertJsonPath('events.7.assessment.support_pending', true)
            ->assertJsonPath('events.7.assessment.supported', true);
        $remoteDevice = (string) Str::uuid();
        $remoteSupport = array_replace($support, ['id' => (string) Str::uuid(), 'device_id' => $remoteDevice, 'local_sequence' => 100]);
        DB::table('learning_events')->insert($this->row($remoteSupport, 'exposure'));
        $this->getJson('/api/v1/learning-sessions/'.$this->session)->assertOk()->assertJsonPath('events.0.assessment.supported', true);
        $remoteFailure = array_replace($failed, ['id' => (string) Str::uuid(), 'device_id' => $remoteDevice, 'local_sequence' => 101]);
        DB::table('learning_events')->insert($this->row($remoteFailure));
        $this->getJson('/api/v1/learning-sessions/'.$this->session)->assertOk()->assertJsonPath('events.0.assessment.outcome', 'technical_issue')->assertJsonPath('events.3.assessment.outcome', 'correct');
    }

    public function test_each_attempt_uses_its_original_snapshot_even_when_activity_ids_match(): void
    {
        $activity = $this->package['activities'][3];
        $response = 'A synthetic alternative';
        $updated = $this->package;
        $updated['activities'][3]['expected']['comparison']['accepted'][] = $response;
        $raw = json_encode($updated);
        $hash = hash('sha256', $raw);
        DB::table('content_snapshots')->insert(['hash' => $hash, 'package_id' => 'order-a-drink', 'version' => 5, 'is_preview' => true, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
        $before = $this->attempt($activity, 1, ['response' => $response]);
        $after = $this->attempt($activity, 2, ['response' => $response, 'package_hash' => $hash]);
        DB::table('learning_events')->insert([$this->row($before), $this->row($after)]);
        $this->getJson('/api/v1/learning-sessions/'.$this->session)->assertOk()
            ->assertJsonPath('events.0.assessment.outcome', 'unverified')
            ->assertJsonPath('events.1.assessment.outcome', 'correct');
    }
}
