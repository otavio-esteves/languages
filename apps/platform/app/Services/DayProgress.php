<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\DB;

class DayProgress
{
    public function savedPackages(User $user): array
    {
        return DB::table('learning_day_progress as progress')
            ->join('content_snapshots as snapshot', 'snapshot.hash', '=', 'progress.package_hash')
            ->where('progress.user_id', $user->id)->orderByDesc('progress.updated_at')->orderByDesc('progress.id')
            ->select(['snapshot.document', 'snapshot.hash', 'snapshot.is_preview', 'progress.day', 'progress.cursor', 'progress.completed', 'progress.completed_lessons'])
            ->get()->filter(function ($row) use ($user) {
                return (! $row->is_preview || $user->is_editor) && ($row->day > 0 || $row->cursor > 0 || $row->completed || count(json_decode($row->completed_lessons ?? '[]', true)) > 0);
            })->map(function ($row) use ($user) {
                $package = json_decode($row->document, true);
                $package['package_hash'] = $row->hash;
                $package['progress'] = $this->get($user, $row->hash);

                return $package;
            })->filter(fn ($package) => $package['purpose'] === 'training' && $package['assets'] === [] && ! array_diff(array_column($package['activities'], 'modality'), ['reading', 'writing']))->values()->all();
    }

    private function lessons(array $package): array
    {
        $days = [];
        foreach ($package['activities'] as $index => $activity) {
            $days[$activity['content']['phase_id'] ?? 'practice'][] = $index;
        }
        $lessons = [];
        foreach ($days as $id => $indices) {
            $count = min(3, count($indices));
            foreach ($indices as $position => $index) {
                $lessons[$id.':'.intdiv($position * $count, count($indices))][] = $index;
            }
        }

        return $lessons;
    }

    private function completedLessons(?object $row, array $lessons): array
    {
        if (isset($row->completed_lessons)) {
            return json_decode($row->completed_lessons, true);
        }

        return array_keys(array_filter($lessons, fn ($indices) => ($row->completed ?? false) || max($indices) < ($row->cursor ?? 0)));
    }

    public function get(User $user, string $hash): array
    {
        $row = DB::table('learning_day_progress')->where('user_id', $user->id)->where('package_hash', $hash)->first();
        $package = json_decode(app(LearningContent::class)->snapshot($user, $hash)->document, true);
        $ids = array_values(array_unique(array_map(fn ($a) => $a['content']['phase_id'] ?? 'practice', $package['activities'])));
        $start = array_key_first(array_filter($package['activities'], fn ($a) => ($a['content']['phase_id'] ?? 'practice') === $ids[$row->day ?? 0]));

        return ['cursor' => max($start, (int) ($row->cursor ?? 0)), 'day' => (int) ($row->day ?? 0), 'completed' => (bool) ($row->completed ?? false), 'completed_lessons' => $this->completedLessons($row, $this->lessons($package))];
    }

    public function save(User $user, string $hash, int $day, bool $completed, ?int $cursor = null, ?string $lesson = null): array
    {
        $package = json_decode(app(LearningContent::class)->snapshot($user, $hash)->document, true);
        $days = array_unique(array_map(fn ($a) => $a['content']['phase_id'] ?? 'practice', $package['activities']));
        abort_unless($day >= 0 && $day < count($days) && (! $completed || $day === count($days) - 1), 422);
        $ids = array_values($days);
        $indices = array_keys(array_filter($package['activities'], fn ($a) => ($a['content']['phase_id'] ?? 'practice') === $ids[$day]));
        $cursor ??= $indices[0];
        $lessons = $this->lessons($package);
        abort_unless(in_array($cursor, $indices, true) && ($lesson === null || isset($lessons[$lesson])), 422);
        DB::transaction(function () use ($user, $hash, $day, $completed, $cursor, $lesson, $lessons) {
            DB::table('learning_day_progress')->insertOrIgnore(['user_id' => $user->id, 'package_hash' => $hash, 'day' => 0, 'completed' => false, 'created_at' => now(), 'updated_at' => now()]);
            $row = DB::table('learning_day_progress')->where('user_id', $user->id)->where('package_hash', $hash)->lockForUpdate()->first();
            $done = $this->completedLessons($row, $lessons);
            if ($lesson !== null) {
                $done[] = $lesson;
            } else {
                // Compatibility with readers that still send only a sequential cursor.
                $done = array_merge($done, array_keys(array_filter($lessons, fn ($indices) => $completed || max($indices) < $cursor)));
            }
            $done = array_values(array_unique($done));
            DB::table('learning_day_progress')->where('id', $row->id)->update(['cursor' => max($cursor, $row->cursor), 'day' => max($day, $row->day), 'completed' => count($done) === count($lessons), 'completed_lessons' => json_encode($done), 'updated_at' => now()]);
        });

        return $this->get($user, $hash);
    }
}
