<?php

namespace Tests\Unit;

use App\Services\TextAnswers;
use PHPUnit\Framework\TestCase;

class ExpandedCurriculumTest extends TestCase
{
    public function test_expanded_draft_has_complete_days_and_fits_the_offline_reader(): void
    {
        $root = dirname(__DIR__, 4);
        $raw = file_get_contents($root.'/content/pt-BR_en/missions/order-a-drink.v5.json');
        $package = json_decode($raw, true);
        $this->assertLessThanOrEqual(2 * 1024 * 1024, strlen($raw));
        $this->assertSame('draft', $package['status']);
        $this->assertNull($package['provenance']['reviewer']);
        $this->assertSame([], $package['assets']);
        $days = [];
        foreach ($package['activities'] as $activity) {
            $days[$activity['content']['phase_id']][] = $activity;
        }
        $this->assertCount(53, $days);
        $this->assertSame('day-01', array_key_first($days));
        $this->assertSame('day-53', array_key_last($days));
        $this->assertCount(count($package['activities']), array_unique(array_column($package['activities'], 'id')));
        foreach ($days as $id => $activities) {
            $this->assertCount(24, $activities, $id);
            $seconds = array_sum(array_column(array_column($activities, 'content'), 'estimated_seconds'));
            $this->assertGreaterThanOrEqual(600, $seconds, $id);
            $this->assertLessThanOrEqual(900, $seconds, $id);
            foreach (array_chunk($activities, 8) as $lesson) {
                $this->assertSame('explanation', $lesson[0]['type']);
                $this->assertSame('communicative_task', $lesson[6]['type']);
                $this->assertSame('summary', $lesson[7]['type']);
                $this->assertCount(4, array_filter($lesson, fn ($a) => $a['modality'] === 'writing'));
                $this->assertCount(2, array_filter($lesson, fn ($a) => $a['type'] === 'information_question'));
                $this->assertSame(['min' => 10, 'max' => 15], $lesson[0]['content']['day_estimated_minutes']);
                // The novel task's expected English is absent from the introductory model.
                $this->assertStringNotContainsString($lesson[6]['expected']['comparison']['example'], $lesson[0]['content']['study_text']);
                $hint = array_values(array_filter($lesson[6]['supports'], fn ($s) => $s['id'] === 'task-vocabulary'));
                $this->assertCount(1, $hint, 'A nova mensagem oferece vocabulário consultável, inclusive quando introduz um termo novo.');
                $this->assertTrue($hint[0]['reveals_answer']);
                $this->assertSame(['writing'], $hint[0]['affected_modalities']);
            }
        }
    }

    public function test_all_authored_variants_and_known_errors_keep_unknown_answers_unverified(): void
    {
        $root = dirname(__DIR__, 4);
        $package = json_decode(file_get_contents($root.'/content/pt-BR_en/missions/order-a-drink.v5.json'), true);
        foreach ($package['activities'] as $activity) {
            if ($activity['type'] === 'information_question') {
                $this->assertContains($activity['expected']['accepted'][0], $activity['content']['options']);
                $this->assertCount(3, array_unique($activity['content']['options']));
            }
            $rule = $activity['expected']['comparison'] ?? null;
            if (! $rule) {
                continue;
            }
            $this->assertContains($rule['example'], $rule['accepted']);
            foreach ($rule['accepted'] as $variant) {
                $this->assertSame('correct', TextAnswers::compare($activity, $variant)['outcome'], $activity['id']);
            }
            foreach ($rule['corrections'] as $correction) {
                $this->assertSame('incorrect', TextAnswers::compare($activity, $correction['response'])['outcome'], $activity['id']);
            }
            $this->assertSame('unverified', TextAnswers::compare($activity, 'An alternative outside the authored variants.')['outcome']);
        }
    }
}
