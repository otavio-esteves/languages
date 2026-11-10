<?php

namespace App\Http\Middleware;

use Illuminate\Http\Request;
use Inertia\Inertia;
use Inertia\Middleware;

class HandleInertiaRequests extends Middleware
{
    protected $rootView = 'app';

    public function share(Request $request): array
    {
        Inertia::encryptHistory($request->user() !== null);

        return [...parent::share($request), 'permissions' => [
            'review_writing' => (bool) $request->user()?->is_reviewer,
        ]];
    }
}
