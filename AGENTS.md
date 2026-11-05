# Instruções do repositório

Leia `docs/BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md` para invariantes pedagógicas e `docs/PLANO_DESENVOLVIMENTO_APP_IDIOMAS_v0.1.md` para arquitetura. Estado e pendências em `docs/milestones/`. F0/F1 constituem a fundação. O usuário autorizou continuar em F2; implementar a missão técnica e registrar a validação humana pendente, sem iniciar F3 automaticamente.

Um monorepo: `apps/platform` (Laravel/Vue), `apps/android` (Kotlin/Compose), `contracts`, `content` e `docs`. Preserve contratos compatíveis e separação por modalidade. Não converter apoio, autoavaliação ou falha técnica em domínio; não publicar rascunhos nem inventar revisões humanas.

Backend e web: `scripts/bootstrap.sh`; `scripts/compose run --rm app php artisan test`; `scripts/compose run --rm app vendor/bin/pint --test`; `scripts/compose run --rm --no-deps node npm run typecheck`; `scripts/compose run --rm --no-deps node npm run build`.

Android (SDK local e JDK 21): `cd apps/android && ./gradlew testDebugUnitTest assembleDebug lintDebug`. `scripts/check` reúne os checks. Sem SDK ou Docker, informe a limitação e não declare verificação passada.

Não versionar `.env`, tokens, senhas, chaves, gravações, dados pessoais ou instrumentos reservados. Contas só são criadas explicitamente pelo operador. Fixtures são sintéticas. Produção/deploy e coleta humana não fazem parte do bootstrap.
