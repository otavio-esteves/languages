<?php

namespace Tests;

use App\Models\User;
use Illuminate\Foundation\Testing\TestCase as BaseTestCase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;

abstract class TestCase extends BaseTestCase
{
    protected function recordLessonActivities(User $user, string $hash, ?string $lesson = null): void
    {
        $package = json_decode(DB::table('content_snapshots')->where('hash', $hash)->value('document'), true);
        $days = [];
        foreach ($package['activities'] as $activity) {
            $days[$activity['content']['phase_id'] ?? 'practice'][] = $activity;
        }
        foreach ($days as $day => $activities) {
            foreach ($activities as $position => $activity) {
                $key = $day.':'.intdiv($position * min(3, count($activities)), count($activities));
                if (($lesson !== null && $lesson !== $key) || $activity['type'] === 'summary') {
                    continue;
                }
                $passive = in_array($activity['type'], ['purpose', 'dialogue', 'explanation'], true);
                $id = (string) Str::uuid();
                $payload = ['activity_id' => $activity['id'], 'kind' => 'training_use'];
                DB::table('learning_events')->insert(['user_id' => $user->id, 'event_id' => $id,
                    'session_id' => (string) Str::uuid(), 'device_id' => (string) Str::uuid(),
                    'local_sequence' => 1, 'kind' => $passive ? 'exposure' : 'attempt', 'package_hash' => $hash,
                    'payload_hash' => hash('sha256', json_encode($payload)), 'is_preview' => true,
                    'payload' => json_encode($payload), 'created_at' => now(), 'updated_at' => now()]);
            }
        }
    }

    public function createApplication()
    {
        $app = parent::createApplication();
        if (! $app->environment('testing') || config('database.default') !== 'pgsql' || config('database.connections.pgsql.database') !== 'idiomas_test') {
            throw new RuntimeException('Testes recusados: banco dedicado idiomas_test não está configurado.');
        }
        if (DB::selectOne('select current_database() as name')->name !== 'idiomas_test' || DB::selectOne('select current_user as name')->name !== 'idiomas_test') {
            throw new RuntimeException('Testes recusados: conexão real não aponta para idiomas_test.');
        }

        return $app;
    }
}
