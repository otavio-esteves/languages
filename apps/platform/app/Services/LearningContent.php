<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\DB;

class LearningContent
{
    public function manifest(User $user, string $id, int $version, bool $preview): array
    {
        $catalog = app(ContentCatalog::class);
        $package = collect($catalog->packages())->first(fn ($p) => $p['id'] === $id && $p['version'] === $version);
        abort_unless($package && ($package['status'] === 'published' || ($preview && $user->is_editor)), 404);
        $raw = null;
        foreach (glob(config('learning.content_path').'/*.json') ?: [] as $path) {
            $value = file_get_contents($path);
            $p = json_decode($value, true);
            if ($p['id'] === $id && $p['version'] === $version) {
                $raw = $value;
                break;
            }
        }
        abort_unless($raw !== null, 404);
        $hash = hash('sha256', $raw);
        if ($package['status'] === 'published' && DB::table('content_snapshots')->where('package_id', $id)->where('version', $version)->where('is_preview', false)->where('hash', '!=', $hash)->exists()) {
            abort(409, 'Uma publicação existente exige uma nova versão para ser alterada.');
        }
        $isPreview = $package['status'] !== 'published';
        $assets = [];
        foreach ($package['assets'] as $asset) {
            abort_unless($asset['status'] === 'ready', 409, 'A mídia deste rascunho ainda não está pronta.');
            $file = $this->assetFile($package, $asset['id']);
            $assets[] = ['id' => $asset['id'], 'sha256' => $asset['sha256'], 'bytes' => filesize($file)];
        }
        DB::table('content_snapshots')->insertOrIgnore(['hash' => $hash, 'package_id' => $id, 'version' => $version, 'is_preview' => $isPreview, 'document' => $raw, 'created_at' => now(), 'updated_at' => now()]);

        return ['schema_version' => '1.0.0', 'id' => $id, 'version' => $version, 'package_hash' => $hash, 'package_bytes' => strlen($raw), 'is_preview' => $isPreview, 'assets' => $assets];
    }

    public function snapshot(User $user, string $hash): object
    {
        abort_unless(preg_match('/^[a-f0-9]{64}$/', $hash), 404);
        $snapshot = DB::table('content_snapshots')->where('hash', $hash)->first();
        abort_unless($snapshot && (! $snapshot->is_preview || $user->is_editor), 404);

        return $snapshot;
    }

    public function assetFile(array $package, string $id): string
    {
        $asset = collect($package['assets'])->firstWhere('id', $id);
        abort_unless($asset && $asset['status'] === 'ready', 404);
        $base = realpath(config('learning.content_path')).DIRECTORY_SEPARATOR;
        $file = realpath($base.$asset['path']);
        abort_unless($file && str_starts_with($file, $base) && is_file($file), 404);
        abort_unless(hash_equals($asset['sha256'], hash_file('sha256', $file)), 409, 'A mídia mudou sem uma nova versão.');

        return $file;
    }
}
