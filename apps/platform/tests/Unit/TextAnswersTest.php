<?php

namespace Tests\Unit;

use App\Services\TextAnswers;
use PHPUnit\Framework\TestCase;

class TextAnswersTest extends TestCase
{
    public function test_shared_comparison_cases_preserve_alternatives_and_uncertainty(): void
    {
        $path = dirname(__DIR__, 4).'/contracts/fixtures/text-answer-cases.json';
        foreach (json_decode(file_get_contents($path), true)['cases'] as $case) {
            $this->assertSame($case['result'], TextAnswers::compare($case['activity'], $case['response']), $case['id']);
        }
    }
}
