# Aplicativo de idiomas — plano de desenvolvimento v0.1

**Data:** 4 de novembro de 2026  
**Status:** proposta técnica para aprovação e implementação incremental; nenhum componente descrito aqui foi implementado por este documento.  
**Base de produto:** `BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md`, fornecida pelo usuário.  
**Decisão recomendada:** Android nativo em Kotlin + Jetpack Compose; Laravel como backend compartilhado e aplicação web; Vue + TypeScript + Inertia na interface web interativa.  
**Repositório:** um único repositório Git (monorepo), com Android/Kotlin em `apps/android` e Laravel/web em `apps/platform`; builds e publicações independentes.  
**Primeiro público:** adultos falantes de português brasileiro aprendendo inglês, mantendo o recorte como hipótese revisável. [P, §1 e §12]

**Revisão de planejamento:** 4 de novembro de 2026. Define política operacional v0, protocolo exploratório, capacidade humana, cobertura mínima de fluência e fronteiras dos marcos. Nenhuma dessas definições representa implementação ou validação concluída.

**Revisão final antes do bootstrap:** consistência entre marcos e prompt, caminhos atuais, registro de exposições sem resposta e checks dos contratos revisados. Laravel/PHP e o starter kit Vue foram reconferidos nas fontes oficiais T5–T6; essa consulta não substitui o build conjunto das versões exatas em F1 nem uma nova revisão bibliográfica da base.

**Estado da execução:** a especificação e a fundação técnica estão registradas em [F0](milestones/F0.md) e [F1](milestones/F1.md); a implementação técnica da primeira missão e as pendências de validação estão em [F2](milestones/F2.md). Este documento continua como referência de planejamento; não certifica conclusão dos requisitos.

## 1. Decisão e limites

Manter Kotlin e Laravel. Não desenvolver dois cursos independentes nem duas plataformas completas ao mesmo tempo. Construir primeiro o ciclo de aprendizagem no Android, apoiado por API e painel editorial Laravel. Expandir o ambiente de estudo no navegador depois de validar o ciclo central.

A base pedagógica não decidiu frameworks, infraestrutura, orçamento ou arquitetura. As decisões técnicas, os marcos e os limites abaixo são propostas novas. Os requisitos PED-001 a PED-062 continuam pertencendo à base; este plano não os substitui nem apresenta as escolhas de engenharia como resultados científicos. [P, abertura, §7 e §12]

A contagem das prioridades da base é de **49 requisitos P0 e 13 P1**. P0 não significa 49 funcionalidades independentes: várias condições são implementadas pelo mesmo componente ou por um processo humano no piloto. Todos os P0 precisam de cobertura verificável antes de tratar o piloto como um MVP pedagogicamente íntegro. [P, §7]

### Alternativas consideradas

| Opção | Quando eu a escolheria | Decisão neste projeto |
|---|---|---|
| Kotlin/Compose + Laravel | Android como primeira experiência e evolução independente da web | Escolhida |
| Flutter + Laravel | Android e iOS com prioridade equivalente já na primeira entrega | Alternativa válida; não há requisito atual de iOS que justifique a troca |
| Web responsiva/PWA + Laravel, antes do Android | Validação inicial exclusivamente pelo navegador e distribuição por link como prioridade | Caminho possível, mas diferente do Android-first proposto |
| Kotlin Multiplatform | Compartilhamento de código entre Android e iOS entrar no escopo concreto | Reavaliar nesse momento; não introduzir por antecipação |

Flutter oferece múltiplos destinos a partir de uma base de código; isso não torna obrigatória a escolha de Flutter para todo o site público. Sua documentação diferencia aplicações interativas de páginas documentais. KMP permite compartilhar lógica e, com Compose Multiplatform, também interface entre plataformas compatíveis. Nenhuma dessas opções elimina por si só a necessidade de backend, autoria de conteúdo e testes por plataforma. [T10–T12]

Estas comparações são decisões de adequação ao escopo, não resultados de benchmarks. Não foi medido desempenho relativo neste projeto. Kotlin não garante sozinho que o aplicativo será mais rápido, leve ou pedagogicamente melhor.

## 2. Escopo da primeira entrega

O produto inicial deve incluir uma entrada curta com condições de uso, diagnóstico opcional, missões comunicativas, leitura e escuta contextualizadas, recuperação escrita, produção oral gravada, feedback, revisão distribuída com orçamento de esforço, progresso por capacidades e avaliação posterior. [P, §7 e §12]

A construção terá três escalas: **uma missão completa**, depois **três missões conectadas**, depois **doze missões revisadas para o piloto**. Doze é uma escolha de escopo dentro da faixa sugerida pela base, não uma definição de curso A1 nem uma quantidade cientificamente ótima. [P, §12]

O MVP poderá usar avaliação humana em produções abertas e regras simples de agendamento. Não exigirá chatbot generativo, nota automática de pronúncia, múltiplos idiomas, iOS, ranking global, rede social, avatares complexos ou um construtor visual universal de cursos. Conteúdo essencial e feedback fechado devem funcionar sem serviços pagos de IA. [P, PED-054, PED-056 e §12]

A produção oral não será removida para simplificar a implementação. Sem um avaliador validado disponível, o sistema registra a prática e a eventual autoavaliação, mas mantém a capacidade correspondente como não verificada. O aluno pode continuar o percurso sem receber uma certificação fictícia. [P, PED-019, PED-028, PED-043, PED-056]

## 3. Stack proposta

| Camada | Escolha | Uso previsto |
|---|---|---|
| Android | Kotlin + Jetpack Compose | Interface e execução local das atividades |
| Estado Android | ViewModel, Coroutines e Flow | Estado observável e separação entre interface e dados |
| Dados Android | Room | Conteúdo instalado, sessões, tentativas e fila de sincronização |
| Trabalho persistente | WorkManager | Reenvio de operações pendentes quando as condições permitirem |
| Reprodução | Media3/ExoPlayer | Áudios de exemplo e reprodução da própria gravação |
| Gravação | MediaRecorder, inicialmente | Gravação curta, explícita e em primeiro plano |
| Backend | Laravel 13 + PHP 8.4 | Domínio, API, web, publicação e avaliação |
| Interface web | Vue 3 + TypeScript + Inertia 3 | Painel editorial, conta e futuro ambiente de estudo |
| Componentes web | Tailwind e shadcn-vue do starter kit | Base visual a adaptar, sem copiar mecanicamente para Android |
| Páginas públicas simples | Blade | Apresentação, ajuda e páginas documentais, sem exigir SSR com Node |
| Banco central | PostgreSQL em versão suportada | Dados relacionais, versões, tentativas e projeções |
| Autenticação | Sessão web; Sanctum no Android | Cookies/CSRF no navegador e tokens de dispositivo na API |
| Filas | Driver de banco inicialmente | Publicação, processamento assíncrono e exclusões |
| Arquivos | Disco privado no desenvolvimento; armazenamento de objetos privado em produção | Conteúdo e gravações com permissões distintas |
| Ambiente | Docker Compose | Serviços locais reproduzíveis do backend |
| Contratos | OpenAPI + JSON Schema + exemplos de teste | API, pacotes de conteúdo e compatibilidade entre clientes |

A documentação Android recomenda Compose e separação entre interface e dados; seu guia offline-first descreve fonte local, repositórios e sincronização persistente. Media3 e MediaRecorder cobrem reprodução e captura, respectivamente. [T1–T4]

Em 4 de novembro de 2026, a documentação consultada apresenta Laravel 13, com PHP 8.3–8.5 suportado. A escolha de PHP 8.4 aqui é uma proposta compatível, não uma afirmação de que seja a versão mais recente. O starter kit Vue documenta Vue 3, Inertia 3, TypeScript e componentes correspondentes. [T5–T6]

As versões exatas de Kotlin, Gradle, Android Gradle Plugin, SDK, bibliotecas Android, PostgreSQL e ferramentas de teste devem ser verificadas em conjunto no bootstrap e registradas em arquivos de versão e lockfiles. Não inventar números de versão nem usar tags flutuantes `latest` em builds reproduzíveis. O SDK mínimo será decidido pela matriz de aparelhos do piloto e pelas dependências, não por palpite sobre o público.

### Por que Inertia/Vue e não somente Livewire?

Livewire é uma opção legítima para aplicações Laravel; a documentação oficial apresenta tanto Livewire quanto Inertia como caminhos suportados. [T7]

Para este projeto, proponho concentrar a interface interativa da web em Vue porque o player terá áudio, gravação, respostas em andamento e transições locais. Isso é uma escolha de organização e manutenção, não uma limitação absoluta do Livewire. Não enviar uma requisição para cada tecla, reprodução ou mudança local de etapa.

Não iniciar React e Vue em paralelo. Também não criar um painel Livewire e um player Vue sem uma necessidade concreta. O starter kit escolhido deve servir ao painel e à futura experiência web. Blade pode continuar sendo usado nas páginas públicas simples.

Inertia não é a API do Android. A web terá rotas e controladores próprios, e o aplicativo usará `/api/v1`. Ambos chamarão os mesmos serviços de aplicação e domínio. [T7]

## 4. Arquitetura: um monólito modular, dois clientes

```text
Android: Compose → ViewModels → repositórios → Room/arquivos
                                           ↕ sincronização REST
Web: Vue/Inertia → controladores web      controladores API
                              \          /
                         Serviços Laravel
                  currículo · sessões · evidências
                  revisão · progressão · avaliação
                  publicação · privacidade · relatórios
                              |
               PostgreSQL + arquivos privados + worker
```

Um único projeto Laravel será responsável pelo backend e pela aplicação web. Organizar o código por responsabilidades reconhecíveis, sem microserviços, Kubernetes, barramento distribuído ou event sourcing completo no MVP.

### Responsabilidades

| Componente | Responsabilidade | Não deve fazer |
|---|---|---|
| Currículo e publicação | Capacidades, pré-requisitos, versões, conteúdo e rubricas | Declarar aprendizagem por publicação ou conclusão de telas |
| Planejamento de sessão | Selecionar atividades segundo contexto, objetivo e orçamento | Obrigar a zerar a revisão para continuar |
| Avaliação | Produzir decisões justificadas, inclusive abstenção | Converter qualquer texto inesperado em erro |
| Evidências | Registrar o que foi observado, com condições e procedência | Somar modalidades distintas em um domínio global fictício |
| Progressão | Diferenciar percurso, demonstração inicial e retenção | Usar pontos como substituto de capacidade |
| Revisão | Priorizar recuperações e alterar intervenção | Repetir indefinidamente a mesma pergunta |
| Clientes | Apresentar tarefas, capturar respostas e manter estado local | Inventar critérios de aprovação próprios |
| Relatórios | Mostrar experiência e aprendizagem separadamente | Usar tempo de tela como prova de eficácia |

### O que será compartilhado

Compartilhar contratos, identificadores, conteúdo, regras declarativas, rubricas, parâmetros e casos de teste. Não prometer compartilhamento automático do código PHP com Kotlin ou Vue.

O Laravel manterá a política canônica de avaliação e progressão. O Android executará apenas o subconjunto local necessário para prática offline, feedback fechado e continuidade da sessão. Esse subconjunto terá versão explícita e testes com entradas e saídas iguais às do servidor.

Estados locais ainda não sincronizados serão identificados como provisórios. O servidor consolida a visão entre dispositivos sem apagar tentativas legítimas. Não centralizar toda interação no servidor, pois isso impediria o funcionamento offline proposto; também não manter dois motores pedagógicos independentes.

## 5. Modelo de domínio e dados

Os nomes são propostas de implementação, não um compromisso de criar uma tabela para cada conceito imediatamente.

| Grupo | Entidades iniciais | Informações essenciais |
|---|---|---|
| Identidade | users, devices, learner_profiles, privacy_preferences | Identidade, condições de uso, objetivos, consentimentos pertinentes |
| Currículo | courses, capabilities, prerequisites, linguistic_targets | Par linguístico, modalidade, função comunicativa, sentido específico |
| Conteúdo | missions, mission_versions, activity_versions, assets | Objetivo, material, apoios, procedência, revisão e versão |
| Critérios | rubric_versions, policy_versions | Critérios de tarefa, avanço e agendamento com histórico |
| Execução | learning_sessions, attempts | Contexto, atividade exata, resposta, apoios e condição técnica |
| Exposição | exposure_events | Modelo, solução, transcrição ou dica acessados, com alvos, modalidades afetadas, versão, sessão e ordem local, mesmo sem resposta posterior |
| Avaliação | assessments, assessment_revisions, learning_evidence | Decisão por dimensão, origem, justificativa e vínculo com tentativa |
| Projeções | capability_progress, review_items | Visão atual recalculável, sem descartar evidências anteriores |
| Operação | editorial_reviews, evaluation_assignments, privacy_requests | Publicação, fila humana e operações de dados |

Uma expressão lexical deve poder ser identificada pelo sentido e pelo uso: reconhecer `bank` como instituição não comprova conhecimento de outro sentido. Leitura, escuta, escrita e fala mantêm evidências próprias. Uma atividade pode observar múltiplos alvos, mas somente os alvos efetivamente demonstrados devem atualizar o histórico. [P, PED-003, PED-010, PED-032, PED-036]

### Tentativa e evidência não são a mesma coisa

A tentativa registra um acontecimento. A evidência resulta da interpretação desse acontecimento sob uma rubrica e uma versão de avaliador. Uma produção pode cumprir o propósito comunicativo sem demonstrar a expressão específica em treino. Uma falha técnica pode impedir qualquer conclusão linguística.

Uma tentativa deve carregar, quando pertinente: identificador global, sessão, dispositivo, sequência local, atividade e versão, alvos, modalidade, tempo observado, apoios utilizados, momento de exposição à resposta, resposta ou referência ao áudio, e qualidade técnica. O servidor acrescenta recebimento, validação e avaliação. Não aceitar um `score` enviado pelo cliente como verdade canônica.

Registrar exposições relevantes como eventos próprios no momento em que o apoio ou modelo é apresentado, inclusive quando o aluno abandona a atividade sem responder. Cada evento identifica o conteúdo mostrado, os alvos e as modalidades que podem ter sido apoiadas; uma transcrição durante escuta deve afetar a interpretação daquela tarefa auditiva, sem criar evidência de leitura automaticamente. O mapeamento dos apoios às modalidades fica no conteúdo versionado. Vincular a tentativa aos eventos pertinentes, sem duplicar uma mesma exposição durante sincronização. Eventos sem resposta não são tentativas erradas nem evidência de recuperação.

Evitar um único `status` que misture dimensões. Distinguir pelo menos: resultado da tarefa, forma-alvo demonstrada, apoio utilizado, condição técnica e origem da avaliação. `self_report`, `human_review` e `automatic_rule` não são fontes equivalentes.

O histórico deve ser auditável dentro dos prazos de retenção definidos, mas não eternamente imutável contra pedidos de exclusão. Redação de respostas, remoção de áudios e exclusão de conta precisam ser possíveis sem que a arquitetura imponha guardar tudo para sempre.

## 6. Contratos, API e conteúdo versionado

Definir primeiro os esquemas; depois os renderizadores. Uma missão descreve seu propósito, capacidades, condições, pré-requisitos, atividades, alvos, apoios e critérios. Seu conteúdo é dado versionado, não código arbitrário executado remotamente.

### Contratos a criar

| Arquivo | Função |
|---|---|
| `contracts/openapi.yaml` | Operações HTTP e respostas de erro |
| `contracts/content-package.schema.json` | Manifesto, versões, atividades e arquivos do pacote |
| `contracts/attempt-event.schema.json` | Registro de tentativa e condições observadas |
| `contracts/exposure-event.schema.json` | Registro de exposição ou apoio mesmo sem envio de resposta |
| `contracts/evidence.schema.json` | Decisão por modalidade e dimensão |
| `contracts/fixtures/` | Cenários de entrada/saída compartilhados por PHP e Kotlin |

Cada pacote terá `schema_version`, versão de publicação, identificadores estáveis, compatibilidade mínima do leitor e hashes dos arquivos. O servidor não altera silenciosamente a versão à qual uma tentativa está vinculada. Atualizações devem preservar sessões em curso ou migrá-las explicitamente.

### Superfície inicial de API

| Operação proposta | Uso |
|---|---|
| `POST /api/v1/auth/token` | Autenticar e emitir token de dispositivo |
| `DELETE /api/v1/auth/token` | Revogar token atual |
| `GET /api/v1/me` | Perfil e preferências |
| `GET /api/v1/catalog` | Catálogo disponível para a conta |
| `GET /api/v1/content-packages/{id}/manifest` | Versão e arquivos necessários |
| `POST /api/v1/session-plans` | Plano online de prática, respeitando condições de uso |
| `POST /api/v1/attempts/batch` | Receber tentativas com idempotência por evento |
| `POST /api/v1/exposures/batch` | Receber exposições e apoios com idempotência, inclusive de atividades abandonadas |
| `GET /api/v1/sync?cursor=...` | Baixar mudanças consolidadas por cursor opaco |
| `POST /api/v1/audio-uploads` | Autorizar envio privado, somente quando solicitado |
| `GET /api/v1/progress` | Visão por capacidades e origem das evidências |
| `POST /api/v1/evaluation-sessions` | Disponibilizar avaliação separada do treino |
| `POST /api/v1/privacy-requests` | Solicitações de exportação/exclusão |

Esses caminhos são proposta de contrato, não endpoints existentes. Definir limites de lote, paginação, autenticação, autorização e erros antes de gerar clientes. A API deduz o usuário autenticado; não confia em um `user_id` livremente informado.

Preservar compatibilidade com a versão móvel anterior durante atualizações graduais. Novos tipos de atividade só podem ser publicados para clientes que declarem suporte. Uma incompatibilidade de conteúdo deve gerar mensagem clara, nunca travar ou atribuir erro ao aluno.

## 7. Motor de atividades e primeira missão

Começar com poucos renderizadores configuráveis, sem fazer uma tela totalmente nova para cada exercício.

| Tipo | Uso | Limite da evidência |
|---|---|---|
| Diálogo ou texto contínuo | Compreensão contextual, com apoios disponíveis | Exposição não comprova recuperação |
| Questão sobre informação | Identificar sentido ou informação central | Reconhecimento não equivale a produção |
| Recuperação escrita | Formular resposta sem modelo visível | Dica ou resposta recém-vista muda a interpretação |
| Produção oral gravada | Formular mensagem e ouvir a própria voz | Gravar não é, por si, demonstrar capacidade |
| Tarefa comunicativa com variação | Obter, transmitir ou esclarecer informação | Deve avaliar objetivo, não somente coincidência literal |

O registro de ajuda deve ocorrer quando ela é acessada, não ser inferido depois. A transcrição não pode aparecer antes de uma avaliação de escuta independente. Controles de acessibilidade devem preservar uma alternativa de estudo sem registrar a habilidade errada. [P, PED-015, PED-025, PED-051]

### Missão vertical de referência

**Pedir uma bebida e esclarecer uma informação.** Esta missão é adotada do exemplo da base, não é uma ordem curricular definitiva. [P, §4]

Apresentar a intenção; oferecer diálogo curto; permitir ouvir novamente e consultar apoio; explicar uma expressão pertinente; solicitar recuperação sem modelo; propor pedido com item ou detalhe diferente; oferecer retorno prioritário; permitir nova tentativa; encerrar com resumo e revisão posterior prevista.

Criar uma variante de avaliação com material não treinado e dificuldade compatível. O aluno deve poder usar uma resposta válida diferente do exemplo. Cumprimento comunicativo e uso da forma-alvo são decisões separadas. Uma resposta aberta não contemplada por regras estreitas deve ir para revisão ou ficar não verificada, não receber reprovação por ausência de correspondência literal.

O ciclo mínimo estará pronto quando uma pessoa puder concluí-lo de ponta a ponta, interrompê-lo e retomá-lo, e quando os registros distinguirem prática apoiada, recuperação independente e avaliação posterior.

## 8. Agendamento de revisão v0

Implementar uma política simples, determinística, versionada e observável. A base permite essa solução inicial e não estabelece uma sequência ótima universal de intervalos. [P, §6]

A política recebe histórico pertinente, modalidade, qualidade da evidência, intervalo transcorrido, utilidade para próximas tarefas e orçamento de esforço. Produz candidatos à revisão, prioridade, intervenção recomendada e explicação do que foi adiado.

O primeiro algoritmo deve selecionar oportunidades elegíveis para o contexto de uso; priorizar alvos úteis e frágeis; evitar duplicar uma recuperação já demonstrada em tarefa integrada; escolher atividades que caibam aproximadamente no orçamento; e permitir continuar o percurso sem quitar toda a fila. O orçamento é uma estimativa de esforço, não um cronômetro punitivo.

Apoio, correção imediata, erro linguístico e falha técnica terão tratamentos distintos. Uma falha de microfone não reduz a estimativa linguística. Várias repetições na mesma sessão não equivalem a várias verificações espaçadas. Dificuldade recorrente deve selecionar outra intervenção, como contraste, explicação ou pré-requisito. [P, PED-031–PED-038]

Parâmetros iniciais de intervalo e limiares são hipóteses operacionais em configuração testada, nunca constantes apresentadas como ciência. Não é necessário implementar FSRS, treinamento de modelo ou um escore sofisticado de domínio na primeira entrega. A comparação de políticas com dados reais pertence à evolução PED-039.

### Política executável v0 de evidência e progressão

Identificar a política como `learning-policy-v0`, com parâmetros em arquivo versionado. Cada decisão registra versão, evidências consideradas, motivo, data e próxima ação. A mesma entrada deve produzir a mesma saída no subconjunto PHP/Kotlin. Mudanças de política não reescrevem avaliações históricas; recalcular projeções registra a versão usada.

Uma evidência elegível requer tentativa tecnicamente válida, atividade e rubrica identificadas, modalidade pertinente e avaliação autorizada para aquela dimensão. Autoavaliação e resposta aberta pendente registram prática, mas não confirmam capacidade. Não inferir um alvo que a resposta não demonstra.

| Decisão | Regra operacional inicial |
|---|---|
| Avanço inicial | Uma tarefa com variação pertinente, executada sem modelo/dica que forneça a resposta, pode demonstrar a capacidade se todas as dimensões essenciais da rubrica forem atendidas. Cada dimensão tem decisão explícita; não calcular média compensatória. |
| Avanço enquanto há avaliação pendente | Permitir explorar a próxima missão que não dependa de capacidade ainda não demonstrada; a avaliação pendente não vira aprovação. Bloqueio por pré-requisito deve oferecer tarefa alternativa ou revisão humana. |
| Retenção observada | Exigir sucesso independente elegível em nova sessão, com variante diferente e ao menos 24 horas desde a última exposição registrada ao alvo ou solução. Registrar o intervalo real e as exposições conhecidas; o rótulo não comprova retenção aos 7 ou 30 dias. |
| Apoio ou correção imediata | Registrar progresso de prática e a exposição; não contar como demonstração independente nem retenção. Um sucesso anterior continua no histórico. |
| Evidência contraditória | Um erro linguístico elegível posterior muda a síntese para “necessita verificação”; preserva a demonstração anterior e não revoga o percurso já liberado. Novo sucesso elegível atualiza a síntese, mantendo a contradição consultável. |
| Falha técnica ou tempo duvidoso | Não reduzir capacidade. Se a ordem ou o intervalo não forem confiáveis, não confirmar retenção; solicitar nova verificação posterior. |
| Recuperação integrada | Atualizar somente os alvos/modalidades explicitamente avaliados. Suprimir outra recuperação do mesmo alvo/modalidade na sessão se já houve sucesso independente elegível; essa supressão não elimina revisão em outra data. |

As 24 horas são uma separação operacional inicial entre prática imediata e observação posterior, sujeita a revisão. Uma sessão diferente, sozinha, não prova espaçamento. Exposição ao alvo significa apresentação de modelo, solução, dica que forneça a resposta ou uso registrado em treino; o estímulo necessário à própria tarefa de verificação não reinicia o intervalo, salvo se revelar a resposta. O intervalo é calculado até o início da tentativa e exclui a própria resposta avaliada. Para eventos offline, usar sequência, exposições, sincronização e condições temporais conhecidas; diante de conflito entre aparelhos ou relógios, manter o resultado temporal como incerto. Uma exposição posterior não apaga uma observação passada, mas reinicia o intervalo para a próxima observação. Novas exposições sincronizadas podem revisar uma classificação provisória.

### Seleção e intervalos v0

Manter agendamento por alvo e modalidade, sem criar um escore geral. Distinguir demonstração inicial de avanço no espaçamento: um acerto independente imediato pode permitir avanço curricular segundo a rubrica, mas não promove a etapa de revisão.

| Evento | Etapa e próxima revisão |
|---|---|
| Introdução, tentativa apoiada ou erro linguístico | Definir etapa inicial com intervalo de 1 dia a partir do evento; preservar histórico. Erro também seleciona a intervenção pertinente. |
| Primeiro sucesso independente temporalmente elegível após introdução ou reinício | Promover de 1 para 3 dias, contados do início da tentativa. |
| Próximo sucesso independente temporalmente elegível | Promover de 3 para 7 dias, depois de 7 para 14; em 14, manter 14 dias, contados da tentativa. |
| Sucesso independente sem intervalo elegível | Manter etapa e vencimento existentes; não adiar a revisão por repetição imediata. Se não existir agenda, criar etapa inicial de 1 dia a partir da tentativa. |
| Falha técnica, avaliação pendente ou tempo incerto | Manter etapa e vencimento existentes; não promover nem reduzir por ausência de evidência válida. |

Sucesso temporalmente elegível tem as condições da linha “Retenção observada” acima: nova sessão, variante diferente e pelo menos 24 horas desde a exposição anterior pertinente. Pode promover a etapa mesmo se ocorrer antes do vencimento sugerido; registrar o intervalo efetivo. Prática imediata não promove etapa mesmo quando o item já estiver vencido. O vencimento é uma recomendação de agenda, não um requisito de aprovação curricular.

Cada intervalo parte do momento da tentativa ou exposição pertinente, não do recebimento tardio da avaliação; datas já passadas tornam o item elegível na próxima sessão. Avaliações recebidas fora de ordem ou revistas exigem recalcular a projeção pela ordem temporal confiável das tentativas/exposições. Sequência local ordena eventos do mesmo dispositivo; identificador pode estabilizar a exibição, mas não comprova a ordem real entre aparelhos. Se ordem ou intervalo não puderem ser determinados, manter a classificação temporal incerta. Não aplicar decisões simplesmente pela ordem de chegada. Esses valores são defaults substituíveis, não uma sequência ótima nem requisito para liberar conteúdo.

Contar dificuldades por alvo/modalidade: dois erros linguísticos elegíveis consecutivos em sessões distintas acionam intervenção alternativa. Tentativas com falha técnica não entram na contagem; sucessos imediatamente corrigidos não a zeram. Sucesso independente encerra a sequência de erros. Depois de uma intervenção e mais dois erros elegíveis, encaminhar a revisão pedagógica e permitir outro percurso compatível, sem repetição automática ilimitada.

Na sessão, filtrar modalidades disponíveis e pré-requisitos; ordenar candidatos por: necessidade na próxima missão escolhida, necessidade de verificação/intervenção, vencimento mais antigo e identificador estável como desempate. Estimar inicialmente 1 minuto por recuperação fechada e 3 por produção aberta, substituindo as estimativas por dados observados. Incluir candidatos que caibam no orçamento escolhido, pulando os que não caibam e mostrando adiamentos. Se nenhum couber, oferecer ajustar o orçamento ou escolher outra atividade. O tempo estimado nunca reprova nem encerra uma resposta em andamento.

Para novidades, estimar a demanda dos próximos 7 dias com os mesmos custos e datas. Se superar o orçamento semanal derivado dos dias e minutos informados pelo aluno, recomendar uma missão com menos alvos novos ou somente manutenção e explicar a previsão. A recomendação pode ser recusada; não bloqueia o conteúdo. Sem disponibilidade semanal informada, pedir a preferência ou mostrar previsão sem afirmar que cabe na rotina. Após ausência de 14 dias ou mais, oferecer rechecagem de até três alvos prioritários dentro do orçamento; não converter toda a fila em dívida obrigatória.

### Fixtures obrigatórias de F0–F3

| Entrada | Saída esperada |
|---|---|
| Escolha correta em leitura, sem produção | Evidência de reconhecimento/leitura; produção permanece não avaliada. |
| Pedido oral válido com outra expressão | Objetivo oral demonstrado se a rubrica for atendida; expressão-alvo ausente permanece não demonstrada. |
| Recuperação escrita sem apoio em variante cuja resposta não foi revelada, após dica em outra variante há 10 minutos; etapa de 1 dia já agendada | Pode registrar recuperação independente atual; não confirma retenção posterior; mantém etapa e vencimento, sem promover para 3 dias. Repetir a resposta recém-corrigida continua sendo prática imediata. |
| Primeiro sucesso independente elegível na etapa de 1 dia | Avança somente a etapa da modalidade avaliada para 3 dias a partir da tentativa; registra intervalo observado. |
| Avaliação tardia de tentativa anterior a um erro já recebido | Recalcula pela ordem dos acontecimentos; não apaga o efeito posterior do erro por ter chegado depois. |
| Abre solução e abandona a atividade sem responder | Persiste exposição, sem tentativa errada; intervalo posterior considera essa exposição, inclusive após sincronização tardia. |
| Variante nova correta, sem apoio, 72 horas após última exposição conhecida | Retenção observada na modalidade e no intervalo registrado; não declara fluência geral. |
| Sucesso anterior seguido de erro elegível | Histórico preservado; síntese “necessita verificação”; revisão em 1 dia, sem revogar percurso. |
| Dois erros elegíveis em sessões distintas | Seleciona outra intervenção; não repete automaticamente a mesma pergunta. |
| Tarefa integrada demonstra dois de três alvos | Atualiza e suprime duplicação apenas dos dois demonstrados na modalidade avaliada. |
| Relógio conflitante ou transcrição vista em outro aparelho | Não confirma independência temporal indevida; revisa projeção provisória quando necessário. |
| Orçamento de 4 minutos, candidatos de 3, 3 e 1 minuto nessa ordem | Seleciona primeiro e terceiro; explica adiamento do segundo. |

F0 detalha rubricas e fixtures; F2 executa avaliação local limitada e projeção provisória; F3 completa seleção, agenda e consolidação. Avaliações posteriores reservadas usam o protocolo de §13, não as datas do agendador.

## 9. Offline e sincronização

**Promessa inicial:** depois de baixar um pacote, o aluno pode ler, ouvir, realizar atividades fechadas, escrever, gravar localmente e consultar progresso armazenado sem conexão. Criação de conta, novo conteúdo, avaliação humana, IA e consolidação entre dispositivos exigem conexão. Essa fronteira deve aparecer na interface.

O desenho de fonte local e sincronização segue o padrão descrito na documentação Android, mas as regras de conflito abaixo são decisões específicas deste produto. [T2]

### Fluxo de escrita

1. A resposta e seu registro de envio pendente são salvos na mesma transação local.
2. A interface usa o estado local imediatamente.
3. A sincronização transmite os eventos com identificadores estáveis e retentativas controladas.
4. O servidor valida acesso e esquema, deduplica, persiste e confirma cada evento.
5. Uma confirmação de persistência não equivale a avaliação concluída; decisões humanas podem chegar depois.
6. O cliente só remove a pendência confirmada e baixa as mudanças por cursor.

Exposições usam a mesma garantia de persistência local e reenvio idempotente, com confirmação independente por evento. Ao acessar um apoio, registrar o evento antes de revelar o conteúdo; uma interrupção nesse ponto pode deixar uma exposição conservadora, mas não revelar apoio sem registro. Tentativas podem chegar antes de exposições de outro lote: consolidar a classificação como provisória e recalcular quando os eventos faltantes chegarem. Em F2, essas garantias já abrangem o uso em um aparelho.

A entrega poderá ocorrer mais de uma vez. O efeito de um mesmo evento não poderá ocorrer duas vezes. Usar restrição única por usuário/evento, verificar que retransmissões não alteraram o payload e tornar também idempotentes os jobs que atualizam evidências e projeções. Não prometer uma rede de entrega exatamente uma vez.

Não usar simplesmente “última escrita vence” para tentativas. Cada tentativa legítima é preservada. Preferências simples podem ter uma política de versão separada. Datas do aparelho podem estar erradas: registrar horário informado, horário de recebimento, sequência local e relação com a sessão. Evidência temporal duvidosa não deve confirmar retenção artificialmente.

Áudios não fazem parte do lote JSON. Seus envios terão estado próprio, limites e reenvio independente. Uma falha de upload mantém a tentativa e o estado pendente, sem fabricar erro linguístico.

Downloads precisam ser retomáveis quando viável, verificados por hash e ativados atomicamente. Um pacote incompleto não substitui o anterior funcional. O usuário deve ver espaço utilizado e poder remover conteúdo e gravações.

Separar dados locais por conta. Logout, troca de conta e expiração de token não podem enviar tentativas ao usuário errado nem apagar silenciosamente operações pendentes. Uma demo sem conta pode existir isoladamente; importação posterior exige uma regra explícita, não fusão implícita de perfis.

**Fronteira de entrega:** F2 já exige persistência transacional da tentativa e da pendência, reenvio idempotente no servidor e nos jobs, recuperação após encerramento do processo, confirmação por evento, isolamento por conta e ativação atômica do pacote. Esses itens são condição do primeiro ciclo offline utilizável. F3 amplia a consolidação entre aparelhos, cursores e recuperação de conflitos, incluindo exposições concorrentes e reavaliações; não adia as garantias básicas de F2. Até F3, a experiência de validação usa um único aparelho por participante e explicita essa limitação.

## 10. Avaliação de fala, escrita e IA

### Primeiro piloto

Usar exemplos previamente revisados, gravação local, reprodução, rubricas simples e uma fila de avaliação humana. Respostas fechadas podem ter correção determinística dentro do domínio validado. Produções abertas desconhecidas pelas regras não devem ser reduzidas a um teste de igualdade de texto.

Mostrar a procedência: prática registrada, autoavaliação, avaliação automática limitada ou avaliação humana. Somente evidência com escopo adequado atualiza a capacidade correspondente. Uma autoavaliação não substitui validação independente. [P, PED-028, PED-041, PED-056]

Dimensionar o piloto pela capacidade real de revisar produções. Tempo do avaliador, concordância e retrabalho entram no custo operacional. Se nem toda fala puder ser avaliada, informar claramente quais resultados permanecem não verificados e definir uma amostra de avaliação coerente com o protocolo. Não prometer avaliação oral automatizada ilimitada.

### Operação humana mínima

Antes de convidar participantes, nomear um responsável pedagógico, avaliadores e um substituto; registrar disponibilidade semanal real, rubricas e treinamento. Uma pessoa pode acumular funções operacionais, mas a verificação independente de concordância requer um segundo avaliador. Sem esses responsáveis e sua capacidade confirmada, F4 não abre recrutamento.

Avaliar humanamente todas as produções abertas dos instrumentos inicial, imediato e posteriores, em cada modalidade prevista. Na prática, avaliar ao menos uma produção escrita e uma oral por missão e participante, quando realizadas e enviadas deliberadamente; selecionar a primeira tentativa tecnicamente válida antes do feedback para evitar escolher apenas a melhor. Tentativas adicionais podem permanecer como prática não verificada. Recusar upload não gera erro nem impede estudo; indisponibilidade de áudio na pesquisa é registrada como dado ausente e limita a conclusão oral.

Um segundo avaliador, sem acesso à primeira decisão, revisa uma amostra aleatória de 20% das produções de avaliação, com pelo menos dez produções por modalidade ou todas quando houver menos de dez. A amostra é distribuída entre momentos e participantes. Registrar decisões originais, concordância por dimensão, desacordos e adjudicação; desacordos em dimensão essencial são resolvidos pelo responsável pedagógico antes da síntese final. Notas usadas para avaliar concordância permanecem preservadas, mesmo após adjudicação. Ordenar e anonimizar as avaliações para reduzir conhecimento do momento e do histórico do aluno, documentando limites desse mascaramento.

Medir tempo de leitura/escuta, decisão, feedback, segunda avaliação e retrabalho em ensaio antes de F4. Calcular, por semana de maior demanda:

```text
carga = participantes × produções primárias por participante × minutos por revisão
      + produções de dupla avaliação × minutos de segunda revisão
      + minutos de adjudicação, treinamento e atendimento
capacidade utilizável = 0,8 × minutos disponíveis dos avaliadores naquela semana
```

As produções incluem prática e instrumentos de pesquisa previstos naquela semana. Calcular o maior número inteiro de participantes cuja carga caiba na capacidade de cada semana e no orçamento monetário. A reserva de 20% é hipótese operacional para imprevistos; se nenhuma vaga couber, reduzir a rodada ou ampliar capacidade antes de recrutar.

O total semanal é uma primeira restrição, não uma garantia do prazo. Antes de admitir uma rodada, montar também um calendário diário de chegadas previstas, vencimentos e minutos disponíveis por pessoa e função: avaliação primária, segunda avaliação, adjudicação e atendimento. Uma pessoa que acumula funções tem sua disponibilidade repartida, sem contar os mesmos minutos duas vezes. A segunda avaliação da mesma produção deve ser feita por pessoa diferente da primeira; capacidade ociosa de um avaliador não compensa automaticamente a falta do outro.

Para cada dia de recebimento, verificar se a carga de prática pode ser alocada até seu vencimento em dois dias úteis, respeitando a reserva de 20% por pessoa e compromissos já assumidos. Definir o calendário de dias úteis e fuso no protocolo operacional. Simular também chegada concentrada de todas as produções de prática previstas para a rodada no mesmo dia, em vez de presumir distribuição uniforme. Se não couber, reduzir vagas, escalonar rodadas ou ampliar disponibilidade; não prometer o prazo com base somente na média semanal. Estudos podem gerar respostas extras: se a demanda real ultrapassar o cenário reservado, comunicar a nova previsão e acionar a regra de fila, sem impedir a prática.

Reservar separadamente capacidade para segunda avaliação e adjudicação dos instrumentos, com prazo até o congelamento dos dados da rodada definido em §13. Cada verificação diária reestima o cumprimento dos vencimentos; risco previsto de descumprimento já suspende novas admissões até replanejamento, sem esperar que duas verificações confirmem atraso efetivo.

Prazo inicial de retorno de prática: até dois dias úteis após recebimento completo da resposta; informar prazo e pendência na interface. Atraso não produz reprovação. Se a fila exceder a capacidade disponível até os vencimentos ou houver risco de atraso na verificação diária, suspender novas admissões, replanejar a fila e comunicar a previsão aos participantes. A prática essencial continua disponível. Resultados de pesquisa podem aguardar adjudicação, com feedback específico de qualquer instrumento apenas após a última avaliação, sem interromper o feedback normal das missões.

### Evolução de automação

Separar captura, reconhecimento de fala e avaliação pedagógica. O `SpeechRecognizer` do Android pode enviar áudio para servidores remotos; não presumir processamento local nem disponibilidade offline apenas porque a API pertence ao sistema. [T9]

Antes de usar uma avaliação automática em decisões relevantes, comparar com julgamentos humanos, medir falsos erros e acertos, analisar condições acústicas e permitir abstenção. A confiança declarada pelo modelo não basta como calibração. [P, §10]

IA generativa, se adicionada, recebe contexto limitado, objetivo e rubrica; não recebe acesso irrestrito à conta ou ao banco. Sua saída é validada, tem orçamento e timeout, não publica currículo automaticamente e não bloqueia o núcleo quando indisponível. Conteúdo do usuário é dado não confiável, não instrução para o sistema de avaliação.

## 11. Autoria e publicação de conteúdo

Começar com importação de JSON validado, formulário editorial simples e pré-visualização. Não construir de saída um CMS visual arrastável. O trabalho pedagógico e a revisão de áudio são entregáveis de produção, não simples dados fictícios para demonstrar telas.

Fluxo: **rascunho → revisão linguística/pedagógica → validação técnica → publicação de versão**. Registrar autoria/procedência, autorização de uso, objetivo, alvos, apoios, explicação, rubrica e revisor. O áudio precisa corresponder ao texto e ser inteligível. [P, PED-053 e §10]

O validador deve detectar referências inexistentes, mídia faltante, tipos incompatíveis, pré-requisitos circulares, ausência de rubrica e sobreposição indevida entre treino e avaliação. A revisão humana continua necessária para naturalidade, correção e pertinência.

Separar os bancos de treino e avaliação desde a origem. Não embutir o banco reservado de avaliação nem seus gabaritos nos pacotes de prática. Disponibilizar tarefas de avaliação quando atribuídas; manter gabaritos no servidor. Material apresentado ao aluno deixa de ser inédito para ele, mesmo que pertença ao banco reservado. Feedback pode exigir aposentar ou substituir aquela variante. [P, PED-058]

Pacotes offline de treino podem conter respostas para feedback local: isso não é uma barreira de segurança nem uma certificação resistente a adulteração. Não alegar controle psicométrico ou antifraude que o produto não implementa.

## 12. Segurança, privacidade e acessibilidade

Usar sessão e proteção CSRF na web e tokens Sanctum no Android, com autorização por recurso. Sanctum suporta autenticação móvel e gestão de tokens; a documentação informa que a expiração precisa ser configurada quando desejada. Neste projeto, definir prazo finito, revogação por dispositivo e comportamento de reautenticação, sem presumir um fluxo de refresh inexistente. [T8]

Credenciais do aplicativo não ficam em texto aberto em logs ou tabelas de aprendizagem. Proteger seu armazenamento local com mecanismos adequados da plataforma; segredos de infraestrutura e fornecedores nunca são enviados aos clientes. Usar HTTPS, controle de acesso editorial, limitação de requisições e validação de arquivos. As recomendações oficiais de segurança Android são referência para a implementação. [T14]

Gravar somente após ação explícita e com indicador visível; pedir permissão no momento de uso; não capturar em segundo plano. O upload deve ser uma decisão separada da gravação. Sem permissão, oferecer estudo em outra modalidade e preservar a ausência de evidência oral.

Definir antes do piloto finalidades, destinatários, retenção, exportação e exclusão de áudios, respostas e metadados. Diferenciar o necessário ao serviço de uso opcional em pesquisa ou aprimoramento. Não reutilizar dados ocultamente. A exclusão precisa alcançar banco, arquivos, derivados e política de backups; um tombstone técnico mínimo só pode existir pelo prazo e finalidade documentados. [P, PED-057]

Não colocar gravações de alunos, identificadores pessoais ou o banco reservado de avaliação em repositório público. Mesmo num projeto aberto, separar código, currículo publicável, conteúdo reservado e dados pessoais.

Tratar acessibilidade como critério de liberação: navegação por leitor de tela, fonte ampliada, contraste, foco, controles de áudio compreensíveis, redução de movimento e alternativas de interação. Legendas e transcrições devem ser possíveis quando adequadas, sem fazer uma alternativa textual contar como avaliação auditiva independente. [P, PED-051]

Este plano não constitui análise jurídica nem certifica conformidade. A revisão das obrigações de privacidade e publicação aplicáveis ao piloto é uma condição de lançamento, não um pressuposto resolvido pela escolha do framework.

## 13. Marcos de implementação

Os marcos são dependências e critérios de saída. As faixas de esforço abaixo são hipóteses de planejamento, não compromissos de calendário. Não autorizar a construção automática de todos eles em um único pedido ao agente de programação.

| Marco | Entrega | Critério de saída | Dependência |
|---|---|---|---|
| F0 — contrato e missão | Esquemas, missão completa, rubrica, política v0, fixtures e protocolo curto de teste | Especificação registrada; revisão humana e teste observado identificados separadamente como concluídos ou pendentes. F0 só fica completo com ambos realizados | Base pedagógica |
| F1 — fundação executável | Monorepo, Laravel, Android, Docker, banco, autenticação, teste de contrato e painel mínimo | Ambiente reproduzível; app consulta catálogo; conta e permissões isoladas; builds e testes reais passam | Especificação de F0; revisão humana e teste podem continuar pendentes |
| F2 — uma missão ponta a ponta | Player, áudio, escrita, fala, apoios, avaliação limitada/humana, persistência e sincronização básicas | Missão concluída e retomável após crash; resposta alternativa tratada; falha técnica não reprova; reenvio idempotente; pacote offline funcional; revisão e achados do teste formativo de F0 tratados | F1 para implementação interna; validação de F0 para concluir |
| F3 — sequência e manutenção | Três missões conectadas, fluência mínima, diagnóstico/pulo, política de revisão, progresso, consolidação entre aparelhos e retomada | Quatro vertentes presentes; contexto sem fala respeitado; carga não aprisiona; conflitos e atraso não apagam histórico nem confirmam retenção indevida | F2 |
| F4 — piloto Android | Doze missões revisadas, avaliação separada, revisão humana operacional, privacidade e instrumentação | Todos os P0 têm teste ou procedimento verificável; piloto pode iniciar com avaliações posteriores previstas e responsáveis definidos | F3 |
| F4b — relatório do piloto | Resultados imediatos e posteriores, dificuldades, custos e limitações | Relatório distingue retenção, transferência, experiência e perdas de acompanhamento; não promete superioridade não demonstrada | Coleta efetiva do piloto |
| F5 — estudo pela web | Player Vue usando os mesmos contratos, rubricas e serviços | Matriz de paridade pedagógica aprovada; captura e reprodução testadas nos navegadores escolhidos | F3; expansão ampla condicionada aos achados de F4 |
| F6 — expansão orientada por evidência | Calibração de revisão, IA supervisionada, conteúdos ampliados, gamificação opcional e avaliação de iOS | Cada expansão tem hipótese, custo, critério de aceite e proteção contra regressão pedagógica | Resultados dos marcos anteriores |

F4 não significa que os resultados de retenção já existem: iniciar o piloto exige protocolo e instrumentos prontos; concluir sua avaliação exige os dados posteriores. Os exemplos de 7 e 30 dias da base são adotados no protocolo exploratório abaixo, com janelas explícitas; não são o calendário obrigatório do agendador de revisão. [P, §11]

### Responsáveis, esforço e limites de investimento

Papéis abaixo são atribuições a preencher com nomes em F0, sem presumir contratação ou disponibilidade. O responsável de produto mantém a decisão de escopo; a liderança técnica responde por implementação e testes; o responsável pedagógico responde por currículo, rubricas e instrumentos. Infraestrutura e operação podem ser acumuladas pela liderança técnica. Revisão independente segue §10.

| Marco | Responsável pela entrega | Engenharia, em dias-pessoa | Pedagogia/operação, em dias-pessoa | Limite inicial de esforço combinado |
|---|---|---|---|---|
| F0 | Produto e responsável pedagógico, com liderança técnica | 3–5 | 3–5 | 10 dias-pessoa |
| F1 | Liderança técnica | 5–8 | 1–2 | 10 dias-pessoa |
| F2 | Liderança técnica e responsável pedagógico | 10–15 | 4–6 | 21 dias-pessoa |

São estimativas iniciais para o recorte descrito, sem medição prévia; dias-pessoa não equivalem a dias corridos. Incluem contrato, implementação, testes e revisão da entrega; não incluem espera de recrutamento ou acompanhamento de 30 dias. Medir esforço real e revisar as faixas ao encerrar cada marco. Com 80% do limite consumido e critério de saída ainda em risco, registrar causa, saldo e opção de reduzir escopo ou reestimar antes de iniciar novos itens. Nunca remover integridade de dados ou requisitos pedagógicos para declarar o marco concluído.

F0 pode ser demonstrado com roteiro, conteúdo e protótipo simples; não exige backend e aplicativo completos. Testar a missão com uma rodada exploratória de três a cinco adultos do público, dentro da capacidade disponível, observando compreensão das instruções, apoios, tarefa e rubrica. É teste formativo de usabilidade, sem alegação de eficácia e sem contar como piloto F4. Somente material revisado é apresentado como currículo; rascunhos ficam identificados para revisão interna.

A especificação de F0 libera a fundação técnica de F1 mesmo enquanto revisão pedagógica ou participantes estiverem indisponíveis. Registrar essas pendências; não marcar F0 como completo nem apresentar validação inexistente. Estrutura, autenticação, catálogo com fixtures e testes técnicos podem avançar. Regras e conteúdo ainda não revisados permanecem provisórios; sua publicação, uso com alunos e decisões que dependam da validação aguardam revisão. O teste formativo ocorre após revisão humana do material; seus achados orientam o player e devem ser tratados antes de concluir F2, sem bloquear antecipadamente toda a infraestrutura.

Até F2, usar ambiente local e recursos já disponíveis; nenhum gasto recorrente novo é pressuposto. Antes de serviço pago ou recrutamento com custos, registrar teto em reais, fonte dos recursos, responsável e custo previsto. Valor desconhecido fica como pendência, nunca como custo zero. F3–F4 recebem estimativas e tetos próprios após o ciclo medido em F2; F5–F6 após os resultados pertinentes. Não iniciar um marco sem responsável disponível e limite de esforço registrado, nem operação paga sem teto financeiro definido.

### Protocolo exploratório inicial do piloto F4

F0 produz o esqueleto e instrumentos da primeira missão; F3 completa a sequência e F4 congela protocolo, conteúdo, rubricas, variantes e plano de análise antes do recrutamento. Registrar alterações posteriores com motivo e impacto, sem trocar critérios após observar resultados. O objetivo é verificar viabilidade, experiência e sinais de aprendizagem com prática continuada; não testar superioridade causal.

| Item | Definição inicial |
|---|---|
| Público e tamanho | Adultos falantes de português brasileiro aprendendo inglês inicial; caracterizar experiência e condições de uso. Número final de vagas é calculado pela capacidade de avaliação de §10 e pelo orçamento. Não adotar a rodada formativa de três a cinco pessoas como dimensionamento confirmatório. |
| Responsabilidade | Nomear coordenação do piloto, responsável pedagógico, avaliadores, substituto e responsável por dados. Registrar meios de atendimento, disponibilidade e calendário antes de convidar. |
| Avaliação inicial | Antes da primeira missão, obter referência por capacidade/modalidade em tarefas reservadas. É condição da pesquisa; diagnóstico opcional do produto permanece independente. Explicar participação e uso de dados separadamente do serviço. |
| Sequência de estudo | Oferecer as doze missões na ordem e com os percursos alternativos documentados; registrar início, fim da sequência, exposição, apoios, saltos e tempo ativo estimado. Na admissão, agendar previsão de conclusão em até 28 dias; desvios permanecem no relatório, sem bloquear o estudo. |
| Avaliações posteriores | Após a sequência: imediata em até 24 horas, dia 7 com janela de ±2 dias e dia 30 com janela de ±5 dias, contados do encerramento da sequência, registrando intervalos reais. São hipóteses do protocolo, distintas da agenda de revisão. |
| Instrumentos | Preparar quatro formas reservadas A–D, com tarefas por capacidade/modalidade e variação relevante. Distribuir os participantes entre quatro ordens: ABCD, BCDA, CDAB e DABC, correspondentes a inicial, imediata, dia 7 e dia 30. Alocar em blocos de quatro com ordem sorteada e registrada antes da coleta; bloco final incompleto mantém a distribuição tão equilibrada quanto possível. Não repetir forma para o mesmo participante. Revisão pedagógica verifica equivalência planejada, sem alegar equivalência psicométrica demonstrada. Registrar forma, ordem e exposições; substituições exigem variante inédita revisada e registro do desvio. |
| Desfecho principal | Proporção de tarefas inéditas com todas as dimensões essenciais atendidas independentemente no dia 30, por capacidade e modalidade; apresentar numeradores, denominadores e mudança descritiva frente à referência inicial. Não somar modalidades em uma nota global. |
| Resultados complementares | Resultados imediatos e no dia 7, dimensões da rubrica, apoios, tempo de prática, fila humana, custos, erros de correção, fadiga, clareza e motivos de abandono. |
| Exposição intermediária | Permitir prática normal e revisões. Registrar exposição conhecida e perguntar sobre prática externa em cada avaliação posterior. A avaliação do dia 7 também é exposição; relatar o dia 30 como manutenção com prática continuada e avaliação intermediária. Não estimar retenção sem prática a partir desse desenho. |
| Feedback dos instrumentos | Não revelar soluções específicas até o último acompanhamento; registrar qualquer revelação acidental e aposentar a variante para aquele participante. Feedback normal das missões continua disponível. |
| Duração e perdas | Fixar antes do recrutamento as datas de abertura e fechamento das admissões da rodada, sem prorrogação implícita. Cada participante tem 28 dias desde a admissão para concluir a sequência dentro do protocolo. Último dia de coleta da rodada = fechamento das admissões + 63 dias corridos (28 de estudo + 35 de acompanhamento). Quem conclui no prazo tem as janelas completas. Quem termina depois pode continuar estudando, mas é registrado como conclusão fora do prazo; eventual coleta até o corte é exploratória, separada do desfecho principal e sem estender a rodada. Não excluir não concluintes do fluxo nem inventar avaliações ausentes. |

Participação é voluntária, com informação de finalidades e opção de sair da pesquisa. Não enviar respostas ou áudios sem a decisão de upload prevista no produto. Quem não fornece dados necessários a uma modalidade continua estudando e é registrado como sem medida naquela modalidade; não recebe nota zero por ausência. Não confundir retirada da pesquisa com exclusão automática da conta; atender pedidos de dados conforme a política aplicável.

Congelar os dados até dez dias úteis após o corte de coleta, com avaliações e adjudicações concluídas ou pendências explicitadas como dados não resolvidos; entregar o relatório até dez dias úteis após esse congelamento. Registrar previamente as datas civis no calendário e reservar orçamento humano até o relatório. Encerrar a coleta de pesquisa não encerra o acesso ao produto. Nova rodada ou extensão excepcional exige protocolo, capacidade e orçamento próprios; não alterar retrospectivamente o corte da rodada original.

A ordem das formas reduz a associação fixa entre dificuldade do instrumento e momento, mas não prova equivalência. Relatar resultados e distribuição por forma/ordem, inclusive desvios e blocos incompletos. O denominador de acompanhamento de 30 dias é o total de inscritos admitidos na rodada, por modalidade prevista no protocolo, incluindo não concluintes e retiradas como perdas de acompanhamento; não usar somente concluintes para atingir o limiar de 70%. Na análise de aprendizagem, mostrar separadamente medidas disponíveis, pares válidos e resultados fora do prazo, com os respectivos denominadores.

### Análise e decisões de continuidade

O relatório mostra convidados, elegíveis, inscritos, iniciantes, concluintes e acompanhados por momento/modalidade. Resultados de aprendizagem incluem denominadores por tarefa e participante, distribuição individual e incerteza descritiva. Não tratar tarefas do mesmo participante como observações independentes para estreitar artificialmente intervalos. Descrever estimativas por participante; se apresentar intervalos por reamostragem, reamostrar participantes. Amostra pequena limita a precisão e não sustenta testes confirmatórios de superioridade.

Separar ausência de coleta, problema técnico, resposta apoiada e resposta linguisticamente insuficiente. Resposta apoiada permanece fora do numerador de sucesso independente; detalhar sua frequência. Apresentar análise dos dados disponíveis e limites de sensibilidade considerando os resultados ausentes como sucesso ou insucesso, sem imputar desempenho individual. Quem não conclui a sequência não recebe artificialmente uma data de encerramento; sua ausência do desfecho posterior aparece explicitamente. Relatar exposição intermediária, atrasos e mudanças de versão; ganhos observados sem controle não provam efeito causal do método.

As seguintes regras operacionais são hipóteses iniciais, congeladas antes da coleta:

- **Interromper a coleta afetada e corrigir:** vazamento de dados, perda confirmada de respostas, atribuição a conta errada ou classificação sistemática de falha técnica como erro linguístico. Manter acesso à prática segura quando possível e reavaliar dados afetados antes de retomar.
- **Ajustar antes de ampliar:** menos de 80% dos iniciantes concluem o primeiro ciclo sem intervenção técnica da equipe; menos de 70% dos participantes previstos fornecem acompanhamento de 30 dias; prazo humano excede o limite de §10; ou concordância exata nas dimensões essenciais fica abaixo de 80% da amostra dupla. Informar contagens, causas e incerteza; o limiar não valida a rubrica por si só.
- **Ampliar de forma limitada:** nenhum problema crítico aberto; todos os P0 verificados; capacidade e orçamento da próxima rodada definidos; critérios operacionais anteriores atendidos; e resultados por capacidade revisados. Se a proporção de sucesso independente aos 30 dias ficar abaixo da referência inicial em alguma capacidade/modalidade com medidas pareadas, investigar e repetir esse recorte antes de ampliá-lo. Ausência de queda é apenas condição de continuidade, não prova de benefício.

Se dados de aprendizagem forem insuficientes, ampliar apenas a coleta necessária dentro da capacidade, sem expandir currículo com base em resultado inconclusivo. A diferença mínima educacionalmente útil e o tamanho amostral para alegações de eficácia serão definidos em estudo comparativo posterior; este piloto não inventa um limiar de eficácia a partir de satisfação ou retenção de usuários.

Conteúdo e software podem evoluir em paralelo após a especificação de F0, mantendo as pendências de validação humana visíveis. Não produzir doze missões antes de observar a primeira funcionando com pessoas. Não usar feedback de uma sessão imediata para declarar eficácia comprovada.

Na sequência de três missões de F3, incluir ao menos uma atividade de compreensão contínua e uma de produção contínua com repertório familiar, sem novo alvo obrigatório ou cronômetro punitivo. F4 verifica sua presença efetiva e o equilíbrio das quatro vertentes por sequência; não basta emitir relatório de lacunas. F6 amplia variedade e personalização de fluência.

### Definição de pronto por entrega

Cada entrega precisa de um fluxo demonstrável, evidências de testes executados, vínculo com os PED pertinentes, migrações verificadas, tratamento de falhas, instruções de execução e limites conhecidos. Uma tela preenchida com dados falsos é um protótipo de interface, não uma funcionalidade concluída.

Uma avaliação que depende de trabalho humano só está pronta quando há rubrica, responsável, fila, permissões, capacidade de atendimento e forma de comunicar espera ao aluno. Uma página “em breve” não cobre um P0.

## 14. Testes e observabilidade

### Testes pedagógicos obrigatórios

| Cenário | Resultado esperado |
|---|---|
| Acerta escolha, mas não produz | Preserva reconhecimento; não declara produção dominada |
| Já sabe executar a tarefa | Permite avanço; planeja manutenção sem cota de telas |
| Recebe dica ou vê transcrição | Registra o apoio; não classifica como desempenho independente |
| Repete após ver solução | Reconhece prática imediata; não inventa retenção posterior |
| Erra repetidamente | Muda intervenção ou pré-requisito |
| Retorna após ausência | Reavalia amostra pertinente e propõe retomada viável |
| Responde com alternativa válida | Separa objetivo cumprido de forma-alvo ainda não demonstrada |
| Microfone ou reconhecimento falha | Registra problema técnico/abstenção, não erro linguístico |
| Não pode falar agora | Oferece outra modalidade sem inventar evidência oral |
| Revisão excede orçamento | Prioriza e explica adiamento; não bloqueia todo o curso |
| Conclui módulo curto | Mostra capacidades específicas, não certificação A1 automática |

Esses testes operacionalizam a seção de casos transversais e as regras de revisão da base. [P, §6 e §8]

### Testes técnicos obrigatórios

Reenviar um mesmo lote dez vezes deve produzir uma única tentativa por identificador e um único efeito em cada projeção. Um lote com um item inválido precisa de resposta por item e política explícita de atomicidade. Um crash entre salvar resposta e sincronizar não pode perder a tentativa.

Testar perda de rede durante sessão e download, encerramento do processo, rotação da tela, permissão recusada, falta de espaço, áudio interrompido, token expirado, logout com fila pendente, dois dispositivos, relógio local errado, atualização do conteúdo e migração de Room/PostgreSQL.

Testar apoio aberto sem resposta, crash ao revelar solução, exposições recebidas depois da tentativa e avaliações recebidas fora de ordem. O resultado consolidado deve refletir os eventos conhecidos, sem promover retenção por chegada tardia nem inferir ordem entre aparelhos apenas pelo identificador.

Testar que o usuário A não lê nem altera áudios, progresso ou sessões do usuário B; que papéis editoriais não recebem permissões indevidas; que logs não incluem tokens, respostas pessoais completas ou áudio; e que a exclusão percorre os componentes definidos.

### Ferramentas propostas

Usar o framework de testes do projeto Laravel para regras, HTTP e banco; testes Kotlin para políticas locais e repositórios; testes Compose para interação; e testes de navegador para os fluxos web. Adotar a mesma bateria de fixtures de contrato em PHP e Kotlin. A documentação Android recomenda testar ViewModels, dados e navegação, com medições de desempenho apropriadas. [T1]

A integração contínua deve compilar os projetos, executar os testes correspondentes, verificar esquemas e contratos, validar conteúdo e analisar alterações de dependências. Não confundir compilação bem-sucedida com validação de áudio ou de aprendizagem: essas exigem testes em aparelhos e participação humana.

### Métricas

| Dimensão | Observação proposta |
|---|---|
| Aprendizagem | Desempenho em tarefas inéditas, por habilidade e apoio; manutenção posterior |
| Eficiência | Ganho observado em relação ao esforço de prática medido, com limitações explícitas |
| Experiência | Fadiga, clareza, frustração, abandonos e motivos relatados |
| Correção | Discordância humana/automática, respostas válidas rejeitadas, abstenções |
| Produto | Tempo até concluir primeiro ciclo, interrupções e retomadas bem-sucedidas |
| Operação | Custo de mídia, atendimento humano, erros, fila, falhas de sincronização e latência |

Pontos, quantidade de telas e presença diária não serão indicadores substitutos de proficiência. Minutos ativos também são uma aproximação: definir como pausas, gravações, reprodução e estudo fora do app entram na análise. [P, PED-060 e §11]

## 15. Infraestrutura e sustentabilidade

O ambiente Docker Compose inicial precisa de serviço PHP/web, PostgreSQL, worker e agendamento operacional. Worker e web podem usar a mesma imagem. Ferramenta de e-mail local e emulação de armazenamento de objetos são opcionais conforme a necessidade. Android Studio, SDK e testes físicos ficam fora desse conjunto de serviços do backend.

Começar com filas no banco é uma decisão para reduzir componentes; Laravel documenta drivers de banco e Redis, entre outros. Redis pode ser acrescentado quando houver carga ou funcionalidade que o justifique. [T13]

Em produção, definir ambiente de homologação, deploy reproduzível, execução controlada de migrações, rollback compatível, logs sem dados sensíveis, alertas essenciais e restauração de backup testada. O código e o conteúdo devem poder ser revertidos separadamente. Não incluir ferramentas de desenvolvimento ou servidores de hot reload na produção.

Não há orçamento monetário fechado neste documento. O custo precisa considerar:

```text
custo operacional = infraestrutura + banco/backups + armazenamento
                  + tráfego de mídia + e-mail/observabilidade
                  + revisão de conteúdo + avaliação humana
                  + serviços opcionais de reconhecimento/geração
```

Áudios de currículo são produzidos e revisados uma vez por versão, não sintetizados em toda reprodução. Downloads e cache devem evitar tráfego redundante. Gravações de alunos têm retenção deliberada e upload opcional. IA, quando houver, recebe orçamento e modo degradado que mantém a prática essencial. Gratuito para o aluno não significa operação sem custo. [P, PED-054 e §10]

A primeira medição de custos deve usar o consumo observado no piloto, incluindo tempo humano. Não comprometer o projeto com fornecedor de IA, plano de hospedagem ou escala global antes dessa medição.

## 16. Organização do repositório

### Decisão: um repositório Git para Laravel e Kotlin

Adotar **um único monorepo** desde F1. `apps/android` contém o projeto Kotlin/Compose; `apps/platform` contém o projeto Laravel e a interface Vue/Inertia. Contratos, fixtures, documentação e currículo autorizado ficam na raiz compartilhada. A escolha favorece a revisão conjunta de mudanças que afetam API, cliente e conteúdo no estágio inicial, com uma única fonte versionada para os contratos.

Dois repositórios exigiriam coordenar versões dos contratos e mudanças correspondentes entre eles já na primeira missão. Não há, neste escopo, necessidade de separar equipes ou permissões do código que compense esse custo. Reavaliar somente quando houver necessidade concreta de acessos distintos, equipes autônomas ou ciclos de manutenção que tornem o monorepo um obstáculo observado.

```text
idiomas/
├── AGENTS.md
├── README.md
├── apps/
│   ├── android/
│   └── platform/              # Laravel; web em resources/js
├── contracts/
│   ├── openapi.yaml
│   ├── content-package.schema.json
│   ├── attempt-event.schema.json
│   ├── exposure-event.schema.json
│   ├── evidence.schema.json
│   └── fixtures/
├── content/
│   └── pt-BR_en/              # Apenas material autorizado para este repositório
├── docs/
│   ├── BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md
│   ├── PLANO_DESENVOLVIMENTO_APP_IDIOMAS_v0.1.md
│   ├── decisions/
│   └── milestones/
├── infra/
│   └── compose.yaml
└── scripts/
```

Não começar com dezenas de módulos Gradle ou pacotes internos. No Android, separar inicialmente interface, dados e regras locais por responsabilidade; criar módulos adicionais quando houver benefício verificável. No Laravel, serviços de aplicação devem servir tanto controladores web quanto API. Evitar duplicação de regras nos controladores e nos componentes visuais.

### Builds, CI e publicações independentes

- Android mantém seu Gradle Wrapper e arquivos de versão em `apps/android`; Laravel mantém `composer.lock` e o lockfile da interface web em `apps/platform`. Cada aplicação tem seus próprios comandos de instalação, build e teste documentados.
- Mudanças apenas no Android executam seus checks; mudanças apenas na interface web executam os checks web. Mudanças no backend/API executam também os testes de consumo dos contratos em Kotlin, mesmo sem edição dos esquemas. Mudanças em `contracts`, políticas compartilhadas, fixtures ou conteúdo executam as validações afetadas de ambos os clientes e do backend. Configuração comum de CI e scripts executa também os checks dos projetos que ela afeta. Mudanças de API devem preservar os clientes suportados; propostas incompatíveis exigem migração/versionamento explícitos e teste com o cliente móvel anterior, sem liberar a quebra apenas porque os checks da versão nova passam.
- Publicar backend/web e Android separadamente. Um commit comum não exige deploy simultâneo: o backend precisa continuar compatível com versões móveis ainda em circulação. Identificar releases com nomes distintos, como `platform-v0.1.0` e `android-v0.1.0`, e registrar o commit e a versão dos contratos de cada artefato.
- Manter segredos de deploy e assinatura Android separados por pipeline; credenciais de produção e arquivos de assinatura não entram no Git. Abrir `apps/android` no Android Studio e `apps/platform` no ambiente PHP/web; não criar repositórios Git aninhados nem submódulos para essas aplicações.

Se houver separação futura, definir antes a distribuição versionada dos contratos e fixtures, a matriz de compatibilidade e a migração do histórico. O monorepo organiza o código; Kotlin e Laravel continuam executáveis distintos, comunicando-se pela API.

Manter o banco reservado de avaliação em armazenamento separado com acesso restrito, fora do Git compartilhado com os clientes. Gravações e dados de participantes também ficam fora do repositório. Fixtures públicas usam dados sintéticos e não expõem instrumentos reservados ou informações de participantes. Diretórios no mesmo repositório não fornecem isolamento de leitura a quem pode cloná-lo; se o próprio código exigir acessos distintos, reavaliar a separação dos repositórios.

## 17. Trabalho com Codex

Usar entregas pequenas, com comportamento esperado, limites e validação. `AGENTS.md` é uma convenção reconhecida pelo Codex para instruções de repositório; planos e arquivos de marcos são organização de trabalho, não requisitos mágicos da ferramenta. [T15]

O arquivo de instruções deve ser curto: apontar a base para regras pedagógicas, o plano para arquitetura, comandos reais de build/teste, política de segredos e escopo da tarefa. Não copiar este documento inteiro para dentro dele. Ao alterar regras, atualizar o contrato, os testes e a rastreabilidade; ao corrigir uma tela, ler o contexto pertinente sem impor uma revisão de toda a documentação.

### Prompt inicial proposto

```text
Vamos iniciar um aplicativo de aprendizagem de idiomas.

Use como referências:
- docs/BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md
- docs/PLANO_DESENVOLVIMENTO_APP_IDIOMAS_v0.1.md
Se os documentos ainda estiverem na raiz, leia essas versões e mova-os para
docs/ ao organizar o repositório, atualizando referências sem manter duplicatas.
Se houver versões diferentes nos dois locais, compare-as e reconcilie antes de mover.

Execute somente F0 e F1. Não implemente a plataforma inteira.
Use um único repositório Git: Kotlin em apps/android, Laravel/web em apps/platform,
contratos e fixtures compartilhados na raiz; builds e releases independentes.
Conclua a especificação F0 antes do bootstrap F1. Prepare o teste formativo.
Sem revisão humana ou participantes disponíveis, registre a pendência e prossiga
com a fundação técnica F1, usando fixtures e rascunhos identificados.
Não marque F0 completo nem publique conteúdo ou inicie testes com alunos antes
da revisão humana pertinente; trate achados do teste formativo antes de concluir F2.
Se já existir código, examine o estado real e preserve o que for compatível.
Não sobrescreva trabalho existente nem faça deploy ou operações externas.

Stack aprovada para esta tarefa:
- Android nativo em Kotlin + Jetpack Compose.
- Laravel 13 com PHP 8.4, PostgreSQL e API REST.
- Web em Vue 3 + TypeScript + Inertia, dentro do Laravel.
- Docker Compose para o backend.

Entregue:
1. Estrutura inicial do repositório, instruções e ambiente reproduzível.
2. Registro das decisões e versões compatíveis efetivamente escolhidas.
3. Esquemas iniciais para conteúdo, tentativas, exposições e evidências.
4. Uma missão de exemplo “pedir uma bebida e esclarecer uma informação”,
   identificada como rascunho até revisão humana, e uma variante de avaliação.
5. API mínima de autenticação/catálogo e uma tela Android que a consuma,
   utilizando repositório e persistência local, sem acesso de rede direto da UI.
6. Painel web mínimo, sem construtor visual genérico.
7. Testes de autenticação/autorização, contrato e carregamento do catálogo.
8. Política v0, fixtures de decisões, protocolo formativo, estimativas por marco
   e lista de responsáveis ainda a nomear. Não presumir disponibilidade humana.

Regras obrigatórias:
- Não substituir capacidades por XP ou porcentagem arbitrária.
- Não promover leitura automaticamente a escuta, fala ou escrita.
- Não considerar dica, autoavaliação ou falha técnica como prova de domínio.
- Não implementar chatbot, nota automática de pronúncia ou IA obrigatória.
- Não marcar rascunhos de conteúdo como revisados.
- Não transformar todos os P0 em telas independentes.
- Não iniciar F2 ou fases seguintes automaticamente.

Ao concluir, informe arquivos alterados, comandos realmente executados,
resultados observados, limitações e pendências. Não declare testes passados
sem executá-los. Não invente credenciais, dados de usuários ou resultados.
```

Esse prompt contempla os documentos atualmente na raiz e sua futura organização em `docs/`. Ele não foi executado neste planejamento; a revisão documental não autoriza por si só iniciar o bootstrap.

## 18. Decisões que precisam de verificação no início

A stack, a sequência dos marcos e os parâmetros operacionais v0 têm recomendação definida. Permanecem sujeitos a dados reais: aparelhos e SDK mínimo; nomes e disponibilidade dos revisores; capacidade humana medida; teto financeiro; armazenamento e prazos de retenção de dados; estimativas de F3 em diante; e prioridades de iOS. Parâmetros de aprendizagem já definidos em §8 exigem calibração posterior, não uma nova escolha implícita durante a implementação.

Esses pontos não precisam impedir o desenho da primeira missão. Precisam, porém, ser resolvidos antes das entregas às quais pertencem. Não substituir escolhas ainda não feitas por alegações de “melhor prática universal”.

## 19. Matriz de rastreabilidade dos requisitos pedagógicos

O texto e a prioridade abaixo são extraídos da base fornecida. Marco e cobertura mínima são propostas deste plano. A matriz não altera os critérios de aceite originais. As linhas registram a cobertura **planejada** nesta versão; a implementação e suas pendências são acompanhadas nos documentos de marcos. Um P0 só está coberto quando o respectivo critério da base também foi verificado.

| Requisito | Prioridade original | Requisito da base | Marco | Cobertura mínima proposta |
|---|---|---|---|---|
| PED-001 | P0 | Coletar objetivos, idioma de origem, experiência prévia, disponibilidade e condições de uso. | F3 | Perfil e contexto da sessão; sem fala/áudio não produz penalidade. |
| PED-002 | P0 | Oferecer diagnóstico inicial opcional, breve e progressivo. | F3 | Diagnóstico opcional e indicação explícita de incerteza. |
| PED-003 | P0 | Manter um perfil por habilidade, não apenas um nível global. | F2–F3 | Evidências e painel separados por modalidade. |
| PED-004 | P0 | Permitir demonstrar conhecimentos e pular conteúdo já conhecido. | F3 | Tarefa de demonstração libera percurso; manutenção permanece. |
| PED-005 | P1 | Personalizar temas sem remover o núcleo curricular. | F6 | Temas alteram exemplos sem excluir o núcleo curricular. |
| PED-006 | P0 | Definir cada unidade por uma capacidade comunicativa observável. | F0–F2 | Objetivo observável obrigatório no esquema e na revisão editorial. |
| PED-007 | P0 | Registrar pré-requisitos reais e permitir percursos alternativos. | F2–F3 | Pré-requisitos vinculados a capacidades, com razão de bloqueio. |
| PED-008 | P0 | Mapear objetivos a descritores de referência, quando pertinentes. | F0–F4 | Mapeamento editorial de descritores e condições, sem certificação. |
| PED-009 | P0 | Priorizar utilidade comunicativa e frequência contextual na seleção lexical. | F0–F4 | Justificativa editorial dos alvos em função da tarefa. |
| PED-010 | P0 | Ensinar palavras, sentidos específicos e expressões utilizáveis. | F2–F4 | Alvos por sentido/expressão, com áudio, significado e exemplos. |
| PED-011 | P0 | Integrar gramática a funções de comunicação. | F2 | Explicação breve ligada à função e à aplicação contextual. |
| PED-012 | P1 | Incluir adequação social, registro e estratégias de reparo. | F6; exemplos em F2 | Expandir registro e estratégias de reparo; pedir esclarecimento já cabe na missão inicial. |
| PED-013 | P1 | Separar o motor pedagógico dos currículos específicos de cada par linguístico. | F6; separação em F0 | Contratos sem inglês embutido no motor; novo par requer autoria e validação próprias. |
| PED-014 | P0 | Equilibrar compreensão, produção, estudo explícito e desenvolvimento de fluência. | F3–F4 | Compreensão e produção contínuas com repertório familiar em F3; revisão editorial verifica quatro vertentes e corrige ausências antes do piloto. |
| PED-015 | P0 | Usar conteúdo compreensível com apoio ajustável. | F2 | Apoios acessíveis e eventos explícitos de utilização. |
| PED-016 | P0 | Oferecer leitura e escuta com continuidade de sentido. | F2 | Diálogo/texto contínuo, sem pergunta obrigatória em cada frase. |
| PED-017 | P0 | Incluir recuperação ativa da memória. | F2 | Recuperação antes de revelar modelo ou solução. |
| PED-018 | P0 | Retirar apoios gradualmente conforme o desempenho. | F2–F3 | Política ajustável de retirada de apoio, sem número fixo universal. |
| PED-019 | P0 | Exigir produção adequada aos objetivos da unidade. | F2 | Produção escrita e oral conforme objetivo; revisão humana quando necessária. |
| PED-020 | P0 | Usar tarefas com resultado comunicativo. | F2 | Tarefa cujo critério inclui obter ou transmitir informação. |
| PED-021 | P0 | Testar transferência com variação relevante. | F2–F4 | Variantes inéditas e equivalência revisada de objetivo/dificuldade. |
| PED-022 | P1 | Misturar conteúdos já introduzidos de forma planejada. | F6 | Seleção editorial de mistura após introdução dos conteúdos. |
| PED-023 | P1 | Disponibilizar prática de fluência com conteúdo familiar. | F6; mínimo em F3 | Ampliar formatos e personalização; oportunidades mínimas de continuidade já cobrem PED-014 antes do piloto. |
| PED-024 | P1 | Propor uso fora do aplicativo. | F6 | Propostas externas e portfólio, distinguindo autorrelato de evidência. |
| PED-025 | P0 | Distinguir compreensão auditiva de leitura da transcrição. | F2 | Registro de transcrição; verificação auditiva sem revelação escrita. |
| PED-026 | P0 | Incluir percepção de contrastes sonoros relevantes e produção oral desde o percurso inicial. | F2–F4 | Contraste sonoro pertinente e produção oral nas primeiras missões. |
| PED-027 | P0 | Avaliar comunicação compreensível, não imitação obrigatória de um sotaque. | F2–F4 | Rubrica humana de comunicação compreensível, sem sotaque obrigatório. |
| PED-028 | P0 | Oferecer gravação e reprodução da própria voz, com privacidade configurável. | F2 | Gravar/reproduzir localmente, upload separado, autoavaliação identificada. |
| PED-029 | P1 | Variar falantes e condições auditivas de forma progressiva. | F6 | Diversidade planejada de vozes e avaliação com vozes não treinadas. |
| PED-030 | P0 | Separar incerteza de reconhecimento de voz de erro do aluno. | F2 | Estado de falha técnica/abstenção separado de erro linguístico. |
| PED-031 | P0 | Distribuir oportunidades de recuperação ao longo do tempo. | F3 | Recuperações entre sessões e manutenção posterior. |
| PED-032 | P0 | Representar memória e habilidade como estimativas atualizáveis. | F2–F3 | Histórico dimensional, sem booleano definitivo de aprendido. |
| PED-033 | P0 | Adaptar revisão à qualidade da evidência. | F3 | Agendamento distingue apoio, independência, correção imediata e falha técnica. |
| PED-034 | P0 | Limitar a carga de revisão ao orçamento escolhido pelo aluno. | F3 | Priorização por orçamento e explicação dos adiamentos. |
| PED-035 | P0 | Controlar a entrada de novos alvos conforme a carga futura. | F3 | Recomendação de carga nova conforme demanda futura, sem bloqueio artificial. |
| PED-036 | P0 | Reutilizar evidência obtida em tarefas integradas. | F3 | Evidência por alvo em tarefa integrada; sem recuperação redundante. |
| PED-037 | P0 | Intervir diante de erros persistentes. | F3 | Política de intervenção alternativa diante de recorrência de erro. |
| PED-038 | P0 | Tratar retorno após ausência sem punição. | F3 | Rechecagem amostral e retomada sem apagar histórico. |
| PED-039 | P1 | Calibrar o agendamento com dados de retenção real. | F6 | Comparar política inicial com alternativa usando retenção observada. |
| PED-040 | P0 | Oferecer feedback explicativo e acionável. | F2 | Feedback útil e nova oportunidade; prioridade de correção definida. |
| PED-041 | P0 | Aceitar respostas linguisticamente válidas além de um gabarito literal. | F2–F4 | Alternativas válidas, dimensões separadas e revisão de respostas desconhecidas. |
| PED-042 | P0 | Ajustar o momento e a quantidade de correção à atividade. | F2 | Momento/quantidade de feedback configurados por atividade. |
| PED-043 | P0 | Separar avanço curricular de retenção confirmada. | F2–F3 | Percurso e confirmação de retenção em estados distintos. |
| PED-044 | P0 | Definir rubricas e critérios de avanço por competência. | F0–F3 | Rubricas, política v0 e fixtures versionadas; avanço, retenção e contradição com decisões explícitas por dimensão essencial. |
| PED-045 | P0 | Mostrar progresso em capacidades observáveis. | F2–F3 | Painel mostra capacidade, apoio, data e procedência. |
| PED-046 | P1 | Oferecer contestação de correção e revisão editorial. | F6 | Contestação pelo aluno e revisão editorial; infraestrutura de revisão preserva histórico. |
| PED-047 | P0 | Manter gamificação subordinada à aprendizagem. | F2–F4 | Missões e progresso não exigem cota de telas nem pontuação artificial. |
| PED-048 | P0 | Não punir erros com perda de acesso à prática. | F2–F4 | Erro oferece apoio; não compra obrigatória de vidas. |
| PED-049 | P0 | Permitir metas flexíveis e retorno acolhedor. | F3 | Meta flexível e retorno sem degradação punitiva. |
| PED-050 | P0 | Oferecer uma sessão com fim claro e escolha sobre continuar. | F2 | Resumo com encerramento claro e escolha de continuar. |
| PED-051 | P0 | Prever acessibilidade e modalidades alternativas sem falsear medidas. | F1–F4 | Acessibilidade, alternativas e modalidade de evidência correta. |
| PED-052 | P1 | Usar narrativa, conquistas e cooperação como recursos opcionais. | F6 | Narrativa e conquistas opcionais; não depender de ranking. |
| PED-053 | P0 | Revisar e versionar todo conteúdo curricular publicado. | F0–F4 | Autoria/procedência, revisão e publicação versionada com critérios obrigatórios. |
| PED-054 | P0 | Manter o percurso essencial utilizável sem dependência de conversa generativa paga. | F2 | Núcleo independente de chatbot e de geração paga. |
| PED-055 | P1 | Confinar geração e feedback de IA aos objetivos e limites do curso. | F6 | Integração limitada, avaliação calibrada e conteúdo gerado não publicado automaticamente. |
| PED-056 | P0 | Validar avaliações automáticas contra julgamentos humanos. | F2–F4; expansão F6 | Auditar correções automáticas limitadas; decisões abertas humanas até validar automação. |
| PED-057 | P0 | Registrar somente dados necessários para aprendizagem e validação. | F1–F4 | Finalidade, retenção, controle de acesso, exportação/exclusão e upload deliberado. |
| PED-058 | P0 | Separar bancos de treino e avaliação. | F0–F4 | Bancos separados e controle da exposição prévia de cada variante. |
| PED-059 | P0 | Medir retenção posterior e transferência. | F4–F4b | Quatro formas reservadas e protocolo congelado; coleta posterior com intervalos reais, exposições e manutenção com prática continuada explicitadas. |
| PED-060 | P0 | Medir eficácia, eficiência e experiência separadamente. | F4–F4b | Relatório separado de aprendizagem, esforço, experiência, desistências e incerteza. |
| PED-061 | P1 | Comparar com uma alternativa pedagógica plausível e controlar diferenças de exposição. | F6 | Comparação planejada com alternativa ativa e controle de exposição. |
| PED-062 | P1 | Pré-definir resultados, critérios e limites de interpretação dos experimentos. | F0–F4; expansão em F6 | Protocolo exploratório com desfecho, calendário, perdas, análise e decisões congelados antes da coleta; desenho confirmatório posterior próprio. |

## 20. Referências e procedência

**[P] Base fornecida:** `BASE_PEDAGOGICA_APP_IDIOMAS_v0.1.md`, datada de 4 de novembro de 2026. Os requisitos, prioridades, ressalvas científicas e objetivos pedagógicos provêm desse documento. As referências E01–E17 ali citadas não foram refeitas como nova revisão bibliográfica neste plano técnico.

**Referências técnicas oficiais consultadas em 4 de novembro de 2026.** Elas sustentam as características das ferramentas citadas; não demonstram eficácia do produto nem superioridade desta stack. Endereços são apresentados em formato de código para permitir consulta independente.

| Código | Fonte | Endereço |
|---|---|---|
| T1 | Android Developers — Recommendations for Android architecture | `https://developer.android.com/topic/architecture/recommendations` |
| T2 | Android Developers — Build an offline-first app | `https://developer.android.com/topic/architecture/data-layer/offline-first` |
| T3 | Android Developers — Media3 ExoPlayer | `https://developer.android.com/media/media3/exoplayer` |
| T4 | Android Developers — MediaRecorder overview | `https://developer.android.com/media/platform/mediarecorder` |
| T5 | Laravel 13 — Release notes e política de suporte | `https://laravel.com/docs/13.x/releases` |
| T6 | Laravel 13 — Starter kits | `https://laravel.com/docs/13.x/starter-kits` |
| T7 | Laravel 13 — Frontend | `https://laravel.com/framework/docs/frontend` |
| T8 | Laravel 13 — Sanctum | `https://laravel.com/docs/13.x/sanctum` |
| T9 | Android Developers — SpeechRecognizer | `https://developer.android.com/reference/android/speech/SpeechRecognizer` |
| T10 | Flutter — FAQ | `https://docs.flutter.dev/resources/faq` |
| T11 | Flutter — Web FAQ | `https://docs.flutter.dev/platform-integration/web/faq` |
| T12 | Kotlin Multiplatform — FAQ | `https://kotlinlang.org/docs/multiplatform/faq.html` |
| T13 | Laravel 13 — Queues | `https://laravel.com/docs/13.x/queues` |
| T14 | Android Developers — Security checklist | `https://developer.android.com/privacy-and-security/security-tips` |
| T15 | OpenAI Developers — Iterating Development Workflows with Codex | `https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex` |
| T16 | MDN — MediaDevices.getUserMedia | `https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia` |

Para F5, a captura web deve tratar permissão e contexto seguro exigidos por `getUserMedia`, além dos resultados dos testes nos navegadores escolhidos. Essa verificação não substitui os testes de formato e reprodução das gravações. [T16]

---

**Próxima entrega concreta recomendada:** especificar F0 com missão completa, rubrica, exemplos de evidência, política v0, fixtures e protocolo formativo; iniciar a fundação técnica F1 enquanto revisão humana e teste formativo são preparados. Concluir a validação de F0 e tratar seus achados antes de encerrar F2. O objetivo é demonstrar um ciclo coerente de conteúdo, áudio, tentativa, feedback, evidência e revisão, dentro da capacidade humana e dos limites de investimento registrados.
