<?php

namespace App\Http\Controllers;

use App\Services\ContentCatalog;
use App\Services\DayProgress;
use App\Services\LearningContent;
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
        $preferred = array_map(fn ($package) => collect($saved)->firstWhere('id', $package['id']) ?? $package, $current);

        return Inertia::render('Editorial', ['packages' => $preferred, 'currentPackages' => $current, 'savedPackages' => $saved]);
    }
}
