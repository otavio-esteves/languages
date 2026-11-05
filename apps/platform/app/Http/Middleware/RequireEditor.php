<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class RequireEditor
{
    public function handle(Request $request, Closure $next)
    {
        abort_unless($request->user()?->is_editor, 403);

        return $next($request);
    }
}
