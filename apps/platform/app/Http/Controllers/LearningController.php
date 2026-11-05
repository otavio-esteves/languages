<?php

namespace App\Http\Controllers;

use App\Services\ContentCatalog;
use App\Services\LearningContent;
use App\Services\LearningEvents;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class LearningController extends Controller
{
    public function editorial(Request $request, ContentCatalog $catalog)
    {
        abort_unless($request->user()->is_editor, 403);

        return ['schema_version' => '1.0.0', 'data' => array_values(array_map(fn ($p) => array_intersect_key($p, array_flip(['id', 'version', 'title', 'language_pair', 'capability_ids'])), array_filter($catalog->packages(), fn ($p) => $p['purpose'] === 'training')))];
    }

    public function manifest(Request $request, string $id, LearningContent $content)
    {
        $data = $request->validate(['version' => 'required|integer|min:1', 'preview' => 'sometimes|boolean']);

        return response()->json($content->manifest($request->user(), $id, (int) $data['version'], $request->boolean('preview')))->header('Cache-Control', 'private, no-store');
    }

    public function document(Request $request, string $hash, LearningContent $content)
    {
        return response($content->snapshot($request->user(), $hash)->document)->header('Content-Type', 'application/json')->header('Cache-Control', 'private, no-store');
    }

    public function asset(Request $request, string $hash, string $asset, LearningContent $content)
    {
        $snapshot = $content->snapshot($request->user(), $hash);

        return response()->file($content->assetFile(json_decode($snapshot->document, true), $asset), ['Content-Type' => 'audio/wav', 'Cache-Control' => 'private, no-store']);
    }

    public function attempts(Request $request, LearningEvents $events)
    {
        return $this->batch($request, $events, 'attempt');
    }

    public function exposures(Request $request, LearningEvents $events)
    {
        return $this->batch($request, $events, 'exposure');
    }

    private function batch(Request $request, LearningEvents $events, string $kind)
    {
        abort_if(strlen($request->getContent()) > 524288, 413);
        $data = $request->validate(['events' => 'required|array|min:1|max:50']);

        return ['results' => $events->receive($request->user(), $kind, $data['events'])];
    }

    public function session(Request $request, string $id, LearningEvents $events)
    {
        abort_unless(Str::isUuid($id), 404);

        return $events->session($request->user(), $id);
    }
}
