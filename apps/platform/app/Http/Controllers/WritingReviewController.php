<?php

namespace App\Http\Controllers;

use App\Services\WritingReviews;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Inertia\Inertia;

class WritingReviewController extends Controller
{
    public function store(Request $request, WritingReviews $reviews)
    {
        $data = $request->validate(['attempt_id' => 'required|uuid']);
        if (array_diff(array_keys($request->all()), ['attempt_id'])) {
            throw ValidationException::withMessages(['attempt_id' => 'Informe somente a tentativa a revisar.']);
        }

        return response()->json($reviews->request($request->user(), $data['attempt_id']))->header('Cache-Control', 'private, no-store');
    }

    private function listing(Request $request, WritingReviews $reviews, bool $reviewer): array
    {
        $data = $request->validate(['status' => 'sometimes|in:pending,reviewed,all', 'page' => 'sometimes|integer|min:1', 'limit' => 'sometimes|integer|min:1|max:50', 'per_page' => 'sometimes|integer|min:1|max:50']);

        return $reviews->listing($request->user(), $reviewer, $data['status'] ?? ($reviewer ? 'pending' : 'all'), $data['limit'] ?? $data['per_page'] ?? 20);
    }

    public function owner(Request $request, WritingReviews $reviews)
    {
        return response()->json($this->listing($request, $reviews, false))->header('Cache-Control', 'private, no-store');
    }

    public function feedback(Request $request, WritingReviews $reviews)
    {
        $result = $this->listing($request, $reviews, false);

        return Inertia::render('WritingFeedback', ['reviews' => $result['data'], 'pagination' => $result['pagination']])->toResponse($request)->header('Cache-Control', 'private, no-store');
    }

    public function queue(Request $request, WritingReviews $reviews)
    {
        $result = $this->listing($request, $reviews, true);

        return Inertia::render('WritingReviews', ['reviews' => $result['data'], 'pagination' => $result['pagination'], 'filters' => ['status' => $request->input('status', 'pending')]])->toResponse($request)->header('Cache-Control', 'private, no-store');
    }

    public function decide(Request $request, string $id, WritingReviews $reviews)
    {
        abort_unless(Str::isUuid($id), 404);
        $data = $request->validate([
            'decision_id' => 'required|uuid', 'expected_revision' => 'required|integer|min:0',
            'feedback' => 'required|string|max:2000', 'dimensions' => 'required|array|min:1|max:20',
            'dimensions.*' => 'required|array:id,result,reason', 'dimensions.*.id' => 'required|string|max:100',
            'dimensions.*.result' => 'required|in:met,not_met,not_observed,abstained', 'dimensions.*.reason' => 'required|string|max:2000',
        ]);
        if (array_diff(array_keys($request->all()), ['decision_id', 'expected_revision', 'feedback', 'dimensions'])) {
            throw ValidationException::withMessages(['decision' => 'Campos não previstos no contrato de revisão.']);
        }

        return response()->json($reviews->decide($request->user(), $id, $data))->header('Cache-Control', 'private, no-store');
    }

    public function history(Request $request, string $id, WritingReviews $reviews)
    {
        abort_unless(Str::isUuid($id), 404);
        $data = $request->validate(['page' => 'sometimes|integer|min:1']);

        return response()->json($reviews->history($request->user(), $id, $data['page'] ?? 1))->header('Cache-Control', 'private, no-store');
    }
}
