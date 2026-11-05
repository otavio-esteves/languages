# Aplicativo de idiomas — base pedagógica e requisitos v0.1

**Data:** 4 de novembro de 2026  
**Status:** proposta inicial para discussão, prototipagem e validação; não é um método já comprovado como conjunto.  
**Recorte de trabalho:** adultos falantes de português brasileiro aprendendo inglês. É uma hipótese de escopo inicial, não uma decisão irreversível.  
**Fora deste documento:** escolha definitiva entre Android/Kotlin e web/Laravel, arquitetura de software, orçamento de serviços e análise jurídica.

**Revisão de planejamento:** 4 de novembro de 2026. Acrescenta condições operacionais para progressão, fluência e piloto; preserva os 62 requisitos e suas prioridades. Os parâmetros iniciais são hipóteses de produto, não novos resultados científicos.

**Estado da execução:** a especificação e a fundação técnica estão registradas em [F0](milestones/F0.md) e [F1](milestones/F1.md). Este documento continua como referência de planejamento; não certifica conclusão dos requisitos.

## 1. Objetivo do produto

Ajudar a pessoa a compreender e usar o idioma em situações novas, conservar o que aprendeu e perceber avanços reais, com uma carga de prática sustentável. Não otimizar o produto para aumentar artificialmente tempo de tela, quantidade de lições ou repetição de conteúdo já demonstrado.

O problema a resolver não é a existência de repetição. É a prática sem objetivo identificável, sem adaptação à necessidade do aluno e sem correspondência com capacidades fora do exercício. A redução de repetições será desejável quando não sacrificar retenção, transferência e fluência.

A unidade de progresso será uma **capacidade demonstrada em determinadas condições**, não uma tela concluída. Avançar no currículo e confirmar retenção serão estados distintos: o aluno poderá seguir aprendendo enquanto capacidades anteriores recebem manutenção proporcional.

## 2. Como interpretar a base científica

Foi realizado um levantamento dirigido de estudos originais, meta-análises e fontes oficiais. Foram consultados textos abertos, páginas editoriais e resumos quando o texto integral não estava disponível. Isso não constitui uma revisão sistemática exaustiva de toda a literatura.

Neste documento, **evidência** designa o resultado relatado por uma fonte; **princípio de desenho** é uma forma de organizar o curso; **decisão de produto** é uma proposta nossa, que exige teste. A existência de evidência favorável a uma técnica não demonstra que qualquer implementação dessa técnica, nem esta combinação completa, será superior.

Os efeitos de estudos distintos não devem ser ordenados como um ranking simples: populações, comparadores, medidas e duração diferem. Evidência sobre memorização de palavras, por exemplo, não prova aquisição de conversação espontânea.


### Matriz resumida de evidências

| Código | Resultado relevante | Limite de interpretação | Implicação proposta |
|---|---|---|---|
| E01 | Kim e Webb (2022): a prática espaçada apresentou benefício médio a grande em aprendizagem de segunda língua. | Não determina um calendário universal nem demonstra superioridade geral de intervalos crescentes sobre iguais. | Revisões distribuídas e calibradas, sem uma sequência fixa anunciada como ótima. |
| E02 | Karpicke e Roediger (2008): recuperar vocabulário da memória favoreceu recordação posterior no experimento. | Um experimento de vocabulário não valida um curso completo. | Solicitar tentativa independente antes de revelar a resposta. |
| E03 | O QECR/CEFR organiza descritores de capacidades e recomenda alinhamento entre objetivos, atividades e avaliação. | É um referencial, não um algoritmo de ensino nem certificação automática. | Planejar unidades por tarefas e registrar perfis por habilidade. |
| E04 | Nation (2007) propõe equilibrar compreensão, produção, estudo explícito e desenvolvimento de fluência. | A divisão aproximada em quatro partes é uma orientação curricular, não proporção ótima comprovada para todos. | Evitar um curso composto quase inteiramente por exercícios de forma. |
| E05 | Rice e Tokowicz (2020; publicação online em 2019) revisam treino de vocabulário adulto e destacam combinações de espaçamento, recuperação e elaboração semântica. | Predominam contextos laboratoriais; generalização requer cuidado. | Combinar significado, forma e uso, em vez de depender só de releitura e tradução repetida. |
| E06 | Spada e Tomita (2010) encontraram vantagem da instrução explícita nos estudos de características gramaticais analisados. | Isso não significa que longas aulas teóricas sejam o formato mais eficiente do aplicativo. | Explicações breves, ligadas a exemplos e aplicação. |
| E07 | Lyster e Saito (2010) encontraram efeitos favoráveis e duráveis de feedback oral corretivo em estudos de sala de aula. | O formato e o momento ótimo de correção não são idênticos em todas as tarefas. | Corrigir de maneira útil, sem interromper indiscriminadamente a comunicação. |
| E08 | Bryfonski e McKay (2019) encontraram resultados positivos na implementação de ensino baseado em tarefas. | Estudos de programas não comprovam eficácia equivalente de qualquer simulação automática. | Organizar prática em torno de resultados comunicativos. |
| E09 | Sangers e colaboradores (2025) encontraram benefícios pequenos a médios da leitura extensiva em diferentes resultados. | Ler muito não garante sozinho a mesma evolução em todas as habilidades. | Oferecer material graduado e tempo de leitura contínua. |
| E10 | Kurokawa, Hein e Uchihara (2025) encontraram vantagem de vídeos com legendas na língua-alvo para aprendizagem incidental de vocabulário. | O resultado não equivale a escuta independente já dominada. | Disponibilizar apoio escrito e também verificar escuta sem ele. |
| E11 | Uchihara, Karas e Thomson (2025) encontraram benefícios de treino fonético de alta variabilidade para percepção de sons. | Não prova que um exercício perceptivo resolve toda a produção oral ou a conversação. | Incluir discriminação auditiva e diversidade planejada de modelos. |
| E12 | Saito e Plonsky (2019) mostram que os resultados do ensino de pronúncia dependem da medida; Saito (2021) diferencia compreensibilidade e semelhança a nativos. | Melhorar sons em produção controlada não garante a mesma mudança na fala espontânea. | Avaliar comunicação real e não apenas repetição de frases. |
| E13 | Ngo, Chen e Lai (2024; online em 2023) encontraram benefícios do reconhecimento de fala como apoio ao treino de pronúncia. | Benefício de uso não torna uma transcrição medida suficiente ou infalível de proficiência. | Usar automação com calibração, possibilidade de abstenção e avaliação humana no piloto. |
| E14 | Sailer e Homner (2020) encontraram efeitos favoráveis de gamificação, com variação entre resultados e estudos. | Não sustenta que uma mecânica específica, como sequência diária ou ranking, seja necessária. | Testar gamificação que apoie autonomia e progresso, não prática artificial. |
| E15 | Nakata e Suzuki (2019) investigaram vantagens da mistura de exercícios para retenção gramatical posterior. | Resultados de gramática não justificam misturar aleatoriamente qualquer conteúdo desde o início. | Introduzir contraste e mistura depois de estabelecer uma base inicial. |
| E16 | Saarela, Gunasekara e Kumarage (2026) encontraram resultados promissores para IA generativa na aprendizagem de línguas. | Heterogeneidade e uso frequente de medidas de curto prazo ou autorrelato limitam conclusões sobre retenção. | IA como recurso supervisionado, não como prova de eficácia nem juiz único. |
| E17 | Mihaylova e colaboradores (2022) encontraram resultados positivos de aplicativos, acompanhados de risco alto de viés e qualidade baixa da evidência. | Não autoriza afirmar que um novo aplicativo será melhor do que alternativas existentes. | Avaliar este produto com medidas independentes e desenho comparativo adequado. |

## 3. Estrutura curricular proposta

Usar descritores de capacidades como referência, sem converter automaticamente capítulos em níveis oficiais. As etapas abaixo são um desenho de produto inspirado no QECR, não uma reprodução literal de sua escala. A conclusão de um pequeno módulo não equivale à conclusão de todo A1 ou A2.

| Etapa | Foco proposto | Exemplos de capacidades |
|---|---|---|
| Entrada e fundamentos | Diagnóstico e comunicação inicial com apoio. | Apresentar-se, reconhecer informações pessoais, pedir repetição, relacionar formas escritas e faladas. |
| Interações básicas | Tarefas previsíveis com ajuda progressivamente menor. | Fazer um pedido simples, perguntar preço, identificar horários, escrever uma mensagem curta. |
| Conexão de ideias | Mensagens e interações com mais de um propósito. | Descrever rotina, contar um evento simples, combinar um compromisso, explicar uma necessidade. |
| Autonomia cotidiana | Mais imprevisibilidade e mais continuidade de discurso. | Resolver um problema comum, relatar experiências, explicar uma preferência e acompanhar conteúdo acessível. |
| Expansão e especialização | Complexidade, nuance, repertório e adequação. | Argumentar, negociar, compreender perspectivas diferentes e atuar em temas de interesse ou profissão. |

No primeiro piloto, construir somente um recorte de capacidades iniciais. Manter leitura, escuta, fala e escrita presentes, com diferentes níveis de apoio. Não estabelecer meses obrigatórios apenas de compreensão antes de permitir produção, nem exigir fala livre complexa sem preparação.


### Seleção de conteúdo

As escolhas de vocabulário e gramática devem servir às tarefas. Exemplo: uma missão sobre comprar uma bebida pode integrar nomes relevantes, quantidades, expressões de pedido, compreensão de uma pergunta e confirmação de uma escolha. Não precisa ensinar uma lista extensa de alimentos antes de qualquer interação.

Expressões como `I'd like...`, `Could you repeat that?` e `What does ... mean?` são exemplos de alvos utilizáveis. Elas devem ser acompanhadas de variação de sentido e de contexto, e não tratadas como roteiros cuja memorização equivale a conversação.

O português poderá apoiar instruções, diferenças de significado e explicações. A retirada de apoio será orientada pelo desempenho e pelo objetivo da atividade, não por uma proibição ideológica da língua de origem.

## 4. Ciclo de aprendizagem de uma missão

**Exemplo autoral:** pedir uma bebida e esclarecer uma informação. O tempo e a quantidade de exercícios serão parâmetros de protótipo, não resultados científicos já estabelecidos.

| Momento | Experiência proposta | O que observar |
|---|---|---|
| Propósito | O aluno vê a situação e a capacidade que será praticada. | Entende o objetivo; pode demonstrar que já consegue realizá-lo. |
| Compreensão | Acompanha um diálogo curto com ajuda disponível. | Compreende intenção, item e informação central; os apoios utilizados ficam registrados. |
| Atenção ao alvo | Recebe uma explicação curta de uma expressão ou distinção necessária. | Relaciona a forma à intenção, sem precisar decorar terminologia. |
| Recuperação | Produz uma parte da mensagem sem o modelo visível. | Lembra com ou sem dica; o sistema distingue os dois casos. |
| Tarefa | Faz um pedido com item ou tamanho diferente e responde a uma pergunta. | Cumpre o objetivo e lida com uma variação não memorizada. |
| Feedback e nova tentativa | Recebe uma correção prioritária e experimenta novamente. | Usa a orientação, sem confundir sucesso imediatamente apoiado com retenção durável. |
| Consolidação posterior | Reencontra o alvo em outra sessão e em uma situação compatível. | Mantém compreensão e uso depois de um intervalo. |

Não é necessário executar todos os momentos em toda sessão. Uma sessão pode privilegiar uma história, uma tarefa de produção ou uma revisão. O equilíbrio deve ser observado em uma sequência de experiências.

## 5. Progressão e representação do aprendizado

Registrar separadamente **percurso** e **evidência de capacidade**. O percurso informa quais situações foram exploradas; a evidência informa o que o aluno consegue fazer, em qual modalidade, com que apoio e em qual data.

Um vocabulário de estados possível é: ainda não avaliado; exposto; reconhece com apoio; recupera de modo independente; usa em tarefa; retenção posterior observada. Esses estados são rótulos de produto para descrever evidências, não uma teoria de estágios universais. Habilidades podem evoluir de modo não linear, e um lapso isolado não deve apagar todo o histórico.

A progressão deve considerar múltiplas oportunidades pertinentes, incluindo tarefas com variação. O número mínimo de evidências, os limiares e os intervalos precisam ser calibrados. Não instituir uma regra universal como “três acertos = aprendido”, “90% = fluente” ou “uma palavra conhecida = todos os seus sentidos conhecidos”.

A pessoa que lê uma expressão, mas não a entende ao ouvir, deve receber apoio auditivo direcionado. A pessoa que conhece o conteúdo não deve ser mantida artificialmente em uma unidade até cumprir uma cota de telas.

Para tornar essas regras implementáveis, cada capacidade terá uma rubrica versionada que declare dimensões essenciais, condições de independência e evidência suficiente para avanço inicial. A primeira política operacional está especificada no plano de desenvolvimento, §8: uma tarefa adequada pode permitir avanço; retenção exige uma oportunidade posterior independente e temporalmente válida. Esse registro descreve a observação e seu intervalo, sem declarar domínio permanente. Evidências contraditórias preservam o histórico, reduzem a certeza atual e orientam nova verificação.

## 6. Política de revisão que não aprisiona o aluno

A revisão terá um orçamento de esforço definido com o aluno. Se houver mais demanda do que capacidade, priorizar alvos úteis, frágeis ou necessários à próxima tarefa, explicitar os adiamentos e ajustar a quantidade de novidades. Limitar a carga não elimina a necessidade de prática; apenas exige fazer escolhas transparentes.

Revisar uma capacidade não exige sempre mostrar o mesmo cartão. Uma tarefa comunicativa pode gerar uma oportunidade válida de recuperação. Ao mesmo tempo, uma história na qual uma palavra apenas apareceu não demonstra que o aluno consegue produzi-la.

O agendamento deve distinguir acerto independente, acerto com apoio, erro, resposta imediatamente após correção e problema técnico. Tempo de resposta pode ser um sinal auxiliar, nunca uma punição automática: digitação, acessibilidade, ruído e contexto de uso precisam ser considerados.

O acesso a modelo, dica, solução ou transcrição relevante deve ser registrado mesmo se o aluno abandonar a atividade sem enviar resposta. Essa exposição pode alterar a interpretação de uma tentativa posterior, mas não equivale a erro ou demonstração de capacidade. Apoios têm escopo explícito por alvo e modalidade; não promovem outras habilidades automaticamente.

Diante de dificuldades persistentes, mudar a intervenção: esclarecer o significado, reduzir a carga, ensinar um pré-requisito, contrastar exemplos ou variar a modalidade. Não resolver todo erro com a mesma pergunta repetida indefinidamente.

Depois de uma ausência, reavaliar uma amostra relevante e oferecer um caminho de retomada. Não decretar que tudo foi esquecido, nem manter certeza de domínio onde já não há evidência recente.

Não fixar um calendário supostamente científico de 1–3–7–14 dias. Uma regra inicial simples pode ser usada como hipótese operacional, desde que seja ajustável e avaliada contra retenção real. O melhor agendamento de cartões também não substitui currículo, exposição significativa e comunicação.

## 7. Requisitos e critérios de aceite

**P0:** indispensável para a integridade do MVP pedagógico ou de sua avaliação; pode ser implementado de forma simples ou manual no piloto.  
**P1:** expansão ou sofisticação a acrescentar depois de validar o ciclo central.  
**Base:** referência relacionada ou indicação explícita de que se trata de uma decisão de produto/pesquisa. Uma referência não valida automaticamente todo o critério de aceite. Muitos requisitos são regras do mesmo componente, não telas ou funcionalidades independentes; o piloto pode verificá-los com apoio manual.


### Diagnóstico e personalização

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-001 | P0 | Coletar objetivos, idioma de origem, experiência prévia, disponibilidade e condições de uso. | O aluno pode indicar que não pode ouvir ou falar naquele momento, sem receber penalidade nem evidência fictícia nessas habilidades. | Decisão de produto |
| PED-002 | P0 | Oferecer diagnóstico inicial opcional, breve e progressivo. | É possível começar sem prova longa; a estimativa inicial exibe incerteza e é atualizada por desempenho posterior. | Decisão de produto; E03 |
| PED-003 | P0 | Manter um perfil por habilidade, não apenas um nível global. | Acertar leitura não promove automaticamente escuta, produção oral ou escrita; habilidades não avaliadas ficam explicitamente sem evidência. | E03; E04 |
| PED-004 | P0 | Permitir demonstrar conhecimentos e pular conteúdo já conhecido. | Uma tarefa inédita adequada ao objetivo pode liberar o próximo conteúdo; itens ainda incertos permanecem disponíveis para verificação posterior. | Decisão de produto |
| PED-005 | P1 | Personalizar temas sem remover o núcleo curricular. | Escolher trabalho ou viagens muda exemplos e missões, mas não elimina objetivos essenciais identificados para a trilha. | Decisão de produto |

### Currículo e conteúdo

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-006 | P0 | Definir cada unidade por uma capacidade comunicativa observável. | Toda unidade informa o que o aluno deverá conseguir fazer e contém uma tarefa que permita observar esse resultado. | E03; E08 |
| PED-007 | P0 | Registrar pré-requisitos reais e permitir percursos alternativos. | Um bloqueio de progresso precisa indicar a capacidade ausente; não pode depender exclusivamente de completar uma quantidade de telas. | Decisão de produto |
| PED-008 | P0 | Mapear objetivos a descritores de referência, quando pertinentes. | O mapeamento ao QECR/CEFR identifica habilidade e condições de execução; não é apresentado como certificação oficial. | E03 |
| PED-009 | P0 | Priorizar utilidade comunicativa e frequência contextual na seleção lexical. | O autor justifica a escolha das palavras e expressões em função das tarefas, sem substituir o currículo por uma lista genérica de frequência. | Decisão de produto |
| PED-010 | P0 | Ensinar palavras, sentidos específicos e expressões utilizáveis. | O material inclui forma escrita, áudio, significado contextual e exemplos; dominar um sentido não declara todos os sentidos dominados. | E05; decisão de produto |
| PED-011 | P0 | Integrar gramática a funções de comunicação. | Uma explicação gramatical é seguida por compreensão e uso em contexto, não apenas por seleção do nome de uma regra. | E06 |
| PED-012 | P1 | Incluir adequação social, registro e estratégias de reparo. | O curso ensina a pedir repetição, esclarecer, confirmar entendimento e reformular; uma variante legítima não é tratada como erro. | E03 |
| PED-013 | P1 | Separar o motor pedagógico dos currículos específicos de cada par linguístico. | Uma nova língua exige revisão de escrita, sons, estrutura e uso; traduzir os enunciados do curso de inglês não basta para publicá-la. | Decisão de produto |

### Atividades e prática

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-014 | P0 | Equilibrar compreensão, produção, estudo explícito e desenvolvimento de fluência. | Um relatório por sequência de unidades identifica lacunas de experiência; não exige uma distribuição rígida em cada sessão. | E04 |
| PED-015 | P0 | Usar conteúdo compreensível com apoio ajustável. | O aluno pode acessar significado, exemplos ou transcrição; o sistema registra esse apoio e não confunde uso apoiado com desempenho independente. | E04; E09; E10 |
| PED-016 | P0 | Oferecer leitura e escuta com continuidade de sentido. | Há histórias, mensagens e diálogos que podem ser acompanhados sem interromper cada frase com uma questão. | E09; decisão de produto |
| PED-017 | P0 | Incluir recuperação ativa da memória. | Depois da exposição inicial, há tarefas em que a resposta-alvo não está visível e o aluno tenta recuperá-la antes de receber ajuda. | E01; E02 |
| PED-018 | P0 | Retirar apoios gradualmente conforme o desempenho. | Uma sequência pode passar de reconhecimento a completamento e produção, mas o número de etapas não é fixo para todos. | Decisão de produto; E05 |
| PED-019 | P0 | Exigir produção adequada aos objetivos da unidade. | Uma unidade que promete expressão oral inclui produção oral; ordenar palavras não é a única evidência de produção independente. | E03; E04 |
| PED-020 | P0 | Usar tarefas com resultado comunicativo. | O aluno precisa obter, transmitir ou negociar informação; a conclusão depende do sentido comunicado, não só de repetir um modelo. | E08 |
| PED-021 | P0 | Testar transferência com variação relevante. | A checagem utiliza novos detalhes ou contexto, mantendo dificuldade compatível; não é apenas o mesmo item com alternativas reordenadas. | Decisão de produto |
| PED-022 | P1 | Misturar conteúdos já introduzidos de forma planejada. | Exercícios mistos exigem selecionar a forma adequada ao sentido; o sistema não embaralha indiscriminadamente material ainda desconhecido. | E15 |
| PED-023 | P1 | Disponibilizar prática de fluência com conteúdo familiar. | Há oportunidades de tornar a compreensão ou produção mais fácil e contínua, sem impor cronômetros punitivos ou exigir vocabulário novo. | E04 |
| PED-024 | P1 | Propor uso fora do aplicativo. | A atividade externa tem objetivo claro e pode gerar reflexão ou portfólio; um autorrelato não é convertido automaticamente em domínio comprovado. | Decisão de produto |

**Cobertura mínima de fluência no P0:** PED-014 exige, antes do piloto, oportunidades de compreensão ou produção contínua com repertório já apresentado, dentro das missões. A sequência inicial deve oferecer ao menos uma oportunidade de compreensão contínua e uma de produção contínua, com apoio opcional registrado, sem novo alvo obrigatório nem cronômetro punitivo. A revisão editorial verifica as quatro vertentes na sequência e corrige ausências. PED-023 permanece P1 para ampliar formatos e personalização dessa prática; sua prioridade não adia a cobertura mínima de PED-014.

### Escuta, fala e pronúncia

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-025 | P0 | Distinguir compreensão auditiva de leitura da transcrição. | Um acerto com texto visível é registrado como apoiado por texto; uma prova de escuta independente usa material novo sem revelar a resposta por escrito. | E10; decisão de produto |
| PED-026 | P0 | Incluir percepção de contrastes sonoros relevantes e produção oral desde o percurso inicial. | O conteúdo de sons é integrado às necessidades comunicativas, sem bloquear toda comunicação até uma pronúncia ideal. | E11; E12 |
| PED-027 | P0 | Avaliar comunicação compreensível, não imitação obrigatória de um sotaque. | A rubrica distingue entendimento da mensagem, aspectos linguísticos e características de pronúncia; variantes compreensíveis não são punidas por origem. | E12; decisão de produto |
| PED-028 | P0 | Oferecer gravação e reprodução da própria voz, com privacidade configurável. | O aluno pode comparar sua produção com modelos; autoavaliação é identificada como tal e não substitui validação independente. | Decisão de produto |
| PED-029 | P1 | Variar falantes e condições auditivas de forma progressiva. | A avaliação inclui vozes não usadas no treino; diferenças de voz, velocidade e sotaque são introduzidas sem mudar todos os fatores simultaneamente. | E11; decisão de produto |
| PED-030 | P0 | Separar incerteza de reconhecimento de voz de erro do aluno. | Áudio incompleto, ruído ou transcrição incerta permitem nova tentativa ou abstenção do avaliador e não geram reprovação automática. | E13; decisão de produto |

### Memória e revisão

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-031 | P0 | Distribuir oportunidades de recuperação ao longo do tempo. | Um acerto imediato não elimina toda revisão posterior, e todas as repetições de um alvo não ficam concentradas na mesma sessão. | E01; E02 |
| PED-032 | P0 | Representar memória e habilidade como estimativas atualizáveis. | O histórico distingue exposição, reconhecimento, recuperação independente e uso em tarefa; não existe um único booleano definitivo de palavra aprendida. | Decisão de produto; E05 |
| PED-033 | P0 | Adaptar revisão à qualidade da evidência. | Acerto com dica, acerto independente, erro e falha técnica recebem tratamentos distintos; repetir após ver a solução não equivale a lembrar dias depois. | E01; E02; decisão de produto |
| PED-034 | P0 | Limitar a carga de revisão ao orçamento escolhido pelo aluno. | Quando a demanda excede a capacidade, o sistema prioriza e informa o que foi adiado; não exige quitar toda a fila para continuar o curso. | Decisão de produto |
| PED-035 | P0 | Controlar a entrada de novos alvos conforme a carga futura. | O sistema pode recomendar menos novidades e explicar a razão; essa recomendação não cria bloqueio comercial nem ocultação de conteúdo. | Decisão de produto |
| PED-036 | P0 | Reutilizar evidência obtida em tarefas integradas. | Uma recuperação válida de um alvo em diálogo pode atualizar seu histórico, evitando um cartão extra redundante; mera exposição não conta como recuperação. | Decisão de produto |
| PED-037 | P0 | Intervir diante de erros persistentes. | Depois de falhas recorrentes, muda-se a explicação, o apoio ou o pré-requisito; o sistema não entra em repetição idêntica ilimitada. | Decisão de produto |
| PED-038 | P0 | Tratar retorno após ausência sem punição. | O sistema faz rechecagem proporcional e monta uma retomada viável; não apaga todo o progresso nem assume que toda memória foi preservada. | Decisão de produto |
| PED-039 | P1 | Calibrar o agendamento com dados de retenção real. | A política de intervalos é comparada a uma regra simples em testes posteriores; não se anuncia um algoritmo universalmente ótimo. | E01; decisão de produto |

### Feedback e progressão

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-040 | P0 | Oferecer feedback explicativo e acionável. | O retorno indica o problema relevante, apresenta uma orientação compreensível e oferece nova oportunidade de uso. | E07 |
| PED-041 | P0 | Aceitar respostas linguisticamente válidas além de um gabarito literal. | O avaliador diferencia cumprimento da tarefa, forma-alvo demonstrada, erro linguístico, registro e variação legítima. | Decisão de produto |
| PED-042 | P0 | Ajustar o momento e a quantidade de correção à atividade. | Prática focalizada pode corrigir imediatamente; tarefas de comunicação podem preservar o turno e comentar depois. Essa política é testada, não tratada como lei universal. | E07; decisão de produto |
| PED-043 | P0 | Separar avanço curricular de retenção confirmada. | O aluno pode avançar após demonstrar capacidade inicial; o estado de retenção só muda com evidência posterior, sem obrigar espera de dias para desbloquear tudo. | Decisão de produto |
| PED-044 | P0 | Definir rubricas e critérios de avanço por competência. | Uma nota global não mascara uma dimensão essencial não demonstrada; limiares são versionados e tratados como hipóteses a calibrar. | E03; decisão de produto |
| PED-045 | P0 | Mostrar progresso em capacidades observáveis. | O painel informa o que foi demonstrado, com qual apoio e quando; não converte automaticamente pontos, presença ou número de telas em proficiência. | E03; decisão de produto |
| PED-046 | P1 | Oferecer contestação de correção e revisão editorial. | O aluno pode reportar uma resposta válida rejeitada; a revisão preserva o histórico e evita penalidade injustificada. | Decisão de produto |

### Motivação e acessibilidade

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-047 | P0 | Manter gamificação subordinada à aprendizagem. | O objetivo curricular não muda para aumentar sessões ou pontos; a habilidade demonstrada libera avanço sem cota artificial de exercícios. | E14; decisão de produto |
| PED-048 | P0 | Não punir erros com perda de acesso à prática. | Errar pode gerar apoio ou replanejamento, nunca compra obrigatória de vidas ou bloqueio do conteúdo essencial. | Decisão de produto |
| PED-049 | P0 | Permitir metas flexíveis e retorno acolhedor. | Há uma opção de continuidade sem sequência diária obrigatória; ausência não remove competências já registradas. | Decisão de produto |
| PED-050 | P0 | Oferecer uma sessão com fim claro e escolha sobre continuar. | O resumo informa objetivo trabalhado, evidências e próximos passos; não encadeia infinitamente obrigações artificiais. | Decisão de produto |
| PED-051 | P0 | Prever acessibilidade e modalidades alternativas sem falsear medidas. | Texto, áudio, tamanho de fonte e controles são ajustáveis; uma alternativa escrita não é registrada como prova de habilidade oral. | Decisão de produto |
| PED-052 | P1 | Usar narrativa, conquistas e cooperação como recursos opcionais. | É possível estudar sem ranking; recompensas valorizam conquistas significativas e não a exploração repetitiva de tarefas fáceis. | E14; decisão de produto |

### Qualidade, IA e dados

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-053 | P0 | Revisar e versionar todo conteúdo curricular publicado. | Cada item registra objetivo, resposta/rubrica, explicação, autoria ou procedência, situação de uso autorizada e revisão linguística. | Decisão de produto |
| PED-054 | P0 | Manter o percurso essencial utilizável sem dependência de conversa generativa paga. | O núcleo possui materiais e tarefas estáveis; falha da IA não impede estudo, revisão e acesso às explicações essenciais. | Decisão de produto |
| PED-055 | P1 | Confinar geração e feedback de IA aos objetivos e limites do curso. | A IA recebe objetivos, nível estimado, apoios e critérios; conteúdo gerado não vira automaticamente item validado de avaliação. | E16; decisão de produto |
| PED-056 | P0 | Validar avaliações automáticas contra julgamentos humanos. | Antes de decisões relevantes sobre fala livre, mede-se acordo com avaliadores e padrões de erro; sem validação, o retorno é explicitamente provisório. | E12; E13; decisão de produto |
| PED-057 | P0 | Registrar somente dados necessários para aprendizagem e validação. | Áudio, tentativas e consentimentos têm finalidade explícita, controles de retenção e exclusão; dados de prática não viram uso secundário oculto. | Decisão de produto; não constitui análise jurídica |

### Validação de aprendizagem

| ID | Prioridade | Requisito | Critério de aceite | Base |
|---|---|---|---|---|
| PED-058 | P0 | Separar bancos de treino e avaliação. | As tarefas de avaliação não são cópias das atividades praticadas; há equivalência planejada de objetivo e dificuldade. | Decisão de pesquisa |
| PED-059 | P0 | Medir retenção posterior e transferência. | O piloto inclui avaliações após um intervalo e tarefas inéditas; o relatório distingue desempenho imediato de conhecimento retido. | E01; E02; decisão de pesquisa |
| PED-060 | P0 | Medir eficácia, eficiência e experiência separadamente. | O relatório informa resultados por habilidade, tempo efetivo de prática, abandono, fadiga e correções contestadas; uso diário não substitui aprendizagem. | E17; decisão de pesquisa |
| PED-061 | P1 | Comparar com uma alternativa pedagógica plausível e controlar diferenças de exposição. | A avaliação comparativa registra nível inicial, tempo, conteúdo, prática externa, desistências e incerteza; diferenças não são atribuídas ao método sem desenho compatível. | Decisão de pesquisa |
| PED-062 | P1 | Pré-definir resultados, critérios e limites de interpretação dos experimentos. | O protocolo informa desfecho principal, calendário, plano de análise e alcance da amostra; um piloto pequeno não fundamenta promessa de superioridade universal. | Decisão de pesquisa |

## 8. Casos de teste pedagógico transversais

**Reconhece, mas não produz.** Um aluno acerta questões de escolha, mas não consegue formular a mensagem. Resultado esperado: leitura/reconhecimento continuam registrados; a produção recebe prática específica e não é declarada dominada.

**Aprende rápido.** Um aluno já consegue realizar a tarefa da unidade sem ajuda. Resultado esperado: pode avançar; uma verificação posterior é agendada sem obrigá-lo a cumprir a mesma série de exercícios de um iniciante.

**Erra persistentemente.** O aluno falha repetidamente em uma distinção. Resultado esperado: surge uma intervenção diferente ou um pré-requisito, não um ciclo ilimitado de repetição idêntica.

**Retorna depois de semanas.** Resultado esperado: retomada proporcional, orçamento respeitado e rechecagem de memória; ausência não vira punição nem apagamento automático do histórico.

**Usa uma resposta alternativa válida.** Resultado esperado: o sistema reconhece o objetivo comunicativo quando cumprido e, quando necessário, informa que a forma específica do exercício ainda não foi demonstrada.

**O reconhecimento de voz falha.** Resultado esperado: o sistema registra incerteza técnica, permite outra tentativa e não reduz competência linguística com base apenas nessa falha.

**Não pode falar naquele momento.** Resultado esperado: pode continuar em outra modalidade; produção oral permanece pendente ou não avaliada, sem nota inventada.

**Recebe uma dica.** Resultado esperado: o acerto apoiado é reconhecido como progresso de prática, mas não vale como a mesma evidência de uma resposta independente posterior.

**Conclui uma sequência curta.** Resultado esperado: o painel descreve capacidades praticadas e demonstradas; não anuncia automaticamente conclusão de A1 ou certificação.

## 9. Gamificação proposta

Usar missões, pequenas narrativas, conquistas de capacidade e comparação com o próprio histórico. Tratar sequência diária e ranking como recursos opcionais, não como infraestrutura obrigatória da motivação.

Não usar perda de vidas por erro, obrigação de pagar para continuar aprendendo, degradação punitiva de conquistas por ausência ou dificuldade artificial para aumentar permanência. Essas restrições são escolhas éticas e de produto; não devem ser apresentadas como resultados específicos de uma meta-análise.

A recompensa deve representar uma conquista compreensível: por exemplo, entender um novo diálogo sem transcrição ou escrever uma mensagem que cumpre um objetivo. Também deve haver espaço para leitura por interesse, sem converter cada minuto em uma obrigação de pontuar.

## 10. Uso de IA e garantia de qualidade

A IA poderá ajudar a preparar rascunhos, variar contextos, simular interlocutores e sugerir feedback. O currículo, as rubricas e os materiais publicados precisam de revisão. O protótipo não deve depender de um chatbot irrestrito que decida sozinho o conteúdo, a dificuldade e a nota.

Em avaliações abertas, separar a qualidade da mensagem, a forma linguística e a confiabilidade da análise automática. Uma estimativa de confiança declarada pelo próprio modelo não é calibração validada. O piloto precisará comparar avaliações automáticas com julgamentos humanos usando amostras representativas.

Manter materiais essenciais estáveis e utilizáveis mesmo quando o serviço de IA não estiver disponível. Isso preserva a proposta de acesso gratuito sem assumir que conversação generativa ilimitada não terá custo.

Conteúdo publicado precisa ter objetivo, nível estimado, modalidade, pré-requisitos, alvos linguísticos, exemplos, rubrica, explicação, apoios, versão e revisão. Áudios precisam ser conferidos quanto a inteligibilidade e correspondência com o texto. Questões de avaliação devem permanecer separadas das variantes usadas no treino.

## 11. Como verificar se o produto realmente funciona

O desfecho principal proposto é **desempenho em tarefas comunicativas inéditas, alinhadas aos objetivos e reavaliadas após um intervalo**. Retenção lexical, compreensão auditiva, produção escrita e oral serão medidas complementares conforme o módulo. Não somar resultados incomparáveis em uma única pontuação de proficiência sem validação.

Uma avaliação inicial deve caracterizar o ponto de partida. A avaliação imediata verifica aprendizagem inicial; avaliações posteriores, por exemplo após 7 e 30 dias, verificam manutenção. Esses prazos são uma proposta de desenho experimental, não os intervalos ótimos de revisão.

No piloto exploratório, observar clareza das instruções, erros de correção, frustração, carga de revisão, funcionamento das tarefas e sinais de aprendizagem. Uma amostra pequena pode detectar problemas de produto, mas não fundamenta alegações amplas de superioridade.

Para testar eficácia comparativa, planejar grupos comparáveis e, quando possível, alocação aleatória. Usar uma alternativa ativa plausível, conteúdo e exposição comparáveis, tarefas diferentes das treinadas e avaliadores que não saibam qual condição cada aluno utilizou. Registrar prática externa, abandonos e perdas de acompanhamento; não relatar apenas quem completou tudo.

Definir previamente desfecho principal, diferença considerada útil, critérios de progressão, calendário de avaliação e plano de análise. A amostra do estudo confirmatório deve ser planejada a partir desses objetivos, e não de um número arbitrário.

Antes de recrutar, congelar um protocolo exploratório versionado com instrumentos, variantes, rubricas, responsáveis, capacidade de avaliação e critérios de continuidade. A avaliação de referência é obrigatória para participantes da pesquisa, embora o diagnóstico do percurso continue opcional. Quem não participa da pesquisa pode estudar sem produzir dados experimentais.

O protocolo inicial do plano técnico adota avaliações imediatamente após a sequência e após 7 e 30 dias, com janelas explícitas e variantes diferentes. As quatro formas de avaliação são distribuídas em ordens alternadas entre participantes, sem repetição individual, para evitar que uma forma fique sempre associada ao mesmo momento. A prática pode continuar entre as avaliações; portanto o resultado principal será descrito como **manutenção com prática continuada**, acompanhado do registro de exposições e prática externa. A própria avaliação intermediária constitui exposição e pode influenciar a medida posterior. Medir retenção sem prática intermediária exigirá outro desenho, sem reinterpretar retrospectivamente este piloto.

O relatório exploratório apresentará resultados por capacidade e modalidade, distribuição das dimensões da rubrica e incerteza; não criará uma nota global de proficiência. Ausência de resposta, falha técnica, uso de apoio e desempenho insuficiente são situações distintas. Critérios operacionais de continuidade não demonstram eficácia causal. O protocolo executável, a amostragem humana e as decisões de ampliar, ajustar ou interromper constam do plano técnico, §13.

Cada rodada terá datas de admissão, corte de coleta, congelamento de dados e relatório definidas antes do recrutamento. O protocolo técnico limita a coleta a 63 dias após o fechamento das admissões, preservando 28 dias de estudo e até 35 de acompanhamento para quem conclui no prazo. Conclusões tardias não estendem automaticamente a rodada nem desaparecem do relatório. A capacidade humana será verificada por pessoa, função e vencimento das respostas, além do total semanal; não prometer retorno de prática em dois dias úteis apenas com base em médias semanais.

Medir eficiência pelo ganho retido em relação ao esforço, sem confundir sessões mais curtas com ensino melhor. Medir também adesão, satisfação e fadiga: são importantes para viabilidade, mas não substituem aprendizagem. Relatar incerteza e resultados por habilidade.

## 12. Primeiro recorte de implementação

Proposta de MVP pedagógico: um único par linguístico, público adulto, um pequeno conjunto de missões iniciais, conteúdo revisado, exercícios de recuperação, revisão distribuída, leitura/escuta contextualizada, produção escrita e oral, painel por capacidades e avaliação posterior. Uma faixa de 12–20 missões é apenas uma opção prática de escopo; não representa um curso A1 completo.

O piloto pode usar avaliação humana de parte da produção e regras de revisão simples. Não precisa começar com rede social, ranking global, dezenas de idiomas, conversação generativa ilimitada ou um modelo sofisticado de domínio.

A escolha da plataforma deve acontecer depois de verificar quais interações precisam funcionar bem: ouvir, gravar, comparar, responder, ler e acompanhar progresso. Android e web devem implementar o mesmo contrato pedagógico; nenhum framework, por si só, comprova eficácia de aprendizagem.

A próxima decisão de conteúdo é escrever uma missão completa e suas variantes de avaliação, seguida de uma sequência curta de missões que permita observar retenção e transferência. Antes de codificar uma trilha extensa, testar esse ciclo com pessoas do público inicial.

## 13. Referências verificadas

Os identificadores E01–E17 permitem rastrear a base científica das recomendações. Os títulos abaixo identificam as fontes; DOI é apresentado como identificador bibliográfico, sem pressupor acesso irrestrito ao texto integral.

**E01.** Kim, S. K.; Webb, S. (2022). *The Effects of Spaced Practice on Second Language Learning: A Meta-Analysis*. Language Learning. DOI: 10.1111/lang.12479.

**E02.** Karpicke, J. D.; Roediger, H. L. (2008). *The Critical Importance of Retrieval for Learning*. Science. DOI: 10.1126/science.1152408.

**E03.** Council of Europe (2020). *Common European Framework of Reference for Languages: Learning, Teaching, Assessment — Companion Volume*. Complementado pelas páginas oficiais “CEFR Descriptors”, “The CEFR in the classroom” e “The framework”.

**E04.** Nation, P. (2007). *The Four Strands*. Innovation in Language Learning and Teaching. DOI: 10.2167/illt039.0.

**E05.** Rice, C. A.; Tokowicz, N. (2020; online em 2019). *A Review of Laboratory Studies of Adult Second Language Vocabulary Training*. Studies in Second Language Acquisition. DOI: 10.1017/S0272263119000500.

**E06.** Spada, N.; Tomita, Y. (2010). *Interactions Between Type of Instruction and Type of Language Feature: A Meta-Analysis*. Language Learning. DOI: 10.1111/j.1467-9922.2010.00562.x.

**E07.** Lyster, R.; Saito, K. (2010). *Oral Feedback in Classroom SLA: A Meta-Analysis*. Studies in Second Language Acquisition.

**E08.** Bryfonski, L.; McKay, T. H. (2019; online em 2017). *TBLT Implementation and Evaluation: A Meta-analysis*. Language Teaching Research. DOI: 10.1177/1362168817744389.

**E09.** Sangers, N. L.; van der Sande, L.; Welie, C.; Dobber, M.; van Steensel, R. (2025). *Learning a Language Through Reading: A Meta-analysis of Studies on the Effects of Extensive Reading on Second and Foreign Language Learning*. Educational Psychology Review. DOI: 10.1007/s10648-025-10068-6.

**E10.** Kurokawa, S.; Hein, A. M.; Uchihara, T. (2025; online em 2024). *Incidental Vocabulary Acquisition Through Captioned Viewing: A Meta-Analysis*. Language Learning. DOI: 10.1111/lang.12697.

**E11.** Uchihara, T.; Karas, M.; Thomson, R. I. (2025). *High Variability Phonetic Training (HVPT): A Meta-analysis of L2 Perceptual Training Studies*. Studies in Second Language Acquisition. DOI: 10.1017/S0272263125100879.

**E12a.** Saito, K.; Plonsky, L. (2019). *Effects of Second Language Pronunciation Teaching Revisited: A Proposed Measurement Framework and Meta-Analysis*. Language Learning. DOI: 10.1111/lang.12345.

**E12b.** Saito, K. (2021). *What Characterizes Comprehensible and Native-like Pronunciation Among English-as-a-Second-Language Speakers? Meta-Analyses of Phonological, Rater, and Instructional Factors*. TESOL Quarterly. DOI: 10.1002/tesq.3027.

**E13.** Ngo, T. T.-N.; Chen, H. H.-J.; Lai, K. K.-W. (2024; online em 2023). *The Effectiveness of Automatic Speech Recognition in ESL/EFL Pronunciation: A Meta-analysis*. ReCALL. DOI: 10.1017/S0958344023000113.

**E14.** Sailer, M.; Homner, L. (2020; online em 2019). *The Gamification of Learning: A Meta-analysis*. Educational Psychology Review. DOI: 10.1007/s10648-019-09498-w.

**E15.** Nakata, T.; Suzuki, Y. (2019). *Mixing Grammar Exercises Facilitates Long-Term Retention: Effects of Blocking, Interleaving, and Increasing Practice*. The Modern Language Journal. DOI: 10.1111/modl.12581.

**E16.** Saarela, M.; Gunasekara, S.; Kumarage, P. (2026). *A Meta-analysis of Generative AI Effects on Language Proficiency and Affective–Cognitive Outcomes in Language Learning*. Discover Computing. DOI: 10.1007/s10791-026-10015-1.

**E17.** Mihaylova, M.; Gorin, S.; Reber, T. P.; Rothen, N. (2022). *A Meta-Analysis on Mobile-Assisted Language Learning Applications: Benefits and Risks*. Psychologica Belgica. DOI: 10.5334/pb.1146.
