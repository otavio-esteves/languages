# ADR 0001 — fundação do produto

Data: 2026-11-04. Estado: decisão de implementação para F0/F1.

Um monorepo contém Android em `apps/android`, Laravel/web em `apps/platform`, contratos e fixtures em `contracts`, currículo autorizado em `content` e os planos em `docs`. Builds e releases são independentes; não criar repositórios aninhados. O backend preserva compatibilidade com clientes móveis suportados.

Laravel 13 e PHP 8.4 atendem API REST e painel Vue 3/TypeScript/Inertia 3. A fundação usa o skeleton oficial Laravel e uma interface Vue mínima, sem importar funcionalidades de equipes, passkeys ou construtor de cursos. Componentes editoriais avançados e a adoção de uma biblioteca visual ficam para uma necessidade observada. PostgreSQL é o banco da aplicação e das verificações HTTP. Docker Compose isola PHP, PostgreSQL, worker e scheduler. Node é ferramenta de build, sem SSR.

Android usa Kotlin/Compose, ViewModel/StateFlow, repositório, cliente HTTP e Room como fonte do catálogo. Em F1 a autenticação usa token de dispositivo de duração finita, mantido somente em memória; fechar o processo exige novo login, sem gravar segredo em texto aberto. Não há player nem sincronização de tentativas em F1. O cache do catálogo é isolado por conta. O SDK mínimo inicial é 26 como hipótese técnica para o protótipo; deve ser confrontado com os aparelhos reais antes de F4, sem alegar cobertura do público.

Os quatro esquemas de aprendizagem e o esquema de catálogo são versionados em JSON Schema 2020-12; OpenAPI descreve apenas os endpoints efetivamente implementados em F1. Endpoints de tentativas, exposições, áudio e avaliação do plano permanecem futuros. Fixtures de avaliação são sintéticas; a variante pública de missão não pode servir como banco reservado de pesquisa.

A missão está em rascunho assistido por IA: autoria, autorização de uso, revisão humana, áudio final e teste com alunos continuam pendentes. Dados pessoais e instrumentos reservados ficam fora do Git. O catálogo de estudo retorna somente conteúdo publicado, com revisão e mídia verificadas; a prévia editorial de rascunhos exige permissão específica. Nenhum seed cria usuário, senha ou revisão fictícia.

A política v0 é especificação com exemplos de aceite, não motor executado. A implementação do agendamento pertence a F3. A validação humana de F0 não bloqueia o bootstrap técnico F1 e permanece condição para publicar conteúdo e concluir F2.
