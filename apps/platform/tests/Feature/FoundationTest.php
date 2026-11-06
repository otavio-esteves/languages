<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use Opis\JsonSchema\Validator;
use Tests\TestCase;

class FoundationTest extends TestCase
{
    use RefreshDatabase;

    private string $contentDirectory;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutVite();
        $this->contentDirectory = storage_path('framework/testing/'.Str::uuid());
        File::ensureDirectoryExists($this->contentDirectory);
        config(['learning.content_path' => $this->contentDirectory]);
    }

    protected function tearDown(): void
    {
        File::deleteDirectory($this->contentDirectory);
        parent::tearDown();
    }

    private function account(bool $editor = false): User
    {
        return User::factory()->create(['is_editor' => $editor]);
    }

    private function bearer(User $user, ?\DateTimeInterface $expires = null): array
    {
        return ['Authorization' => 'Bearer '.$user->createToken('test-device', ['catalog:read'], $expires ?? now()->addDay())->plainTextToken];
    }

    public function test_operator_can_explicitly_create_an_editor_without_default_credentials(): void
    {
        $this->artisan('app:create-user --editor')
            ->expectsQuestion('Nome', 'Synthetic test editor')
            ->expectsQuestion('E-mail', 'explicit-editor@example.test')
            ->expectsQuestion('Senha (mínimo 12 caracteres)', 'test-only-password-123')
            ->expectsConfirmation('Criar esta conta local?', 'yes')
            ->assertSuccessful();
        $user = User::where('email', 'explicit-editor@example.test')->sole();
        $this->assertTrue($user->is_editor);
        $this->assertTrue(Hash::check('test-only-password-123', $user->password));
    }

    public function test_database_is_dedicated_before_refresh_database_runs(): void
    {
        $this->assertTrue(app()->environment('testing'));
        $this->assertSame('idiomas_test', DB::selectOne('select current_database() as name')->name);
    }

    public function test_unauthenticated_catalog_and_profile_are_private(): void
    {
        $this->getJson('/api/v1/catalog')->assertUnauthorized();
        $this->getJson('/api/v1/me')->assertUnauthorized();
    }

    public function test_credentials_issue_a_finite_token_and_invalid_credentials_do_not(): void
    {
        $user = User::factory()->create(['password' => 'test-only-strong-password']);
        $this->postJson('/api/v1/auth/token', ['email' => $user->email, 'password' => 'wrong', 'device_name' => 'test'])->assertUnprocessable();
        $response = $this->postJson('/api/v1/auth/token', ['email' => $user->email, 'password' => 'test-only-strong-password', 'device_name' => 'test'])->assertCreated();
        $this->assertNotEmpty($response->json('token'));
        $this->assertEqualsWithDelta(now()->addDay()->timestamp, strtotime($response->json('expires_at')), 2);
        $this->assertArrayNotHasKey('password', $response->json('user'));
        $this->assertNotSame($response->json('token'), $user->tokens()->first()->token);
    }

    public function test_login_is_rate_limited(): void
    {
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v1/auth/token', ['email' => 'rate-test@example.test', 'password' => 'wrong', 'device_name' => 'test'])->assertUnprocessable();
        }
        $this->postJson('/api/v1/auth/token', ['email' => 'rate-test@example.test', 'password' => 'wrong', 'device_name' => 'test'])->assertStatus(429);
    }

    public function test_device_tokens_are_isolated_and_revoking_one_preserves_the_other(): void
    {
        $first = $this->account();
        $second = $this->account();
        $a = $this->bearer($first);
        $otherDevice = $this->bearer($first);
        $b = $this->bearer($second);
        $this->getJson('/api/v1/me', $a)->assertOk()->assertJsonPath('id', $first->id);
        $this->deleteJson('/api/v1/auth/token', [], $a)->assertNoContent();
        auth()->forgetGuards();
        $this->getJson('/api/v1/me', $a)->assertUnauthorized();
        auth()->forgetGuards();
        $this->getJson('/api/v1/me', $otherDevice)->assertOk()->assertJsonPath('id', $first->id);
        auth()->forgetGuards();
        $this->getJson('/api/v1/me', $b)->assertOk()->assertJsonPath('id', $second->id);
    }

    public function test_expired_and_wrong_scope_tokens_cannot_read_catalog(): void
    {
        $user = $this->account();
        $this->getJson('/api/v1/catalog', $this->bearer($user, now()->subMinute()))->assertUnauthorized();
        auth()->forgetGuards();
        $wrongScope = $user->createToken('test', ['unrelated'], now()->addDay())->plainTextToken;
        $this->getJson('/api/v1/catalog', ['Authorization' => 'Bearer '.$wrongScope])->assertForbidden();
    }

    public function test_only_editor_can_preview_drafts_and_preview_does_not_publish(): void
    {
        $source = dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v2.json';
        File::copy($source, $this->contentDirectory.'/draft.json');
        $user = $this->account();
        $this->actingAs($user)->get('/editorial')->assertForbidden();
        $this->actingAs($this->account(true))->get('/editorial')->assertOk()->assertSee('order-a-drink');
        $this->getJson('/api/v1/catalog', $this->bearer($user))->assertExactJson(['schema_version' => '1.0.0', 'data' => []]);
    }

    public function test_published_synthetic_catalog_matches_shared_fixture_and_schema(): void
    {
        $fixture = json_decode(file_get_contents(config('learning.contracts_path').'/fixtures/catalog.json'), true);
        $package = json_decode(file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v1.json'), true);
        $package = array_replace($package, $fixture['data'][0]);
        $package['status'] = 'published';
        $package['provenance'] = ['author_type' => 'human_authored', 'license' => 'synthetic-test-only', 'reviewer' => 'synthetic-test-only', 'reviewed_at' => now()->toIso8601String()];
        $package['assets'] = [];
        $package['activities'] = [$package['activities'][0]];
        File::put($this->contentDirectory.'/synthetic.json', json_encode($package));
        $response = $this->getJson('/api/v1/catalog', $this->bearer($this->account()))->assertOk()->assertExactJson($fixture);
        $schema = json_decode(file_get_contents(config('learning.contracts_path').'/catalog.schema.json'));
        $this->assertTrue((new Validator)->validate(json_decode($response->getContent()), $schema)->isValid());
    }

    public function test_catalog_selects_latest_published_version_and_ignores_newer_draft(): void
    {
        $package = json_decode(file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v1.json'), true);
        $package['status'] = 'published';
        $package['provenance'] = ['author_type' => 'human_authored', 'license' => 'synthetic-test-only', 'reviewer' => 'synthetic-test-only', 'reviewed_at' => now()->toIso8601String()];
        $package['assets'] = [];
        $package['activities'] = [$package['activities'][0]];
        foreach ([2, 10, 11] as $version) {
            $package['version'] = $version;
            $package['status'] = $version === 11 ? 'draft' : 'published';
            File::put($this->contentDirectory.'/version-'.$version.'.json', json_encode($package));
        }
        $this->getJson('/api/v1/catalog', $this->bearer($this->account()))
            ->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.version', 10);
    }

    public function test_marking_a_draft_as_published_does_not_bypass_review(): void
    {
        $package = json_decode(file_get_contents(dirname(base_path(), 2).'/content/pt-BR_en/missions/order-a-drink.v1.json'));
        $package->status = 'published';
        File::put($this->contentDirectory.'/bad.json', json_encode($package));
        $this->withoutExceptionHandling();
        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('Publicação sem revisão');
        $this->getJson('/api/v1/catalog', $this->bearer($this->account()));
    }
}
