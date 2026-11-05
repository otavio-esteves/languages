# Plataforma Laravel/Vue

Siga `../../AGENTS.md` e os planos em `../../docs/`. A stack autorizada usa PHP 8.4 em Docker e Laravel 13. Não instalar PHP no host nem trocar sua versão para atender instruções genéricas do skeleton. O bootstrap não depende de Laravel Boost.

Na raiz do monorepo: `scripts/compose run --rm app php artisan test`, `scripts/compose run --rm app vendor/bin/pint --test`, `scripts/compose run --rm --no-deps node npm run typecheck` e `scripts/compose run --rm --no-deps node npm run build`.

Use PostgreSQL `idiomas_test` nos testes, nunca dados reais. Mantenha API e controladores web sobre os mesmos serviços. Catálogo de estudo contém somente publicações revisadas; prévia de rascunho exige permissão editorial. Não criar contas padrão, publicar conteúdo ou atribuir revisão humana fictícia.
