<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class PracticeFlowTest extends TestCase
{
    use RefreshDatabase;

    private string $directory;

    private array $package;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutVite();
        $this->directory = storage_path('framework/testing/'.Str::uuid());
        File::copyDirectory(dirname(base_path(), 2).'/content/pt-BR_en/missions', $this->directory);
        config(['learning.content_path' => $this->directory]);
        $this->package = json_decode(file_get_contents($this->directory.'/order-a-drink.v1.json'), true);
    }

    protected function tearDown(): void
    {
        File::deleteDirectory($this->directory);
        parent::tearDown();
    }

    private function editor(): User
    {
        $user = User::factory()->create(['is_editor' => true]);
        Sanctum::actingAs($user, ['catalog:read', 'practice:write']);

        return $user;
    }

    private function manifest(): array
    {
        return $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=1&preview=1')->assertOk()->json();
    }

    private function event(string $activity, string $hash, int $sequence = 1): array
    {
        $item = collect($this->package['activities'])->firstWhere('id', $activity);

        return ['schema_version' => '1.0.0', 'id' => (string) Str::uuid(), 'session_id' => '00000000-0000-4000-8000-000000000002', 'device_id' => '00000000-0000-4000-8000-000000000003', 'local_sequence' => $sequence, 'occurred_at' => now()->toIso8601String(), 'time_quality' => 'uncertain', 'activity_id' => $activity, 'package_id' => $this->package['id'], 'package_version' => 1, 'package_hash' => $hash, 'target_ids' => $item['target_ids']];
    }

    private function attempt(string $activity, string $hash, int $sequence = 1): array
    {
        $item = collect($this->package['activities'])->firstWhere('id', $activity);

        return [...$this->event($activity, $hash, $sequence), 'modality' => $item['modality'], 'support_event_ids' => [], 'technical_status' => 'valid', 'response' => 'Levar', 'audio_reference' => null, 'assessment_status' => 'pending'];
    }

    private function exposure(string $hash, int $sequence): array
    {
        return [...$this->event('dialogue', $hash, $sequence), 'support_id' => 'dialogue-transcript', 'kind' => 'transcript', 'affected_modalities' => ['listening'], 'reveals_answer' => true];
    }

    public function test_drafts_require_editor_and_manifest_pins_raw_document_and_audio(): void
    {
        Sanctum::actingAs(User::factory()->create(), ['catalog:read']);
        $this->getJson('/api/v1/editorial/catalog')->assertForbidden();
        $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=1&preview=1')->assertNotFound();
        $this->editor();
        $manifest = $this->manifest();
        $this->assertTrue($manifest['is_preview']);
        $raw = $this->get('/api/v1/content-snapshots/'.$manifest['package_hash'])->assertOk()->getContent();
        $this->assertSame($manifest['package_hash'], hash('sha256', $raw));
        $this->assertSame(strlen($raw), $manifest['package_bytes']);
        $asset = $manifest['assets'][0];
        $response = $this->get('/api/v1/content-snapshots/'.$manifest['package_hash'].'/assets/'.$asset['id'])->assertOk();
        $this->assertSame($asset['sha256'], hash_file('sha256', $response->baseResponse->getFile()->getPathname()));
        $this->getJson('/api/v1/catalog')->assertJsonCount(0, 'data');
    }

    public function test_replays_have_one_effect_and_changed_payload_conflicts(): void
    {
        $user = $this->editor();
        $hash = $this->manifest()['package_hash'];
        $event = $this->attempt('meaning', $hash);
        $this->postJson('/api/v1/attempts/batch', ['events' => [$event]])->assertJsonPath('results.0.status', 'accepted');
        for ($i = 0; $i < 10; $i++) {
            $this->postJson('/api/v1/attempts/batch', ['events' => [$event]])->assertJsonPath('results.0.status', 'duplicate');
        }
        $event['response'] = 'No local';
        $this->postJson('/api/v1/attempts/batch', ['events' => [$event]])->assertJsonPath('results.0.status', 'conflict');
        $this->assertSame(1, DB::table('learning_events')->where('user_id', $user->id)->count());
    }

    public function test_late_support_arrival_is_not_independent_success_and_future_support_does_not_rewrite_past(): void
    {
        $this->editor();
        $hash = $this->manifest()['package_hash'];
        $exposure = $this->exposure($hash, 1);
        $event = $this->attempt('meaning', $hash, 2);
        $event['support_event_ids'] = [$exposure['id']];
        $this->postJson('/api/v1/attempts/batch', ['events' => [$event]])->assertJsonPath('results.0.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$event['session_id'])->assertJsonPath('events.0.assessment.support_pending', true)->assertJsonPath('events.0.assessment.retention_confirmed', false);
        $this->postJson('/api/v1/exposures/batch', ['events' => [$exposure]])->assertJsonPath('results.0.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$event['session_id'])->assertJsonPath('events.1.assessment.supported', true)->assertJsonPath('events.1.assessment.support_pending', false);
        $new = $this->attempt('meaning', $hash, 3);
        $new['session_id'] = (string) Str::uuid();
        $this->postJson('/api/v1/attempts/batch', ['events' => [$new]])->assertJsonPath('results.0.status', 'accepted');
        $future = $this->exposure($hash, 4);
        $future['session_id'] = $new['session_id'];
        $this->postJson('/api/v1/exposures/batch', ['events' => [$future]])->assertJsonPath('results.0.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$new['session_id'])->assertJsonPath('events.0.assessment.supported', false);
    }

    public function test_open_alternatives_and_technical_failures_are_not_graded_as_language_errors(): void
    {
        $this->editor();
        $hash = $this->manifest()['package_hash'];
        $open = $this->attempt('recall', $hash);
        $open['response'] = 'Could I have a large tea, please?';
        $technical = $this->attempt('speak', $hash, 2);
        $technical['technical_status'] = 'failed';
        $technical['response'] = null;
        $this->postJson('/api/v1/attempts/batch', ['events' => [$open, $technical]])->assertJsonPath('results.1.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$open['session_id'])->assertJsonPath('events.0.assessment.outcome', 'unverified')->assertJsonPath('events.1.assessment.outcome', 'technical_issue')->assertJsonPath('events.0.assessment.is_preview', true);
        $failedListening = $this->attempt('dialogue', $hash, 3);
        $failedListening['technical_status'] = 'failed';
        $failedListening['response'] = null;
        $following = $this->attempt('meaning', $hash, 4);
        $this->postJson('/api/v1/attempts/batch', ['events' => [$failedListening, $following]])->assertJsonPath('results.1.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$open['session_id'])->assertJsonPath('events.3.assessment.outcome', 'technical_issue');
        Sanctum::actingAs(User::factory()->create(), ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/learning-sessions/'.$open['session_id'])->assertJsonCount(0, 'events');
    }

    public function test_batch_has_per_item_results_and_original_snapshot_survives_a_draft_edit(): void
    {
        $this->editor();
        $hash = $this->manifest()['package_hash'];
        $valid = $this->attempt('meaning', $hash);
        $bad = $this->attempt('meaning', $hash, 2);
        $bad['score'] = 100;
        $this->postJson('/api/v1/attempts/batch', ['events' => [$bad, $valid]])->assertJsonPath('results.0.status', 'rejected')->assertJsonPath('results.1.status', 'accepted');
        $raw = json_decode(file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v1.json'));
        $raw->title = 'Rascunho modificado';
        File::put($this->directory.'/order-a-drink.v1.json', json_encode($raw));
        $new = $this->manifest()['package_hash'];
        $this->assertNotSame($hash, $new);
        $this->get('/api/v1/content-snapshots/'.$hash)->assertOk();
        $valid['id'] = (string) Str::uuid();
        $valid['local_sequence'] = 3;
        $this->postJson('/api/v1/attempts/batch', ['events' => [$valid]])->assertJsonPath('results.0.status', 'accepted');
    }
}
