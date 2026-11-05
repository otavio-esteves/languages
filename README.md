# Idiomas

Fundação de um aplicativo de inglês para adultos falantes de português brasileiro. Um monorepo reúne Laravel/API e Vue em `apps/platform`, Android/Kotlin em `apps/android` e contratos compartilhados em `contracts`. Cada aplicação tem build e release independentes.

A fundação F0/F1 evoluiu para a implementação técnica de F2: uma missão com áudio, escrita, fala local, apoios, retomada e sincronização básica no Android. O painel possui uma prévia interativa para revisão. O catálogo de alunos permanece vazio enquanto o conteúdo estiver em rascunho. Revisão humana, teste formativo e avaliação humana operacional ainda estão pendentes; F2 não está pedagogicamente concluída.

## Executar a plataforma local

Requisitos: Docker Engine com Compose v2 ou posterior, Python 3 e acesso à rede para baixar dependências. Não é necessário instalar PHP, Composer ou Node no host.

```bash
scripts/bootstrap.sh
```

O script cria `.env` locais se ausentes, gera uma senha aleatória para o PostgreSQL local e uma chave da aplicação, instala dependências, executa migrações, compila a web e inicia os serviços. Preserva a configuração existente, acrescenta a senha exclusiva dos testes se ausente e não cria usuários. A primeira construção da imagem PHP pode demorar. O servidor de desenvolvimento fica em `http://localhost:8080` (somente loopback); não usar essa configuração em produção. `APP_PORT` na `.env` da raiz permite alterar a porta.

Criar uma conta explicitamente, escolhendo nome, e-mail e senha nos prompts:

```bash
scripts/compose run --rm app php artisan app:create-user --editor
```

Omitir `--editor` cria uma conta sem acesso à prévia. Não há credenciais padrão. A conta com permissão editorial pode entrar em `/login` e consultar os rascunhos em `/editorial` e clicar em **Abrir prévia interativa**; a prévia não aprova nem publica conteúdo.

```bash
scripts/compose logs --tail=50 app
scripts/compose stop
```

`stop` preserva o banco. O bootstrap garante o banco e o usuário exclusivos `idiomas_test`, com senha própria. Antes de qualquer limpeza, os testes conferem o ambiente, o banco e o usuário da conexão real; o usuário de testes não pode apagar as tabelas de desenvolvimento. Nunca usar dados reais nos testes. As filas usam PostgreSQL e o agendador remove tokens expirados. E-mail, upload de voz e produção permanecem sem configuração.

## Android

Abra `apps/android` no Android Studio ou use um JDK 21 completo e SDK com plataforma 36 e build-tools 35.0.0. Configure `sdk.dir` em `apps/android/local.properties` ou `ANDROID_HOME`; esse caminho não entra no Git. Neste ambiente, o JDK completo está em `/opt/android-studio/jbr`.

```bash
cd apps/android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Se o Java padrão for apenas JRE, defina `JAVA_HOME` para um JDK completo antes do comando. O APK de desenvolvimento fica em `app/build/outputs/apk/debug/app-debug.apk`. O build debug usa `http://10.0.2.2:8080/api/v1/`, endereço do host visto pelo emulador Android. O HTTP é permitido apenas no debug para os hosts locais declarados; release bloqueia HTTP e não tem endereço de produção configurado. Não distribuir release antes dessa configuração e da assinatura apropriada.

Entre com uma conta criada na plataforma. A tela lê o catálogo pela API, persiste-o com Room e observa o banco local. Cache é isolado por conta; falha de rede preserva o catálogo já baixado, erro 401 encerra a sessão e logout remove o cache daquela conta. O token é protegido pelo Android Keystore e recuperado após encerrar o processo; continua expirando em 24 horas no servidor. Após baixar um pacote, é possível praticar offline, retomar posição e resposta escrita e sincronizar depois. Logout preserva a prática e a fila daquela conta para uma nova entrada; outra conta não pode acessá-las. A exclusão local exige confirmação e descarta também os registros ainda não enviados.

Editores podem baixar a prévia de treino na seção editorial do Android. O pacote e seus áudios são verificados por SHA-256 antes da ativação. Apoios são registrados antes da revelação; resposta, posição e pendência são persistidas numa única transação. A voz só é gravada após permissão explícita e permanece no armazenamento privado do aparelho; não há upload nem avaliação oral automática. A sincronização ocorre com rede disponível e sessão válida. Uma conta expirada precisa entrar novamente, sem perder a fila. O uso validado nesta fase é em um aparelho por pessoa.

## Verificações

Após o bootstrap:

```bash
scripts/compose run --rm app vendor/bin/pint --test
scripts/compose run --rm app php artisan test
scripts/compose run --rm --no-deps node npm run typecheck
scripts/compose run --rm --no-deps node npm run build
```

`scripts/check` reúne as verificações PHP/web e Android, exigindo ambos os ambientes. A CI executa os dois projetos em toda alteração nesta primeira entrega, inclusive as fixtures de consumo em Kotlin. Os testes PHP verificam contratos, autenticação, expiração/revogação, isolamento entre contas, permissões editoriais e exclusão de rascunhos do catálogo. Os testes Kotlin verificam consumo do contrato, cache, indisponibilidade, sessão inválida e resposta tardia após troca de conta. Os testes instrumentados verificam persistência, migração Room 1→2, transação da resposta/fila, repetição após perda de confirmação, pacote corrompido, isolamento e proteção da sessão em um emulador/dispositivo de teste: `cd apps/android && ./gradlew connectedDebugAndroidTest`. Não é executado pelo job de CI sem emulador. Nenhum desses testes demonstra eficácia pedagógica.

## Documentação e conteúdo

- [Base pedagógica](docs/BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md)
- [Plano de desenvolvimento](docs/PLANO_DESENVOLVIMENTO_APP_IDIOMAS_v0.1.md)
- [Decisão de arquitetura](docs/decisions/0001-fundacao.md)
- [Especificação e pendências F0](docs/milestones/F0.md)
- [Entrega e verificações F1](docs/milestones/F1.md)
- [Implementação, testes e pendências F2](docs/milestones/F2.md)
- [Versões resolvidas](docs/decisions/versions.json)
- [API implementada](contracts/openapi.yaml)

JSON Schema 2020-12 descreve pacotes, tentativas, exposições, evidências e catálogo. `contracts/fixtures` contém apenas dados sintéticos. Os exemplos de política são especificação de aceite; o motor de revisão ainda não foi implementado. `content/pt-BR_en/missions` contém uma missão de treino e uma variante pública de avaliação, ambas rascunhos assistidos por IA; há áudio sintético local para prévia, com revisão linguística, inteligibilidade e direitos de publicação ainda pendentes. A variante pública não pode ser usada como instrumento reservado de pesquisa.

Não colocar dados pessoais, áudios de alunos, segredos, chaves de assinatura ou instrumentos reservados no repositório. A revisão humana e o teste formativo não bloqueiam a fundação técnica, mas precisam ocorrer antes de publicar conteúdo e concluir F2.
