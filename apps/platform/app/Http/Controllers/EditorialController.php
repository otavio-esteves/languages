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

        return Inertia::render('Editorial', ['packages' => array_map(function ($package) use ($request) {
            $manifest = app(LearningContent::class)->manifest($request->user(), $package['id'], $package['version'], true);
            $package['progress'] = app(DayProgress::class)->get($request->user(), $manifest['package_hash']);

            return $package;
        }, $catalog->textTraining())]);
    }
}
