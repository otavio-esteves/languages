<?php

use App\Models\User;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

Artisan::command('app:create-user {--editor : Permissão de prévia editorial}', function () {
    $name = $this->ask('Nome');
    $email = Str::lower((string) $this->ask('E-mail'));
    $secret = $this->secret('Senha (mínimo 12 caracteres)');
    Validator::make(['name' => $name, 'email' => $email, 'password' => $secret], [
        'name' => 'required|string|max:255',
        'email' => 'required|email|max:254|unique:users,email',
        'password' => 'required|string|min:12',
    ])->validate();
    if (! $this->confirm('Criar esta conta local?', false)) {
        return;
    }
    $user = new User(['name' => $name, 'email' => $email, 'password' => $secret]);
    $user->is_editor = (bool) $this->option('editor');
    $user->save();
    $this->info('Conta criada. Nenhuma senha foi exibida.');
})->purpose('Criar explicitamente uma conta sem credenciais padrão');

Schedule::command('sanctum:prune-expired --hours=24')->daily();
