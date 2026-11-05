# Plataforma Idiomas

API Laravel 13 e painel Vue 3/TypeScript/Inertia 3 do monorepo. Consulte o [README principal](../../README.md) para instalação, execução e verificações via Docker.

- `routes/api.php`: autenticação de dispositivo, perfil e catálogo publicado.
- `routes/web.php`: sessão web e prévia editorial protegida.
- `app/Services/ContentCatalog.php`: validação de pacotes e regras de publicação.
- `tests/`: contratos compartilhados e cenários de autenticação/autorização.

Dependências exatas estão em `composer.lock` e `package-lock.json`. O projeto parte do skeleton oficial Laravel, com procedência registrada em `../../docs/decisions/skeleton-source.json`.
