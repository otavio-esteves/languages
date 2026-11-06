<?php

namespace App\Services;

use Opis\JsonSchema\Validator;
use RuntimeException;

class ContentCatalog
{
    public function packages(): array
    {
        $schema = json_decode(file_get_contents(config('learning.contracts_path').'/content-package.schema.json'));
        $packages = [];
        $seen = [];
        foreach (glob(config('learning.content_path').'/*.json') ?: [] as $path) {
            $raw = file_get_contents($path);
            $object = json_decode($raw, false, 512, JSON_THROW_ON_ERROR);
            if (! (new Validator)->validate($object, $schema)->isValid()) {
                throw new RuntimeException('Pacote curricular inválido: '.basename($path));
            }
            $package = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
            $key = json_encode([$package['id'], $package['version']]);
            if (isset($seen[$key])) {
                throw new RuntimeException('Versão duplicada do mesmo pacote.');
            }
            $seen[$key] = true;
            $this->assertReferences($package);
            if ($package['status'] === 'published') {
                $this->assertPublishable($package, dirname($path));
            }
            $packages[] = $package;
        }

        return $packages;
    }

    private function assertReferences(array $package): void
    {
        foreach (['targets', 'assets', 'activities'] as $group) {
            $ids = array_column($package[$group], 'id');
            if (count($ids) !== count(array_unique($ids))) {
                throw new RuntimeException('Identificador duplicado no pacote.');
            }
        }
        $targets = array_column($package['targets'], 'id');
        $assets = array_column($package['assets'], 'id');
        foreach ($package['activities'] as $activity) {
            $supportIds = array_column($activity['supports'], 'id');
            if (count($supportIds) !== count(array_unique($supportIds))) {
                throw new RuntimeException('Identificador de apoio duplicado.');
            }
            foreach ($activity['content']['glossary'] ?? [] as $entry) {
                $support = collect($activity['supports'])->firstWhere('id', $entry['support_id']);
                if (! $support || $support['kind'] !== 'hint' || $support['content'] !== $entry['meaning'] || ! in_array($activity['modality'], $support['affected_modalities'], true)) {
                    throw new RuntimeException('Significado sem apoio correspondente na modalidade.');
                }
            }
            if (isset($activity['expected']['comparison'])) {
                $example = $activity['expected']['comparison']['example'];
                $support = collect($activity['supports'])->firstWhere('id', 'comparison-example');
                if ($activity['modality'] !== 'writing' || ! $support || $support['kind'] !== 'solution' || $support['content'] !== $example || ! $support['reveals_answer'] || ! in_array('writing', $support['affected_modalities'], true)) {
                    throw new RuntimeException('Comparação escrita sem exposição do exemplo declarada.');
                }
            }
            if (array_diff($activity['target_ids'], $targets)) {
                throw new RuntimeException('Alvo inexistente na atividade.');
            }
            $assetId = $activity['content']['asset_id'] ?? null;
            if ($assetId && ! in_array($assetId, $assets, true)) {
                throw new RuntimeException('Mídia inexistente na atividade.');
            }
        }
    }

    private function assertPublishable(array $package, string $directory): void
    {
        if ($package['purpose'] !== 'training' || ! $package['provenance']['reviewer'] || ! $package['provenance']['reviewed_at'] || $package['provenance']['license'] === 'pending_owner_decision') {
            throw new RuntimeException('Publicação sem revisão ou autorização de uso.');
        }
        foreach ($package['assets'] as $asset) {
            $file = $asset['path'] ? realpath($directory.'/'.$asset['path']) : false;
            $base = realpath($directory).DIRECTORY_SEPARATOR;
            if ($asset['status'] !== 'ready' || ! $file || ! str_starts_with($file, $base) || ! hash_equals($asset['sha256'], hash_file('sha256', $file))) {
                throw new RuntimeException('Mídia publicada ausente ou inválida.');
            }
        }
    }

    public function textTraining(): array
    {
        $latest = [];
        foreach ($this->packages() as $package) {
            if ($package['purpose'] !== 'training' || $package['assets'] !== [] || array_diff(array_column($package['activities'], 'modality'), ['reading', 'writing'])) {
                continue;
            }
            if (! isset($latest[$package['id']]) || $package['version'] > $latest[$package['id']]['version']) {
                $latest[$package['id']] = $package;
            }
        }

        return array_values($latest);
    }

    public function published(): array
    {
        $latest = [];
        foreach ($this->packages() as $package) {
            if ($package['status'] !== 'published') {
                continue;
            }
            $id = $package['id'];
            if (! isset($latest[$id]) || $package['version'] > $latest[$id]['version']) {
                $latest[$id] = $package;
            }
        }
        ksort($latest, SORT_STRING);
        $items = array_values($latest);

        return ['schema_version' => '1.0.0', 'data' => array_map(fn ($p) => array_intersect_key($p, array_flip(['id', 'version', 'title', 'language_pair', 'capability_ids'])), $items)];
    }
}
