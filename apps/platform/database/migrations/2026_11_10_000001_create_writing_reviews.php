<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', fn (Blueprint $table) => $table->boolean('is_reviewer')->default(false));
        Schema::create('writing_review_requests', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('learning_event_id')->unique()->constrained('learning_events')->cascadeOnDelete();
            $table->timestampTz('requested_at');
            $table->index(['user_id', 'requested_at']);
        });
        Schema::create('writing_review_decisions', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('request_id')->constrained('writing_review_requests')->cascadeOnDelete();
            $table->foreignId('reviewer_id')->nullable()->constrained('users')->nullOnDelete();
            $table->unsignedInteger('revision');
            $table->uuid('supersedes_id')->nullable();
            $table->char('payload_hash', 64);
            $table->text('feedback');
            $table->jsonb('dimensions');
            $table->timestampTz('reviewed_at');
            $table->unique(['request_id', 'revision']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('writing_review_decisions');
        Schema::dropIfExists('writing_review_requests');
        Schema::table('users', fn (Blueprint $table) => $table->dropColumn('is_reviewer'));
    }
};
