<?php

namespace Tests\Feature;

use App\Http\Middleware\HandleInertiaRequests;
use App\Models\User;
use App\Services\LearningEvents;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Opis\JsonSchema\Validator as SchemaValidator;
use Tests\TestCase;

class WritingReviewsTest extends TestCase
{
    use RefreshDatabase;

    private User $owner;

    private array $package;

    private string $hash;

    private array $attempt;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutVite();
        $this->withHeader('X-Inertia-Version', (string) app(HandleInertiaRequests::class)->version(Request::create('/')));
        $this->owner = User::factory()->create(['is_editor' => true]);
        $raw = file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v5.json');
        $this->package = json_decode($raw, true);
        $this->hash = hash('sha256', $raw);
        DB::table('content_snapshots')->insert(['hash' => $this->hash, 'package_id' => $this->package['id'], 'version' => 5, 'is_preview' => true, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
        $activity = $this->package['activities'][3];
        $this->attempt = [
            'schema_version' => '1.0.0', 'id' => (string) Str::uuid(), 'session_id' => (string) Str::uuid(), 'device_id' => (string) Str::uuid(),
            'local_sequence' => 2, 'occurred_at' => now()->toIso8601String(), 'time_quality' => 'uncertain',
            'activity_id' => $activity['id'], 'package_id' => $this->package['id'], 'package_version' => 5,
            'package_hash' => $this->hash, 'target_ids' => $activity['target_ids'], 'modality' => 'writing',
            'support_event_ids' => [], 'technical_status' => 'valid', 'response' => 'Synthetic unlisted written response.',
            'audio_reference' => null, 'assessment_status' => 'pending',
        ];
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$this->attempt])[0]['status']);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
    }

    private function requestReview(): array
    {
        $review = $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertOk()->json();
        $this->assertReviewSchema($review);

        return $review;
    }

    private function assertReviewSchema(array $review): void
    {
        $schema = json_decode(file_get_contents(dirname(base_path(), 2).'/contracts/writing-review.schema.json'));
        $this->assertTrue((new SchemaValidator)->validate(json_decode(json_encode($review)), $schema)->isValid(), 'O resumo deve corresponder ao contrato versionado de revisão escrita.');
    }

    private function reviewer(bool $editor = true): User
    {
        return User::factory()->create(['is_editor' => $editor, 'is_reviewer' => true]);
    }

    private function decision(int $revision = 0): array
    {
        return ['decision_id' => (string) Str::uuid(), 'expected_revision' => $revision, 'feedback' => 'Comentário sintético sobre esta mensagem escrita.', 'dimensions' => [
            ['id' => 'meaning', 'result' => 'met', 'reason' => 'A mensagem comunica a informação solicitada.'],
            ['id' => 'reading', 'result' => 'not_observed', 'reason' => 'Esta revisão observa somente escrita.'],
            ['id' => 'form', 'result' => 'abstained', 'reason' => 'Forma-alvo ainda não confirmada.'],
        ]];
    }

    public function test_requests_are_explicit_idempotent_private_and_leave_automatic_results_unchanged(): void
    {
        $this->assertDatabaseCount('writing_review_requests', 0);
        $before = $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->json('events.0.assessment');
        $request = $this->requestReview();
        $this->assertSame('pending', $request['status']);
        $this->assertSame('not_assessed', $request['source']);
        $this->assertFalse($request['retention_confirmed']);
        $this->assertNull($request['feedback']);
        $this->assertSame($request['id'], $this->requestReview()['id']);
        $this->assertDatabaseCount('writing_review_requests', 1);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.0.assessment', $before)->assertJsonPath('events.0.human_review.id', $request['id']);
        $this->getJson('/api/v1/writing-reviews')->assertHeader('Cache-Control', 'no-store, private')->assertJsonPath('data.0.response', $this->attempt['response'])->assertJsonMissingPath('data.0.reviewer_id')->assertJsonMissingPath('data.0.user_id');
        $other = User::factory()->create(['is_editor' => true]);
        Sanctum::actingAs($other, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/writing-reviews')->assertJsonCount(0, 'data');
        $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertNotFound();
    }

    public function test_unauthorized_editors_cannot_review_and_roles_require_explicit_operator_action(): void
    {
        $request = $this->requestReview();
        $this->actingAs($this->owner, 'web');
        $this->get('/reviews')->assertForbidden();
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertForbidden();
        $this->assertFalse($this->owner->fresh()->is_reviewer);
        $this->artisan('app:reviewer', ['email' => $this->owner->email])->expectsConfirmation('Conceder permissão de avaliação escrita a esta conta?', 'no')->assertSuccessful();
        $this->assertFalse($this->owner->fresh()->is_reviewer);
        $this->artisan('app:reviewer', ['email' => $this->owner->email])->expectsConfirmation('Conceder permissão de avaliação escrita a esta conta?', 'yes')->assertSuccessful();
        $this->assertTrue($this->owner->fresh()->is_reviewer);
        $this->artisan('app:reviewer', ['email' => $this->owner->email, '--revoke' => true])->expectsConfirmation('Revogar permissão de avaliação escrita desta conta?', 'yes')->assertSuccessful();
        $this->assertFalse($this->owner->fresh()->is_reviewer);
        $count = User::count();
        $this->artisan('app:reviewer', ['email' => 'missing@example.test'])->assertFailed();
        $this->assertSame($count, User::count());
    }

    public function test_decisions_are_append_only_idempotent_and_do_not_regrade_or_promote_modalities(): void
    {
        $request = $this->requestReview();
        $before = $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->json('events.0.assessment');
        $reviewer = $this->reviewer();
        $this->actingAs($reviewer, 'web');
        $data = $this->decision();
        $url = '/reviews/'.$request['id'].'/decisions';
        $first = $this->postJson($url, $data)->assertOk()->assertJsonPath('revision', 1)->assertJsonPath('source', 'human_review')->assertJsonPath('retention_confirmed', false)->json();
        $this->assertReviewSchema($first);
        $this->postJson($url, $data)->assertOk()->assertJsonPath('revision', 1);
        $this->assertDatabaseCount('writing_review_decisions', 1);
        $changed = $data;
        $changed['feedback'] = 'Outro comentário.';
        $this->postJson($url, $changed)->assertConflict();
        $this->postJson($url, $this->decision())->assertConflict();
        $next = $this->decision(1);
        $next['dimensions'][0]['result'] = 'abstained';
        $this->postJson($url, $next)->assertOk()->assertJsonPath('revision', 2);
        $this->assertDatabaseCount('writing_review_decisions', 2);
        $this->assertDatabaseHas('writing_review_decisions', ['id' => $data['decision_id'], 'revision' => 1, 'feedback' => $first['feedback']]);
        $this->assertDatabaseHas('writing_review_decisions', ['id' => $next['decision_id'], 'revision' => 2, 'supersedes_id' => $data['decision_id'], 'reviewer_id' => $reviewer->id]);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.0.assessment', $before)->assertJsonPath('events.0.human_review.revision', 2)->assertJsonMissingPath('events.0.human_review.reviewer_id');
        $this->getJson('/api/v1/writing-reviews')->assertJsonPath('data.0.status', 'reviewed')->assertJsonMissingPath('data.0.history');
    }

    public function test_self_review_draft_permissions_and_dimensions_are_enforced(): void
    {
        $request = $this->requestReview();
        $this->owner->is_reviewer = true;
        $this->owner->save();
        $this->actingAs($this->owner, 'web');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertForbidden();
        $this->get('/reviews', ['X-Inertia' => 'true'])->assertJsonCount(0, 'props.reviews');
        $reviewer = $this->reviewer(false);
        $this->actingAs($reviewer, 'web');
        $this->get('/reviews', ['X-Inertia' => 'true'])->assertJsonCount(0, 'props.reviews');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertForbidden();
        $reviewer->is_editor = true;
        $reviewer->save();
        $data = $this->decision();
        $data['dimensions'][1]['result'] = 'met';
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $data = $this->decision();
        array_pop($data['dimensions']);
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $data = $this->decision();
        $data['dimensions'][2]['id'] = 'meaning';
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $data = $this->decision() + ['independent' => true];
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $data = $this->decision();
        $data['dimensions'][0]['reason'] = ' ';
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $data = $this->decision();
        $data['feedback'] = "\u{00A0}\t";
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertUnprocessable();
        $this->assertDatabaseCount('writing_review_decisions', 0);
    }

    public function test_queue_filters_pagination_snapshot_and_privacy_are_preserved(): void
    {
        $request = $this->requestReview();
        $reviewer = $this->reviewer();
        $this->actingAs($reviewer, 'web');
        $this->get('/reviews?status=pending&limit=1', ['X-Inertia' => 'true'])->assertOk()->assertHeader('Cache-Control', 'no-store, private')->assertJsonPath('encryptHistory', true)->assertJsonPath('props.reviews.0.package_hash', $this->hash)->assertJsonPath('props.reviews.0.rubric.dimensions.1.applicable', false)->assertJsonPath('props.reviews.0.automatic_assessment.outcome', 'unverified')->assertJsonPath('props.filters.status', 'pending')->assertJsonPath('props.pagination.current_page', 1)->assertJsonMissingPath('props.reviews.0.email')->assertJsonMissingPath('props.reviews.0.user_id');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertOk();
        $this->get('/reviews?status=pending', ['X-Inertia' => 'true'])->assertJsonCount(0, 'props.reviews');
        $this->get('/reviews?status=reviewed', ['X-Inertia' => 'true'])->assertJsonPath('props.reviews.0.history.0.reviewer_id', $reviewer->id)->assertJsonPath('props.reviews.0.history.0.revision', 1);
        $this->getJson('/reviews?limit=1000')->assertUnprocessable();
        $this->getJson('/reviews?status=other')->assertUnprocessable();
        $this->actingAs($this->owner, 'web');
        $this->get('/writing-feedback', ['X-Inertia' => 'true'])->assertOk()->assertHeader('Cache-Control', 'no-store, private')->assertJsonPath('encryptHistory', true)->assertJsonPath('props.reviews.0.attempt_id', $this->attempt['id'])->assertJsonMissingPath('props.reviews.0.reviewer_id');
    }

    public function test_ineligible_attempts_and_unsupported_rubrics_are_rejected_without_requests(): void
    {
        $event = DB::table('learning_events')->where('event_id', $this->attempt['id'])->first();
        foreach ([['modality' => 'reading'], ['technical_status' => 'failed'], ['response' => ''], ['response' => "\u{00A0}\n\t"], ['modality' => 'speaking']] as $change) {
            DB::table('learning_events')->where('id', $event->id)->update(['payload' => json_encode(array_replace($this->attempt, $change))]);
            $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertUnprocessable();
        }
        DB::table('learning_events')->where('id', $event->id)->update(['payload' => json_encode($this->attempt)]);
        $package = $this->package;
        $package['rubric']['version'] = 999;
        DB::table('content_snapshots')->where('hash', $this->hash)->update(['document' => json_encode($package)]);
        $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertUnprocessable();
        $this->assertDatabaseCount('writing_review_requests', 0);
    }

    public function test_snapshot_and_late_support_remain_separate_from_human_feedback(): void
    {
        $request = $this->requestReview();
        $package = $this->package;
        $package['version'] = 6;
        $package['activities'][3]['prompt'] = 'Outro enunciado sintético na versão posterior.';
        $raw = json_encode($package);
        DB::table('content_snapshots')->insert(['hash' => hash('sha256', $raw), 'package_id' => $package['id'], 'version' => 6, 'is_preview' => true, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
        $reviewer = $this->reviewer();
        $this->actingAs($reviewer, 'web');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertOk();
        $support = array_intersect_key($this->attempt, array_flip(['schema_version', 'session_id', 'device_id', 'occurred_at', 'time_quality', 'activity_id', 'package_id', 'package_version', 'package_hash', 'target_ids']));
        $support += ['id' => (string) Str::uuid(), 'local_sequence' => 1, 'support_id' => 'comparison-example', 'kind' => 'solution', 'affected_modalities' => ['writing'], 'reveals_answer' => true];
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'exposure', [$support])[0]['status']);
        $this->get('/reviews?status=reviewed', ['X-Inertia' => 'true'])->assertJsonPath('props.reviews.0.prompt', $this->package['activities'][3]['prompt'])->assertJsonPath('props.reviews.0.support_context.supported', true)->assertJsonPath('props.reviews.0.package_version', 5);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.1.assessment.supported', true)->assertJsonPath('events.1.assessment.retention_confirmed', false)->assertJsonPath('events.1.human_review.revision', 1)->assertJsonMissingPath('events.1.human_review.independent');
    }

    public function test_write_scope_and_preview_revocation_are_enforced(): void
    {
        Sanctum::actingAs($this->owner, ['catalog:read']);
        $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertForbidden();
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id'], 'retention_confirmed' => true])->assertUnprocessable();
        $this->requestReview();
        $this->owner->is_editor = false;
        $this->owner->save();
        $this->getJson('/api/v1/writing-reviews')->assertJsonCount(0, 'data');
        $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $this->attempt['id']])->assertNotFound();
    }

    public function test_supported_older_text_rubric_and_published_reviews_do_not_require_editor_role(): void
    {
        $raw = file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v3.json');
        $package = json_decode($raw, true);
        $hash = hash('sha256', $raw);
        DB::table('content_snapshots')->insert(['hash' => $hash, 'package_id' => $package['id'], 'version' => 3, 'is_preview' => false, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
        $activity = collect($package['activities'])->firstWhere('type', 'written_recall');
        $payload = array_replace($this->attempt, ['id' => (string) Str::uuid(), 'local_sequence' => 3, 'package_hash' => $hash, 'package_version' => 3, 'activity_id' => $activity['id'], 'target_ids' => $activity['target_ids']]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$payload])[0]['status']);
        $request = $this->postJson('/api/v1/writing-reviews', ['attempt_id' => $payload['id']])->assertOk()->assertJsonPath('rubric_version', 3)->json();
        $reviewer = $this->reviewer(false);
        $this->actingAs($reviewer, 'web');
        $this->get('/reviews', ['X-Inertia' => 'true'])->assertJsonCount(1, 'props.reviews')->assertJsonPath('props.reviews.0.rubric.dimensions.0.applicable', true);
        $data = $this->decision();
        $data['dimensions'][1] = ['id' => 'choice', 'result' => 'abstained', 'reason' => 'Não avaliado nesta oportunidade.'];
        $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertOk();
    }

    public function test_login_and_root_choose_a_landing_allowed_for_each_role(): void
    {
        $this->app['auth']->forgetGuards();
        $this->app['auth']->shouldUse('web');
        foreach ([['is_editor' => true, 'is_reviewer' => false, 'path' => '/editorial'], ['is_editor' => false, 'is_reviewer' => true, 'path' => '/reviews'], ['is_editor' => false, 'is_reviewer' => false, 'path' => '/writing-feedback']] as $case) {
            $user = User::factory()->create(['is_editor' => $case['is_editor'], 'is_reviewer' => $case['is_reviewer'], 'password' => 'synthetic-password-123']);
            $this->post('/login', ['email' => $user->email, 'password' => 'synthetic-password-123'])->assertRedirect($case['path']);
            $this->get('/')->assertRedirect($case['path']);
            $this->get($case['path'], ['X-Inertia' => 'true'])->assertOk()->assertJsonPath('encryptHistory', true)->assertJsonPath('clearHistory', true);
            $this->get($case['path'], ['X-Inertia' => 'true'])->assertOk()->assertJsonPath('encryptHistory', true)->assertJsonMissingPath('clearHistory');
            $this->post('/logout')->assertRedirect('/login');
            $this->get('/login', ['X-Inertia' => 'true'])->assertOk()->assertJsonPath('clearHistory', true)->assertJsonMissingPath('encryptHistory');
            $this->get('/login', ['X-Inertia' => 'true'])->assertOk()->assertJsonMissingPath('clearHistory')->assertJsonMissingPath('encryptHistory');
        }
    }

    public function test_login_keeps_authorized_intended_routes_and_discards_forbidden_ones(): void
    {
        $this->app['auth']->forgetGuards();
        $this->app['auth']->shouldUse('web');
        $reviewer = $this->reviewer();
        $reviewer->password = 'synthetic-password-123';
        $reviewer->save();
        $this->withSession(['url.intended' => url('/reviews?status=reviewed')])->post('/login', ['email' => $reviewer->email, 'password' => 'synthetic-password-123'])->assertRedirect('/reviews?status=reviewed');
        $this->post('/logout')->assertRedirect('/login');
        $owner = User::factory()->create(['password' => 'synthetic-password-123']);
        $this->withSession(['url.intended' => url('/editorial')])->post('/login', ['email' => $owner->email, 'password' => 'synthetic-password-123'])->assertRedirect('/writing-feedback');
        $this->post('/logout')->assertRedirect('/login');
        $this->withSession(['url.intended' => url('/reviews')])->post('/login', ['email' => $owner->email, 'password' => 'synthetic-password-123'])->assertRedirect('/writing-feedback');
    }

    public function test_delivered_feedback_is_conservative_context_only_for_later_matching_writing(): void
    {
        $request = $this->requestReview();
        $reviewer = $this->reviewer();
        $this->actingAs($reviewer, 'web');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision())->assertOk();
        $this->get('/reviews?status=reviewed', ['X-Inertia' => 'true'])->assertOk();
        $this->assertDatabaseCount('writing_review_deliveries', 0);
        $before = array_replace($this->attempt, ['id' => (string) Str::uuid(), 'local_sequence' => 3]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$before])[0]['status']);
        $result = app(LearningEvents::class)->session($this->owner, $this->attempt['session_id'], false);
        $this->assertFalse($result['events'][1]['assessment']['human_feedback_available']);
        $count = DB::table('learning_events')->count();
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/writing-reviews')->assertOk();
        $this->assertDatabaseCount('writing_review_deliveries', 1);
        $this->getJson('/api/v1/writing-reviews')->assertOk();
        $this->requestReview();
        $this->assertDatabaseCount('writing_review_deliveries', 1);
        $this->assertSame($count, DB::table('learning_events')->count());
        $later = array_replace($this->attempt, ['id' => (string) Str::uuid(), 'local_sequence' => 4]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$later])[0]['status']);
        $reading = $this->package['activities'][1];
        $read = array_replace($this->attempt, ['id' => (string) Str::uuid(), 'local_sequence' => 5, 'activity_id' => $reading['id'], 'modality' => 'reading', 'target_ids' => $reading['target_ids'], 'response' => $reading['content']['options'][0]]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$read])[0]['status']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.0.assessment.human_feedback_available', false)->assertJsonPath('events.1.assessment.supported', false)->assertJsonPath('events.2.assessment.human_feedback_available', true)->assertJsonPath('events.2.assessment.supported', true)->assertJsonPath('events.2.assessment.retention_confirmed', false)->assertJsonPath('events.3.assessment.human_feedback_available', false);
        $other = User::factory()->create(['is_editor' => true]);
        $foreign = array_replace($later, ['id' => (string) Str::uuid()]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($other, 'attempt', [$foreign])[0]['status']);
        $this->assertFalse(app(LearningEvents::class)->session($other, $foreign['session_id'])['events'][0]['assessment']['supported']);
        $package = $this->package;
        $package['version'] = 6;
        $raw = json_encode($package);
        $hash = hash('sha256', $raw);
        DB::table('content_snapshots')->insert(['hash' => $hash, 'package_id' => $package['id'], 'version' => 6, 'is_preview' => true, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);
        $newSnapshot = array_replace($later, ['id' => (string) Str::uuid(), 'local_sequence' => 6, 'package_hash' => $hash, 'package_version' => 6]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$newSnapshot])[0]['status']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.4.assessment.human_feedback_available', false);
        $different = collect($this->package['activities'])->first(fn ($activity) => $activity['type'] === 'written_recall' && array_intersect($activity['target_ids'], $this->attempt['target_ids']) === []);
        $otherTarget = array_replace($later, ['id' => (string) Str::uuid(), 'local_sequence' => 7, 'activity_id' => $different['id'], 'target_ids' => $different['target_ids']]);
        $this->assertSame('accepted', app(LearningEvents::class)->receive($this->owner, 'attempt', [$otherTarget])[0]['status']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.5.assessment.human_feedback_available', false);
        $this->actingAs($reviewer, 'web');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision(1))->assertOk();
        $this->assertDatabaseCount('writing_review_deliveries', 1);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertOk();
        $this->assertDatabaseCount('writing_review_deliveries', 2);
        $this->assertDatabaseCount('learning_events', 7);
    }

    public function test_latest_summaries_and_bounded_history_preserve_all_revisions_and_semantic_replays(): void
    {
        $request = $this->requestReview();
        $reviewer = $this->reviewer();
        $this->actingAs($reviewer, 'web');
        $first = $this->decision();
        $this->postJson('/reviews/'.$request['id'].'/decisions', $first)->assertOk();
        $reordered = $first;
        $reordered['dimensions'] = array_reverse(array_map(fn ($dimension) => ['reason' => $dimension['reason'], 'result' => $dimension['result'], 'id' => $dimension['id']], $first['dimensions']));
        $this->postJson('/reviews/'.$request['id'].'/decisions', $reordered)->assertOk()->assertJsonPath('revision', 1);
        for ($revision = 1; $revision < 12; $revision++) {
            $data = $this->decision($revision);
            $this->postJson('/reviews/'.$request['id'].'/decisions', $data)->assertOk()->assertJsonPath('revision', $revision + 1);
        }
        $this->get('/reviews?status=reviewed', ['X-Inertia' => 'true'])->assertJsonPath('props.reviews.0.revision', 12)->assertJsonCount(10, 'props.reviews.0.history')->assertJsonPath('props.reviews.0.history.0.revision', 12)->assertJsonPath('props.reviews.0.history_total', 12)->assertJsonPath('props.reviews.0.history_pagination.last_page', 2);
        $this->getJson('/reviews/'.$request['id'].'/history?page=2')->assertOk()->assertJsonCount(2, 'data')->assertJsonPath('data.0.revision', 2)->assertJsonPath('data.1.revision', 1)->assertJsonPath('pagination.total', 12);
        $this->assertDatabaseCount('writing_review_deliveries', 0);
        $this->assertDatabaseCount('writing_review_decisions', 12);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/api/v1/writing-reviews')->assertJsonPath('data.0.revision', 12)->assertJsonMissingPath('data.0.history');
        $this->getJson('/api/v1/learning-sessions/'.$this->attempt['session_id'])->assertJsonPath('events.0.human_review.revision', 12);
        DB::enableQueryLog();
        DB::flushQueryLog();
        app(LearningEvents::class)->session($this->owner, $this->attempt['session_id'], false);
        $before = DB::getQueryLog();
        DB::disableQueryLog();
        DB::flushQueryLog();
        $this->assertCount(6, $before);
        $this->assertTrue(collect($before)->contains(fn ($query) => str_contains($query['query'], 'max(revision)')));
        $this->actingAs($reviewer, 'web');
        $this->postJson('/reviews/'.$request['id'].'/decisions', $this->decision(12))->assertOk();
        DB::enableQueryLog();
        DB::flushQueryLog();
        $latest = app(LearningEvents::class)->session($this->owner, $this->attempt['session_id'], false);
        $after = DB::getQueryLog();
        DB::disableQueryLog();
        DB::flushQueryLog();
        $this->assertCount(count($before), $after);
        $this->assertSame(13, $latest['events'][0]['human_review']['revision']);
        Sanctum::actingAs($this->owner, ['catalog:read', 'practice:write']);
        $this->getJson('/reviews/'.$request['id'].'/history')->assertForbidden();
        $reviewer->is_editor = false;
        $reviewer->save();
        $this->actingAs($reviewer, 'web');
        $this->getJson('/reviews/'.$request['id'].'/history')->assertForbidden();
    }
}
