<?php

namespace Tests\Unit;

use Opis\JsonSchema\Validator;
use PHPUnit\Framework\TestCase;

class ContractTest extends TestCase
{
    public function test_learning_fixtures_and_draft_packages_match_their_schemas(): void
    {
        $root = dirname(__DIR__, 4);
        $cases = [
            'package-manifest' => [$root.'/contracts/fixtures/manifest.json'],
            'attempt-event' => [$root.'/contracts/fixtures/attempt.json'],
            'exposure-event' => [$root.'/contracts/fixtures/exposure.json'],
            'evidence' => [$root.'/contracts/fixtures/evidence.json'],
            'catalog' => [$root.'/contracts/fixtures/catalog.json'],
            'content-package' => glob($root.'/content/pt-BR_en/missions/*.json'),
        ];
        foreach ($cases as $name => $files) {
            $schema = json_decode(file_get_contents($root.'/contracts/'.$name.'.schema.json'));
            foreach ($files as $file) {
                $data = json_decode(file_get_contents($file));
                $result = (new Validator)->validate($data, $schema);
                $this->assertTrue($result->isValid(), basename($file).' deve atender '.$name);
            }
        }
        $attempt = json_decode(file_get_contents($root.'/contracts/fixtures/attempt.json'));
        $attempt->score = 100;
        $attemptSchema = json_decode(file_get_contents($root.'/contracts/attempt-event.schema.json'));
        $this->assertFalse((new Validator)->validate($attempt, $attemptSchema)->isValid(), 'Cliente não envia score canônico');
        $catalog = json_decode(file_get_contents($root.'/contracts/fixtures/catalog.json'));
        $catalog->data[0]->version = 0;
        $schema = json_decode(file_get_contents($root.'/contracts/catalog.schema.json'));
        $this->assertFalse((new Validator)->validate($catalog, $schema)->isValid());
    }
}
