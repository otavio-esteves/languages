<?php

// PHPUnit's <env> does not replace Docker values already present in $_SERVER.
// Apply the declared test environment consistently before Laravel is booted.
$configuration = simplexml_load_file(__DIR__.'/../phpunit.xml');
foreach ($configuration->php->env as $variable) {
    $name = (string) $variable['name'];
    $value = (string) $variable['value'];
    putenv($name.'='.$value);
    $_ENV[$name] = $value;
    $_SERVER[$name] = $value;
}
$testPassword = getenv('DB_TEST_PASSWORD');
if (! $testPassword) {
    throw new RuntimeException('Credenciais dedicadas de teste ausentes. Execute scripts/bootstrap.sh.');
}
foreach (['DB_USERNAME' => 'idiomas_test', 'DB_PASSWORD' => $testPassword] as $name => $value) {
    putenv($name.'='.$value);
    $_ENV[$name] = $value;
    $_SERVER[$name] = $value;
}
require __DIR__.'/../vendor/autoload.php';
