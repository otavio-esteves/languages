<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\DayProgress;
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

        return ['schema_version' => '1.0.0', 'id' => (string) Str::uuid(), 'session_id' => '00000000-0000-4000-8000-000000000002', 'device_id' => '00000000-0000-4000-8000-000000000003', 'local_sequence' => $sequence, 'occurred_at' => now()->toIso8601String(), 'time_quality' => 'uncertain', 'activity_id' => $activity, 'package_id' => $this->package['id'], 'package_version' => $this->package['version'], 'package_hash' => $hash, 'target_ids' => $item['target_ids']];
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

    public function test_text_trail_has_no_media_and_keeps_reading_and_writing_evidence_separate(): void
    {
        $this->editor();
        $this->getJson('/api/v1/editorial/catalog')->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.version', 4);
        $manifest = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=2&preview=1')->assertOk()->assertJsonCount(0, 'assets')->json();
        $this->package = json_decode(file_get_contents($this->directory.'/order-a-drink.v2.json'), true);
        $this->assertEquals(['reading', 'writing'], array_values(array_unique(array_column($this->package['activities'], 'modality'))));
        $this->assertCount(3, array_unique(array_column(array_column($this->package['activities'], 'content'), 'phase_id')));
        $hash = $manifest['package_hash'];
        $correct = $this->attempt('meaning', $hash);
        $wrong = $this->attempt('meaning', $hash, 2);
        $wrong['response'] = 'No local';
        $open = $this->attempt('recall', $hash, 3);
        $open['response'] = 'Could I have a large tea, please?';
        $transfer = $this->attempt('transfer', $hash, 4);
        $transfer['response'] = 'A small water for here, please. Could you explain that?';
        $mismatch = $this->attempt('meaning', $hash, 5);
        $mismatch['modality'] = 'listening';
        $this->postJson('/api/v1/attempts/batch', ['events' => [$correct, $wrong, $open, $transfer, $mismatch]])
            ->assertJsonPath('results.0.status', 'accepted')->assertJsonPath('results.3.status', 'accepted')->assertJsonPath('results.4.status', 'rejected');
        $this->getJson('/api/v1/learning-sessions/'.$correct['session_id'])
            ->assertJsonPath('events.0.assessment.outcome', 'correct')
            ->assertJsonPath('events.0.assessment.retention_confirmed', false)
            ->assertJsonPath('events.1.assessment.message', 'Leia novamente e observe o sentido da mensagem.')
            ->assertJsonPath('events.2.assessment.outcome', 'unverified')
            ->assertJsonPath('events.3.assessment.outcome', 'unverified');
        $this->getJson('/api/v1/catalog')->assertJsonCount(0, 'data');
    }

    public function test_automatic_writing_comparison_keeps_glossary_and_later_examples_as_support(): void
    {
        $this->editor();
        $manifest = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=3&preview=1')->assertOk()->assertJsonCount(0, 'assets')->json();
        $this->package = json_decode(file_get_contents($this->directory.'/order-a-drink.v3.json'), true);
        $hash = $manifest['package_hash'];
        $first = $this->attempt('write-water', $hash);
        $first['response'] = 'coffee';
        $first['support_event_ids'] = [];
        $this->postJson('/api/v1/attempts/batch', ['events' => [$first]])->assertJsonPath('results.0.status', 'accepted');
        $example = array_merge($this->event('write-water', $hash, 2), ['support_id' => 'comparison-example', 'kind' => 'solution', 'affected_modalities' => ['writing'], 'reveals_answer' => true]);
        $gloss = array_merge($this->event('write-water', $hash, 3), ['support_id' => 'gloss-water', 'kind' => 'hint', 'affected_modalities' => ['writing'], 'reveals_answer' => true]);
        $this->postJson('/api/v1/exposures/batch', ['events' => [$example, $gloss]])->assertJsonPath('results.1.status', 'accepted');
        $retry = $this->attempt('write-water', $hash, 4);
        $retry['response'] = ' WATER! ';
        $retry['support_event_ids'] = [$example['id'], $gloss['id']];
        $unknown = $this->attempt('transfer', $hash, 5);
        $unknown['response'] = 'May I please get one large coffee?';
        $failed = $this->attempt('contract', $hash, 6);
        $failed['technical_status'] = 'failed';
        $failed['response'] = null;
        $this->postJson('/api/v1/attempts/batch', ['events' => [$retry, $unknown, $failed]])->assertJsonPath('results.2.status', 'accepted');
        $this->getJson('/api/v1/learning-sessions/'.$first['session_id'])
            ->assertJsonPath('events.0.assessment.outcome', 'incorrect')
            ->assertJsonPath('events.0.assessment.supported', false)
            ->assertJsonPath('events.0.assessment.example', 'water')
            ->assertJsonPath('events.3.assessment.outcome', 'correct')
            ->assertJsonPath('events.3.assessment.supported', true)
            ->assertJsonPath('events.3.assessment.rule_version', 'text-variants-v1')
            ->assertJsonPath('events.3.assessment.retention_confirmed', false)
            ->assertJsonPath('events.4.assessment.outcome', 'unverified')
            ->assertJsonPath('events.5.assessment.outcome', 'technical_issue');
    }

    public function test_current_day_is_saved_per_account_and_cannot_be_rewound(): void
    {
        $first = $this->editor();
        $manifest = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=3&preview=1')->assertOk()->json();
        $progress = app(DayProgress::class);
        $this->assertSame(['cursor' => 0, 'day' => 0, 'completed' => false, 'completed_lessons' => []], $progress->get($first, $manifest['package_hash']));
        $this->actingAs($first)->postJson('/editorial/day-progress', ['package_hash' => $manifest['package_hash'], 'day' => 1, 'completed' => false])->assertOk()->assertJsonPath('day', 1);
        $this->postJson('/editorial/day-progress', ['package_hash' => $manifest['package_hash'], 'day' => 0, 'completed' => false])->assertJsonPath('day', 1);
        $this->postJson('/editorial/day-progress', ['package_hash' => $manifest['package_hash'], 'day' => 99, 'completed' => false])->assertUnprocessable();
        $this->postJson('/editorial/day-progress', ['package_hash' => $manifest['package_hash'], 'day' => 1, 'completed' => true])->assertUnprocessable();
        $second = $this->editor();
        $this->assertSame(['cursor' => 0, 'day' => 0, 'completed' => false, 'completed_lessons' => []], $progress->get($second, $manifest['package_hash']));
        $this->assertSame(['cursor' => 3, 'day' => 1, 'completed' => false, 'completed_lessons' => ['words:0', 'words:1', 'words:2']], $progress->get($first, $manifest['package_hash']));
        $this->actingAs($first)->postJson('/editorial/day-progress', ['package_hash' => $manifest['package_hash'], 'day' => 1, 'cursor' => 5, 'completed' => false])->assertOk()->assertJsonPath('cursor', 5);
        $this->assertSame(5, $progress->get($first, $manifest['package_hash'])['cursor']);
    }

    public function test_browsing_future_days_does_not_complete_skipped_lessons_and_each_day_keeps_its_progress(): void
    {
        $first = $this->editor();
        $manifest = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=4&preview=1')->assertOk()->json();
        $hash = $manifest['package_hash'];
        $save = fn ($day, $cursor, $lesson) => $this->postJson('/editorial/day-progress', ['package_hash' => $hash, 'day' => $day, 'cursor' => $cursor, 'completed' => false, 'lesson' => $lesson]);
        $this->actingAs($first);
        $save(2, 9, 'sentences:0')->assertOk()->assertJsonPath('completed_lessons', ['sentences:0'])->assertJsonPath('completed', false);
        $save(0, 1, 'words:0')->assertOk()->assertJsonPath('completed_lessons', ['sentences:0', 'words:0']);
        $save(0, 1, 'words:0')->assertOk()->assertJsonCount(2, 'completed_lessons');
        $save(0, 1, 'unknown:0')->assertUnprocessable();
        $second = $this->editor();
        $this->assertSame([], app(DayProgress::class)->get($second, $hash)['completed_lessons']);
        $this->actingAs($first);
        foreach ([[0, 2, 'words:1'], [0, 2, 'words:2'], [1, 4, 'requests:0'], [1, 6, 'requests:1'], [1, 6, 'requests:2'], [2, 11, 'sentences:1']] as [$day, $cursor, $lesson]) {
            $save($day, $cursor, $lesson)->assertOk()->assertJsonPath('completed', false);
        }
        $save(2, 11, 'sentences:2')->assertOk()->assertJsonCount(9, 'completed_lessons')->assertJsonPath('completed', true);
        $this->assertCount(9, app(DayProgress::class)->get($first, $hash)['completed_lessons']);
        $this->assertSame(0, DB::table('learning_events')->where('user_id', $first->id)->count());
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

    public function test_saved_practice_remains_the_default_after_a_metadata_update_and_uses_its_original_snapshot(): void
    {
        $user = $this->editor();
        $old = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=3&preview=1')->assertOk()->json();
        $new = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=4&preview=1')->assertOk()->json();
        app(DayProgress::class)->save($user, $old['package_hash'], 0, false, 1, 'words:0');
        $page = $this->actingAs($user)->get('/editorial')->assertOk()->viewData('page');
        $this->assertSame(3, $page['props']['packages'][0]['version']);
        $this->assertSame(['words:0'], $page['props']['packages'][0]['progress']['completed_lessons']);
        $this->assertSame(4, $page['props']['currentPackages'][0]['version']);
        $this->assertSame([], $page['props']['currentPackages'][0]['progress']['completed_lessons']);
        $this->assertSame($old['package_hash'], $page['props']['savedPackages'][0]['package_hash']);
        File::delete($this->directory.'/order-a-drink.v3.json');
        $resumed = $this->get('/editorial/practice/order-a-drink/3?snapshot='.$old['package_hash'])->assertOk()->viewData('page');
        $this->assertSame(3, $resumed['props']['mission']->version);
        $this->assertSame($old['package_hash'], $resumed['props']['manifest']['package_hash']);
        $this->assertSame(['words:0'], $resumed['props']['progress']['completed_lessons']);
        $this->get('/editorial/practice/order-a-drink/4?snapshot='.$old['package_hash'])->assertNotFound();
        $this->assertSame(0, DB::table('learning_events')->where('user_id', $user->id)->count());
        $other = $this->editor();
        $otherPage = $this->actingAs($other)->get('/editorial')->assertOk()->viewData('page');
        $this->assertSame(4, $otherPage['props']['packages'][0]['version']);
        $this->assertSame([], $otherPage['props']['savedPackages']);
        $this->assertSame([], app(DayProgress::class)->get($other, $new['package_hash'])['completed_lessons']);
    }

    public function test_completed_previous_practice_is_visible_and_a_newer_started_practice_can_be_resumed(): void
    {
        $user = $this->editor();
        $old = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=3&preview=1')->assertOk()->json();
        $new = $this->getJson('/api/v1/content-packages/order-a-drink/manifest?version=4&preview=1')->assertOk()->json();
        $progress = app(DayProgress::class);
        $progress->save($user, $old['package_hash'], 2, true, 11);
        $page = $this->actingAs($user)->get('/editorial')->assertOk()->viewData('page');
        $this->assertSame(3, $page['props']['packages'][0]['version']);
        $this->assertTrue($page['props']['packages'][0]['progress']['completed']);
        $progress->save($user, $new['package_hash'], 0, false, 1, 'words:0');
        $page = $this->get('/editorial')->assertOk()->viewData('page');
        $this->assertSame(4, $page['props']['packages'][0]['version']);
        $this->assertCount(2, $page['props']['savedPackages']);
        $this->assertCount(9, $progress->get($user, $old['package_hash'])['completed_lessons']);
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
