<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('content_snapshots', function (Blueprint $table) {
            $table->char('hash', 64)->primary();
            $table->string('package_id');
            $table->unsignedInteger('version');
            $table->boolean('is_preview');
            $table->text('document');
            $table->timestamps();
        });
        Schema::create('learning_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->uuid('event_id');
            $table->uuid('session_id');
            $table->uuid('device_id');
            $table->unsignedBigInteger('local_sequence');
            $table->string('kind', 16);
            $table->char('package_hash', 64);
            $table->char('payload_hash', 64);
            $table->boolean('is_preview');
            $table->jsonb('payload');
            $table->jsonb('assessment')->nullable();
            $table->timestamps();
            $table->unique(['user_id', 'event_id']);
            $table->unique(['user_id', 'device_id', 'local_sequence']);
            $table->index(['user_id', 'session_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('learning_events');
        Schema::dropIfExists('content_snapshots');
    }
};
