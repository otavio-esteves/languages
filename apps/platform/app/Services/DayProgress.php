<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\DB;

class DayProgress
{
    public function get(User $user, string $hash): array
    {
        $row = DB::table('learning_day_progress')->where('user_id', $user->id)->where('package_hash', $hash)->first();

        $package = json_decode(app(LearningContent::class)->snapshot($user, $hash)->document, true);
        $ids = array_values(array_unique(array_map(fn ($a) => $a['content']['phase_id'] ?? 'practice', $package['activities'])));
        $start = array_key_first(array_filter($package['activities'], fn ($a) => ($a['content']['phase_id'] ?? 'practice') === $ids[$row->day ?? 0]));

        return ['cursor' => max($start, (int) ($row->cursor ?? 0)), 'day' => (int) ($row->day ?? 0), 'completed' => (bool) ($row->completed ?? false)];
    }

    public function save(User $user, string $hash, int $day, bool $completed, ?int $cursor = null): array
    {
        $package = json_decode(app(LearningContent::class)->snapshot($user, $hash)->document, true);
        $days = array_unique(array_map(fn ($a) => $a['content']['phase_id'] ?? 'practice', $package['activities']));
        abort_unless($day < count($days) && (! $completed || $day === count($days) - 1), 422);
        $ids = array_values($days);
        $indices = array_keys(array_filter($package['activities'], fn ($a) => ($a['content']['phase_id'] ?? 'practice') === $ids[$day]));
        $cursor ??= $indices[0];
        abort_unless(in_array($cursor, $indices, true), 422);
        DB::transaction(function () use ($user, $hash, $day, $completed, $cursor) {
            DB::table('learning_day_progress')->insertOrIgnore(['user_id' => $user->id, 'package_hash' => $hash, 'day' => 0, 'completed' => false, 'created_at' => now(), 'updated_at' => now()]);
            $row = DB::table('learning_day_progress')->where('user_id', $user->id)->where('package_hash', $hash)->lockForUpdate()->first();
            DB::table('learning_day_progress')->where('id', $row->id)->update(['cursor' => max($cursor, $row->cursor), 'day' => max($day, $row->day), 'completed' => $completed || $row->completed, 'updated_at' => now()]);
        });

        return $this->get($user, $hash);
    }
}
