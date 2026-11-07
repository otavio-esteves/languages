<?php

use App\Http\Controllers\EditorialController;
use App\Http\Controllers\LearningController;
use App\Http\Middleware\RequireEditor;
use App\Services\DayProgress;
use App\Services\LearningContent;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Route;
use Illuminate\Validation\ValidationException;
use Inertia\Inertia;

Route::get('/', fn () => redirect()->route('editorial'));
Route::get('/login', fn () => Inertia::render('Login'))->middleware('guest')->name('login');
Route::post('/login', function (Request $request) {
    $credentials = $request->validate(['email' => 'required|email|max:254', 'password' => 'required|string|max:1024']);
    if (! Auth::attempt($credentials)) {
        throw ValidationException::withMessages(['email' => 'Credenciais inválidas.']);
    }
    $request->session()->regenerate();

    return redirect()->intended(route('editorial'));
})->middleware(['guest', 'throttle:device-login']);
Route::post('/logout', function (Request $request) {
    Auth::logout();
    $request->session()->invalidate();
    $request->session()->regenerateToken();

    return redirect()->route('login');
})->middleware('auth');
Route::get('/editorial', EditorialController::class)->middleware('auth')->name('editorial');

Route::prefix('editorial')->middleware(['auth', RequireEditor::class])->group(function () {
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

        return Inertia::render('PracticePreview', ['mission' => $mission, 'manifest' => $manifest, 'progress' => app(DayProgress::class)->get($request->user(), $manifest['package_hash'])]);
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
