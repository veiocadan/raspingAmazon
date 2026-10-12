# ADR-0018 — Segurança, governança e fechamento da versão 1.0

- **Status:** Aceita
- **Data:** 2026-10-12
- **Projeto:** Rasping Amazon
- **Fase relacionada:** FASE 21 — Segurança, governança e fechamento da v1.0
- **Complementa:** ADR-0009, ADR-0010, ADR-0011, ADR-0012, ADR-0013, ADR-0014, ADR-0015 e ADR-0017
- **Escopo:** segredos, privilégios, auditoria, retenção, backup, restauração, operação reproduzível e release v1.0.0

---

## 1. Contexto

O Rasping Amazon concluiu funcionalmente as FASES 0 a 20.

Ao iniciar a FASE 21, o sistema já possui:

- PostgreSQL como fonte de verdade operacional;
- migrations Flyway versionadas até V37;
- coleta, parsing, enriquecimento e avaliação;
- filtros comerciais, score, ranking, histórico e momentum;
- geração versionada de publicações;
- seleção operacional, quota e cadência;
- interface operacional CLI não bloqueante;
- scheduler e processamento contínuo;
- outbox persistente e adapters de publicação;
- classificação de falhas, retry, recovery e reprocessamento;
- tratamento conservador de DELIVERY_UNKNOWN;
- observabilidade e auditoria de decisões.

O baseline local da FASE 21 é:

- 1786 testes;
- 0 falhas;
- 0 erros;
- 0 ignorados;
- BUILD SUCCESS;
- working tree limpa.

O encerramento remoto da FASE 20 foi posteriormente confirmado por merge do PR #11 e CI pós-merge verde.

A FASE 21 não deverá reconstruir funcionalidades encerradas nas fases anteriores.

Seu objetivo é transformar a implementação existente em uma versão operacionalmente segura, administrável, reproduzível e recuperável.

## 2. Hierarquia documental

O documento `ROADMAP_RASPING_AMAZON_V1.md` permanece como referência histórica de planejamento.

A interpretação do projeto seguirá:

1. Roadmap: direção histórica e objetivos das fases.
2. ADRs aceitas: decisões arquiteturais específicas e vigentes.
3. Resultados de fase posteriores: evidência das implementações efetivamente realizadas.
4. Código e testes da branch vigente: estado técnico a ser verificado.

Quando uma decisão posterior alterar uma previsão histórica, a divergência deverá permanecer explícita.

Não serão reescritos documentos históricos para ocultar mudanças de decisão.

## 3. Princípios da FASE 21

A implementação deverá preservar:

- separação entre domínio, aplicação, infraestrutura e apresentação;
- PostgreSQL como autoridade do estado durável;
- migrations append-only;
- idempotência e rastreabilidade;
- configurações semânticas versionadas;
- fail closed;
- mínimo privilégio;
- ausência de segredos no repositório;
- ausência de aprovação humana obrigatória no fluxo normal;
- recuperação segura de falhas;
- operação reproduzível sem IDE;
- alterações incrementais validadas por testes.

A FASE 21 não introduzirá novo motor de negócio.

## 4. Segredos e configuração

Segredos permanecerão fora do código-fonte e do repositório.

Entre os valores protegidos estão:

- senhas PostgreSQL;
- tokens Telegram;
- tokens WhatsApp;
- credenciais de integrações externas;
- credenciais administrativas;
- futuras credenciais de fontes;
- quaisquer tokens de autenticação incorporados a URLs.

O arquivo `.env.example` deverá representar o contrato de configuração, sem conter valores reais.

O arquivo `.env` continuará ignorado pelo Git.

Objetos de configuração não poderão expor segredos por `toString()` ou representação textual equivalente.

Mensagens de validação deverão identificar o nome da variável incorreta sem imprimir seu valor quando ele puder ser sensível.

Os testes deverão comprovar que segredos não aparecem nas representações textuais desses objetos.

## 5. PostgreSQL e mínimo privilégio

A aplicação não deverá utilizar credenciais de superusuário como identidade operacional normal.

As responsabilidades serão separadas em:

- **Bootstrap/administrador:** preparação inicial de banco, usuários e privilégios.
- **Migrator:** execução das migrations Flyway e alterações autorizadas de schema.
- **Runtime:** execução dos casos de uso e acesso somente aos objetos necessários.

O usuário de runtime não deverá possuir:

- SUPERUSER;
- CREATEDB;
- CREATEROLE;
- autorização geral para criar, alterar ou remover tabelas;
- permissões administrativas desnecessárias.

O runtime deverá possuir os privilégios de leitura e escrita necessários às operações já implementadas, incluindo sequência/identidade, fila, seleção, outbox, auditoria e recovery.

A configuração JDBC deverá preservar a separação entre migração e execução normal.

Os testes deverão comprovar:

1. migração bem-sucedida com identidade autorizada;
2. operação funcional com identidade restrita;
3. rejeição de DDL não autorizado para a identidade de runtime;
4. funcionamento das operações duráveis existentes após a separação.

O CI poderá utilizar infraestrutura descartável de testes, mas isso não deverá enfraquecer o contrato operacional de produção.

## 6. Exposição de rede

A v1.0 possuirá interface operacional local baseada em CLI.

A CLI não será exposta como serviço HTTP remoto.

Não será introduzido um sistema de autenticação web sem existir uma interface remota correspondente.

A exposição do PostgreSQL deverá ser limitada ao necessário para operação.

Ambientes locais deverão restringir a publicação da porta à interface de loopback quando o banco precisar ser acessível pelo host.

Uma futura interface remota exigirá nova decisão arquitetural e controles de autenticação e autorização adequados antes de sua exposição.

## 7. Logs e tratamento seguro de erros

Logs estruturados continuarão utilizando os contratos de observabilidade existentes.

Os logs poderão conter:

- identificadores persistidos;
- códigos estáveis de falha;
- origem e categoria da falha;
- operação;
- duração;
- resultado;
- contexto operacional não sensível.

Não deverão conter:

- senhas;
- access tokens;
- cabeçalhos de autorização;
- URLs contendo credenciais;
- cookies ou sessões autenticadas;
- payloads externos completos contendo informação sensível.

A proteção deverá ocorrer nas fronteiras de configuração, transporte, diagnóstico e apresentação.

A simples omissão intencional de um campo de log não substitui testes de proteção contra vazamentos.

## 8. Governança de configuração

A FASE 21 preservará os mecanismos existentes de versionamento de:

- FilterProfile;
- ScoreProfile;
- políticas de seleção;
- perfis de quota;
- perfis de cadência;
- templates e estratégias de publicação.

Versões históricas não poderão ser silenciosamente sobrescritas.

Alterações relevantes de configuração operacional deverão possuir rastreabilidade suficiente para identificar:

- qual configuração foi alterada;
- valor ou versão anterior;
- valor ou versão posterior;
- momento da alteração;
- origem ou responsável, quando identificável;
- motivo ou referência da mudança administrativa.

Não será criado um segundo mecanismo de auditoria para fatos já protegidos por versionamento ou eventos persistidos.

O histórico do Flyway continuará sendo a evidência de evolução de schema.

As alterações administrativas sem auditoria suficiente serão identificadas e complementadas na FASE 21.

## 9. Intervenção operacional

A interface não será colocada no caminho crítico da publicação.

Conforme ADR-0009 e resultado posterior da FASE 19:

- geração não exige aprovação humana;
- readiness normal ocorre automaticamente;
- entrega permanece desacoplada;
- intervenção humana é reservada às exceções operacionais.

A FASE 21 preservará a semântica da ADR-0015.

Em especial:

- DELIVERY_UNKNOWN não recebe retry cego;
- resolução posterior permanece auditada;
- reprocessamento de ProcessingJob.DEAD exige decisão explícita;
- startup recovery continua ocorrendo antes do runtime normal.

Governança não deverá enfraquecer essas garantias.

## 10. Retenção

A política inicial de retenção será conservadora.

Fatos comerciais e históricos necessários à explicabilidade não serão eliminados automaticamente apenas por idade.

A v1.0 deverá distinguir:

- OfferSnapshot e evidências;
- DealEvaluation, fatores e regras;
- histórico e momentum;
- ProcessingRun e ProcessingJob;
- Publication e PublicationAttempt;
- outbox e eventos de resolução;
- observações operacionais;
- logs;
- diagnósticos temporários;
- backups.

Para a v1.0, os registros duráveis de negócio e auditoria serão preservados até que exista política específica, documentada e testada para sua expiração.

Não será implementada exclusão destrutiva genérica como mecanismo inicial de retenção.

Logs e diagnósticos temporários deverão possuir política operacional explícita de armazenamento e expiração.

Backups deverão possuir política própria de frequência, conservação e proteção.

Os prazos operacionais específicos deverão ser documentados e verificados antes do gate final da v1.0.

## 11. Backup

PostgreSQL permanece como fonte principal do estado.

A v1.0 deverá possuir procedimento de backup reproduzível.

O procedimento deverá contemplar:

- identificação do banco de origem;
- execução consistente do backup;
- tratamento de falhas;
- identificação do artefato produzido;
- proteção de acesso;
- armazenamento fora do repositório;
- política de conservação;
- documentação de restauração.

Artefatos de backup não deverão ser versionados no Git.

Backup executado não será considerado prova de recuperação.

## 12. Restauração

A FASE 21 deverá demonstrar restauração prática em banco isolado.

O teste deverá validar, conforme dados existentes:

- schema;
- catálogo de migrations;
- tabelas operacionais;
- registros históricos;
- relacionamentos;
- configurações versionadas;
- dados de avaliação;
- publicações;
- auditoria;
- integridade dos dados restaurados.

Uma restauração de teste não poderá sobrescrever silenciosamente o banco operacional original.

O procedimento deverá ser repetível por um operador seguindo a documentação.

## 13. Operação reproduzível

A v1.0 deverá possuir instruções suficientes para executar o sistema sem IDE.

A documentação operacional deverá cobrir:

1. requisitos do ambiente;
2. clonagem do repositório;
3. configuração externa;
4. inicialização do PostgreSQL;
5. preparação de privilégios;
6. execução de migrations;
7. instalação de dependências do Playwright;
8. compilação e testes;
9. execução da CLI;
10. inicialização do processamento contínuo;
11. consulta de estado;
12. pausa e retomada;
13. encerramento controlado;
14. backup;
15. restauração;
16. atualização da aplicação;
17. diagnóstico de falhas.

O Maven Wrapper continuará sendo a referência para execução do build.

A preparação operacional não deverá depender de alterações manuais nas tabelas da aplicação.

## 14. Canais externos

As integrações atuais permanecem:

- TELEGRAM;
- WHATSAPP_MANUAL;
- WHATSAPP.

A ativação deverá continuar explícita e condicionada à configuração válida.

WHATSAPP_MANUAL continuará representando entrega ao staging privado, não comprovação de publicação final no WhatsApp.

Conforme ADR-0017, não será introduzido WHATSAPP_OPENWA.

Nenhuma configuração, migration, adapter, segredo ou infraestrutura OpenWA fará parte da v1.0.

## 15. Empacotamento e release

O projeto continuará sendo um único módulo Maven.

A FASE 21 deverá definir o artefato executável e o procedimento de distribuição adequados à operação local da v1.0.

O build de desenvolvimento continuará distinguível do release final.

O versionamento de release será realizado somente após os gates de qualidade.

A versão final planejada é:

`v1.0.0`

A tag não será criada antes da conclusão local e remota.

## 16. Gate de segurança

O gate deverá comprovar:

- segredos protegidos;
- configuração externa validada;
- ausência de credenciais reais no repositório;
- usuário de runtime sem privilégios administrativos;
- migrations executáveis pela identidade autorizada;
- PostgreSQL com exposição de rede controlada;
- interface operacional não exposta remotamente;
- operações administrativas auditáveis;
- retenção documentada;
- backup reproduzível;
- restauração real validada.

## 17. Gate funcional

O gate deverá preservar o pipeline existente:

Amazon → coleta → parsing → enrichment → persistência → elegibilidade → filtros → score/ranking → histórico/momentum → seleção → geração de Publication → readiness automática → outbox → canal configurado → tentativa persistida → auditoria.

O gate deverá comprovar:

- operação normal sem aprovação humana;
- idempotência;
- cadência e quota;
- prevenção de duplicações;
- tratamento de falhas;
- recovery;
- preservação de histórico;
- observabilidade.

A validação externa deverá usar evidências reais quando aplicável, mantendo as probes externas separadas da suíte hermética.

Restrições da fonte não autorizam bypass ou invenção de dados.

## 18. Gate de qualidade e release

Antes de declarar a versão 1.0 concluída:

- executar a suíte hermética completa;
- validar Flyway;
- executar testes de segurança;
- executar prova de backup/restore;
- validar operação reproduzível;
- verificar o estado da fonte externa;
- concluir documentação operacional;
- validar ausência de segredos;
- concluir os gates locais;
- publicar a branch;
- obter CI remoto verde;
- realizar merge em main;
- confirmar CI pós-merge;
- definir versão final;
- criar tag v1.0.0.

O resultado deverá ser registrado em `FASE_21_RESULTADO.md`.

A versão não poderá ser declarada concluída apenas porque os testes unitários passaram.

## 19. Fora do escopo

Não serão introduzidos sem nova decisão e evidência suficiente:

- Kafka;
- RabbitMQ;
- microserviços;
- Kubernetes;
- Redis como estado autoritativo;
- circuit breaker especulativo;
- dashboard web;
- autenticação web sem interface remota;
- OpenWA;
- novos marketplaces;
- integração obrigatória com Amazon Creators API;
- implantação em Raspberry Pi como condição do release;
- reimplementação do recovery da FASE 20;
- segundo motor de seleção ou avaliação.

## 20. Plano de execução

A FASE 21 será dividida em blocos verificáveis:

- **21-A:** contrato arquitetural e baseline;
- **21-B:** proteção de segredos e contrato de configuração;
- **21-C:** privilégios e isolamento PostgreSQL;
- **21-D:** governança e auditoria de configuração;
- **21-E:** retenção e classificação dos dados;
- **21-F:** backup e restauração;
- **21-G:** operação reproduzível e empacotamento;
- **21-H:** validação integral e release v1.0.0.

Cada bloco deverá registrar:

- arquivos alterados;
- decisões efetivas;
- testes adicionados;
- gates executados;
- resultado técnico;
- pendências;
- commit correspondente.

## 21. Critério final

A FASE 21 estará concluída quando o sistema puder ser instalado, configurado, iniciado, operado, diagnosticado, interrompido, atualizado e restaurado com segurança.

A v1.0 deverá continuar:

- auditável;
- idempotente;
- recuperável;
- desacoplada;
- configurável;
- reproduzível;
- independente de IDE;
- independente de Excel;
- sem manipulação manual obrigatória do banco;
- sem aprovação humana obrigatória no fluxo normal.

A conclusão será comprovada por testes, evidências operacionais, documentação e CI, não apenas por declaração de implementação.

---

**Estado desta ADR:** Aceita em 2026-10-12 para orientar a execução da FASE 21.
