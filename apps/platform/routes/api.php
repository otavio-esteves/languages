<?php

use App\Http\Controllers\DeviceTokenController;
use App\Http\Controllers\LearningController;
use App\Http\Controllers\WritingReviewController;
use App\Services\ContentCatalog;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function () {
    Route::post('auth/token', [DeviceTokenController::class, 'store'])->middleware('throttle:device-login');
    Route::middleware(['auth:sanctum', 'abilities:catalog:read'])->group(function () {
        Route::delete('auth/token', [DeviceTokenController::class, 'destroy']);
        Route::get('editorial/catalog', [LearningController::class, 'editorial']);
        Route::get('content-packages/{id}/manifest', [LearningController::class, 'manifest']);
        Route::get('content-snapshots/{hash}', [LearningController::class, 'document']);
        Route::get('content-snapshots/{hash}/assets/{asset}', [LearningController::class, 'asset']);
        Route::post('attempts/batch', [LearningController::class, 'attempts'])->middleware(['abilities:practice:write', 'throttle:60,1']);
        Route::post('exposures/batch', [LearningController::class, 'exposures'])->middleware(['abilities:practice:write', 'throttle:60,1']);
        Route::get('learning-sessions/{id}', [LearningController::class, 'session']);
        Route::post('writing-reviews', [WritingReviewController::class, 'store'])->middleware(['abilities:practice:write', 'throttle:30,1']);
        Route::get('writing-reviews', [WritingReviewController::class, 'owner']);
        Route::get('me', fn (Request $request) => response()->json($request->user()->only(['id', 'name', 'email', 'is_editor']))->header('Cache-Control', 'no-store'));
        Route::get('catalog', fn (ContentCatalog $catalog) => response()->json($catalog->published())->header('Cache-Control', 'private, no-store'));
    });
});
