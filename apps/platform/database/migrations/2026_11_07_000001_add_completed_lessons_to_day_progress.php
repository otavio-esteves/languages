<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('learning_day_progress', fn (Blueprint $table) => $table->jsonb('completed_lessons')->nullable());
    }

    public function down(): void
    {
        Schema::table('learning_day_progress', fn (Blueprint $table) => $table->dropColumn('completed_lessons'));
    }
};
