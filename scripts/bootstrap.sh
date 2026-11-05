#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$TASK_ROOT"
python3 - <<'PY'
from pathlib import Path
import os, secrets
root = Path('.env')
if not root.exists():
    root.write_text(f'LOCAL_UID={os.getuid()}\nLOCAL_GID={os.getgid()}\nDB_PASSWORD={secrets.token_hex(24)}\nAPP_PORT=8080\n')
    root.chmod(0o600)
if not any(line.startswith('DB_PASSWORD=') and line.partition('=')[2].strip() for line in root.read_text().splitlines()):
    raise SystemExit('Defina DB_PASSWORD em .env; não use uma senha de produção.')
if not any(line.startswith('DB_TEST_PASSWORD=') and line.partition('=')[2].strip() for line in root.read_text().splitlines()):
    with root.open('a') as stream:
        stream.write('\nDB_TEST_PASSWORD='+secrets.token_hex(24)+'\n')
app = Path('apps/platform/.env')
if not app.exists():
    app.write_text(Path('apps/platform/.env.example').read_text())
    app.chmod(0o600)
PY
scripts/compose build app
scripts/compose up -d --wait db
scripts/compose exec -T db bash /docker-entrypoint-initdb.d/10-test-db.sh
scripts/compose run --rm app composer install --no-interaction --prefer-dist
if python3 - <<'PY'
from pathlib import Path
raise SystemExit(0 if 'APP_KEY=\n' in Path('apps/platform/.env').read_text() else 1)
PY
then
    scripts/compose run --rm app php artisan key:generate --no-interaction
fi
scripts/compose run --rm app php artisan migrate --no-interaction
if [ -f apps/platform/package-lock.json ]; then
    scripts/compose run --rm --no-deps node npm ci --ignore-scripts
else
    scripts/compose run --rm --no-deps node npm install --ignore-scripts
fi
scripts/compose run --rm --no-deps node npm run build
scripts/compose up -d app worker scheduler
