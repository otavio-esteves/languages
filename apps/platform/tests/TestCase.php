<?php

namespace Tests;

use Illuminate\Foundation\Testing\TestCase as BaseTestCase;
use Illuminate\Support\Facades\DB;
use RuntimeException;

abstract class TestCase extends BaseTestCase
{
    public function createApplication()
    {
        $app = parent::createApplication();
        if (! $app->environment('testing') || config('database.default') !== 'pgsql' || config('database.connections.pgsql.database') !== 'idiomas_test') {
            throw new RuntimeException('Testes recusados: banco dedicado idiomas_test não está configurado.');
        }
        if (DB::selectOne('select current_database() as name')->name !== 'idiomas_test' || DB::selectOne('select current_user as name')->name !== 'idiomas_test') {
            throw new RuntimeException('Testes recusados: conexão real não aponta para idiomas_test.');
        }

        return $app;
    }
}
