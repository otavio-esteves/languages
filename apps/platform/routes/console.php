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

Artisan::command('app:reviewer {email : Conta existente} {--revoke : Revogar a permissão de avaliação}', function () {
    $user = User::where('email', Str::lower((string) $this->argument('email')))->first();
    if (! $user) {
        $this->error('Conta existente não encontrada. Nenhuma conta foi criada.');

        return 1;
    }
    $grant = ! $this->option('revoke');
    if (! $this->confirm($grant ? 'Conceder permissão de avaliação escrita a esta conta?' : 'Revogar permissão de avaliação escrita desta conta?', false)) {
        return 0;
    }
    $user->is_reviewer = $grant;
    $user->save();
    $this->info('Permissão atualizada. Isso não registra revisão humana de conteúdo ou respostas.');

    return 0;
})->purpose('Conceder ou revogar explicitamente avaliação escrita sem criar contas');
