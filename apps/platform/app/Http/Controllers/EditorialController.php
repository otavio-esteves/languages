<?php

namespace App\Http\Controllers;

use App\Services\ContentCatalog;
use App\Services\DayProgress;
use App\Services\LearningContent;
use App\Services\LearningEvents;
use Illuminate\Http\Request;
use Inertia\Inertia;

class EditorialController extends Controller
{
    public function __invoke(Request $request, ContentCatalog $catalog)
    {
        abort_unless($request->user()->is_editor, 403);

        $progress = app(DayProgress::class);
        $current = array_map(function ($package) use ($request, $progress) {
            $manifest = app(LearningContent::class)->manifest($request->user(), $package['id'], $package['version'], true);
            $package['package_hash'] = $manifest['package_hash'];
            $package['progress'] = $progress->get($request->user(), $manifest['package_hash']);

            return $package;
        }, $catalog->textTraining());
        $saved = $progress->savedPackages($request->user());
        $answers = app(LearningEvents::class)->answeredActivities($request->user(), array_values(array_unique(array_column([...$current, ...$saved], 'package_hash'))));
        $withAnswers = function ($package) use ($answers) {
            $package['answeredActivityIds'] = $answers[$package['package_hash']] ?? [];

            return $package;
        };
        $current = array_map($withAnswers, $current);
        $saved = array_map($withAnswers, $saved);
        $preferred = array_map(fn ($package) => collect($saved)->firstWhere('id', $package['id']) ?? $package, $current);

        return Inertia::render('Editorial', ['packages' => array_map($this->overview(...), $preferred), 'currentPackages' => array_map($this->overview(...), $current), 'savedPackages' => array_map($this->overview(...), $saved)]);
    }

    private function overview(array $package): array
    {
        $seen = [];
        $package['activities'] = array_map(function ($activity) use (&$seen) {
            $phase = $activity['content']['phase_id'] ?? 'practice';
            $fields = isset($seen[$phase]) ? ['phase_id', 'label'] : ['phase_id', 'label', 'phase_title', 'phase_description', 'phase_level', 'day_estimated_minutes'];
            $seen[$phase] = true;

            return ['id' => $activity['id'], 'type' => $activity['type'], 'content' => array_intersect_key($activity['content'], array_flip($fields))];
        }, $package['activities']);

        return array_intersect_key($package, array_flip(['id', 'version', 'title', 'package_hash', 'progress', 'answeredActivityIds', 'activities']));
    }
}
