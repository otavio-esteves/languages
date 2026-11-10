<?php

use App\Http\Controllers\EditorialController;
use App\Http\Controllers\LearningController;
use App\Http\Controllers\WritingReviewController;
use App\Http\Middleware\RequireEditor;
use App\Http\Middleware\RequireReviewer;
use App\Services\DayProgress;
use App\Services\LearningContent;
use App\Services\LearningEvents;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Route;
use Illuminate\Validation\ValidationException;
use Inertia\Inertia;

$landing = fn (Request $request) => $request->user()->is_editor ? route('editorial') : ($request->user()->is_reviewer ? url('/reviews') : url('/writing-feedback'));
Route::get('/', fn (Request $request) => redirect($landing($request)))->middleware('auth');
Route::get('/login', fn () => Inertia::render('Login'))->middleware('guest')->name('login');
Route::post('/login', function (Request $request) use ($landing) {
    $credentials = $request->validate(['email' => 'required|email|max:254', 'password' => 'required|string|max:1024']);
    if (! Auth::attempt($credentials)) {
        throw ValidationException::withMessages(['email' => 'Credenciais inválidas.']);
    }
    $request->session()->regenerate();
    Inertia::clearHistory();

    $intended = $request->session()->get('url.intended');
    $path = is_string($intended) ? parse_url($intended, PHP_URL_PATH) : null;
    if (is_string($path) && ((($path === '/editorial' || str_starts_with($path, '/editorial/')) && ! $request->user()->is_editor)
        || (($path === '/reviews' || str_starts_with($path, '/reviews/')) && ! $request->user()->is_reviewer))) {
        $request->session()->forget('url.intended');
    }

    return redirect()->intended($landing($request));
})->middleware(['guest', 'throttle:device-login']);
Route::post('/logout', function (Request $request) {
    Auth::logout();
    $request->session()->invalidate();
    $request->session()->regenerateToken();
    Inertia::clearHistory();

    return redirect()->route('login');
})->middleware('auth');
Route::get('/editorial', EditorialController::class)->middleware('auth')->name('editorial');
Route::get('/writing-feedback', [WritingReviewController::class, 'feedback'])->middleware('auth');
Route::get('/reviews', [WritingReviewController::class, 'queue'])->middleware(['auth', RequireReviewer::class]);
Route::get('/reviews/{id}/history', [WritingReviewController::class, 'history'])->middleware(['auth', RequireReviewer::class]);
Route::post('/reviews/{id}/decisions', [WritingReviewController::class, 'decide'])->middleware(['auth', RequireReviewer::class, 'throttle:30,1']);

Route::prefix('editorial')->middleware(['auth', RequireEditor::class])->group(function () {
    Route::post('writing-reviews', [WritingReviewController::class, 'store'])->middleware('throttle:30,1');
    Route::get('writing-reviews', [WritingReviewController::class, 'owner']);
    Route::get('practice/{id}/{version}', function (Request $request, string $id, int $version, LearningContent $content) {
        $selection = $request->validate(['snapshot' => 'sometimes|string|regex:/^[a-f0-9]{64}$/']);
        if (isset($selection['snapshot'])) {
            $snapshot = $content->snapshot($request->user(), $selection['snapshot']);
            $mission = json_decode($snapshot->document);
            abort_unless($mission->id === $id && $mission->version === $version, 404);
            $manifest = ['package_hash' => $selection['snapshot'], 'is_preview' => (bool) $snapshot->is_preview];
        } else {
            $manifest = $content->manifest($request->user(), $id, $version, true);
            $mission = json_decode($content->snapshot($request->user(), $manifest['package_hash'])->document);
        }

        return Inertia::render('PracticePreview', ['mission' => $mission, 'manifest' => $manifest, 'progress' => app(DayProgress::class)->get($request->user(), $manifest['package_hash']), 'answeredActivityIds' => app(LearningEvents::class)->answeredActivities($request->user(), [$manifest['package_hash']])[$manifest['package_hash']] ?? []]);
    })->whereNumber('version');
    Route::post('day-progress', function (Request $request, DayProgress $progress) {
        $data = $request->validate(['package_hash' => 'required|string|size:64', 'day' => 'required|integer|min:0', 'completed' => 'required|boolean', 'cursor' => 'sometimes|integer|min:0', 'lesson' => 'sometimes|string|max:150']);

        return $progress->save($request->user(), $data['package_hash'], $data['day'], $data['completed'], $data['cursor'] ?? null, $data['lesson'] ?? null);
    });
    Route::get('content-snapshots/{hash}/assets/{asset}', [LearningController::class, 'asset']);
    Route::post('attempts/batch', [LearningController::class, 'attempts']);
    Route::post('exposures/batch', [LearningController::class, 'exposures']);
    Route::get('learning-sessions/{id}', [LearningController::class, 'session']);
});
