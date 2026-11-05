<?php

namespace App\Http\Controllers;

use App\Services\ContentCatalog;
use Illuminate\Http\Request;
use Inertia\Inertia;

class EditorialController extends Controller
{
    public function __invoke(Request $request, ContentCatalog $catalog)
    {
        abort_unless($request->user()->is_editor, 403);

        return Inertia::render('Editorial', ['packages' => $catalog->packages()]);
    }
}
