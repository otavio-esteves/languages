<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('writing_review_deliveries', function (Blueprint $table) {
            $table->foreignUuid('decision_id')->primary()->constrained('writing_review_decisions')->cascadeOnDelete();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->char('package_hash', 64);
            $table->jsonb('target_ids');
            $table->string('modality', 16)->default('writing');
            $table->timestampTz('delivered_at');
            $table->unsignedBigInteger('last_event_id');
            $table->index(['user_id', 'package_hash']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('writing_review_deliveries');
    }
};
