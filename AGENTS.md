# Instruções do repositório

Leia `docs/BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md` para invariantes pedagógicas e `docs/PLANO_DESENVOLVIMENTO_APP_IDIOMAS_v0.1.md` para arquitetura. Estado e pendências em `docs/milestones/`. F0/F1 constituem a fundação. O usuário autorizou continuar em F2; implementar a missão técnica e registrar a validação humana pendente, sem iniciar F3 automaticamente.

Direção atual de produto: interface minimalista, trilha em fases e foco em leitura e escrita. Áudio e voz foram adiados pelo usuário; preservar histórico e contratos sem reintroduzi-los no fluxo atual. A trilha organiza atividades da missão, sem declarar domínio ou conclusão de F2/F3. A interface exibe um dia por vez (Dia 1, Dia 2...), com setas no topo e seletor rolável ao tocar no dia. O nível ao lado do dia é referência editorial do conteúdo, não certificação do aluno. A conclusão é registrada por lição, sem confundir navegação com progresso e sem espera obrigatória de calendário. Os temas claro/escuro e o visual com cards arredondados seguem a direção solicitada pelo usuário. A versão atual progride de palavras para pedidos curtos e frases/contrações. Comparações automáticas usam variantes e erros conhecidos versionados; respostas não previstas exigem abstenção. Significados consultados e exemplos de correção são apoios registrados, sem promover evidência independente.

Um monorepo: `apps/platform` (Laravel/Vue), `apps/android` (Kotlin/Compose), `contracts`, `content` e `docs`. Preserve contratos compatíveis e separação por modalidade. Não converter apoio, autoavaliação ou falha técnica em domínio; não publicar rascunhos nem inventar revisões humanas.

Backend e web: `scripts/bootstrap.sh`; `scripts/compose run --rm app php artisan test`; `scripts/compose run --rm app vendor/bin/pint --test`; `scripts/compose run --rm --no-deps node npm run typecheck`; `scripts/compose run --rm --no-deps node npm run build`.

Android (SDK local e JDK 21): `cd apps/android && ./gradlew testDebugUnitTest assembleDebug lintDebug`. `scripts/check` reúne os checks. Sem SDK ou Docker, informe a limitação e não declare verificação passada.

Não versionar `.env`, tokens, senhas, chaves, gravações, dados pessoais ou instrumentos reservados. Contas só são criadas explicitamente pelo operador. Fixtures são sintéticas. Produção/deploy e coleta humana não fazem parte do bootstrap.
