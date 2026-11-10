<?php

namespace App\Services;

class TextAnswers
{
    public static function normalize(string $value): string
    {
        $value = strtr(mb_strtolower($value, 'UTF-8'), ['’' => "'", '‘' => "'", 'ʼ' => "'"]);
        $value = preg_replace('/[.,!?]/u', '', $value);

        return trim(preg_replace('/[\p{Z}\s]+/u', ' ', $value));
    }

    public static function compare(array $activity, ?string $response): array
    {
        $result = ['outcome' => 'unverified', 'message' => 'Prática registrada. Respostas abertas precisam de avaliação independente.', 'example' => null, 'rule_version' => null];
        if ($activity['type'] === 'information_question') {
            $correct = in_array($response, $activity['expected']['accepted'] ?? [], true);

            return array_replace($result, ['outcome' => $correct ? 'correct' : 'incorrect', 'message' => $correct ? 'Você identificou a informação. Isso registra reconhecimento nesta atividade.' : ($activity['modality'] === 'reading' ? 'Leia novamente e observe o sentido da mensagem.' : 'Ouça novamente e identifique a escolha comunicada.')]);
        }
        $rule = $activity['expected']['comparison'] ?? null;
        if (! $rule || $rule['version'] !== 'text-variants-v1' || $activity['modality'] !== 'writing') {
            return $result;
        }
        $result['rule_version'] = $rule['version'];
        $normalized = self::normalize($response ?? '');
        foreach ($rule['accepted'] as $variant) {
            if ($normalized !== '' && $normalized === self::normalize($variant)) {
                return array_replace($result, ['outcome' => 'correct', 'message' => 'Resposta aceita nesta atividade.']);
            }
        }
        $result['example'] = $rule['example'];
        foreach ($rule['corrections'] as $correction) {
            if ($normalized === self::normalize($correction['response'])) {
                return array_replace($result, ['outcome' => 'incorrect', 'message' => $correction['message']]);
            }
        }

        return array_replace($result, ['message' => 'Ainda não confirmei esta forma. Compare com o exemplo; outra resposta também pode ser válida.']);
    }
}
