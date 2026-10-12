# FASE 21-C2-E-D-D-D-A — Gate de preparacao da inicializacao segura

## Estado e objetivo

Apos o commit `502f757`, a politica de alertas inicial esta versionada e foi validada em cinco consultas CLI reais, sem persistencia de credenciais. A CLI nao representa o bootstrap completo da aplicacao continua. Antes de permitir qualquer partida persistente, e necessario auditar os efeitos de startup e preservar os gates anteriores.

## Distincao arquitetural obrigatoria

- `OperationalCliMain` compoe uma CLI sob demanda, e os cinco comandos ja exercitados foram de leitura.
- `ContinuousProcessingMain` cria um processo de longa duracao. Seu bootstrap abre conexoes JDBC independentes para scheduler e worker, executa `EnsureProcessingScheduleUseCase` (pode inserir schedule), prepara recursos externos e realiza `startupRecoveryService().recover()` **antes** de iniciar o runtime. A recuperacao pode modificar objetos do banco.
- O runtime pode realizar HTTP externo, coleta, enriquecimento e trabalho de publicacao. Por isso, **nao e seguro usar `ContinuousProcessingMain` como um teste de leitura**, tampouco assumir que a execucao da CLI demonstrou a prontidao do daemon.
- `DatabaseMigration` escolhe `databaseMigrationUser()` e `databaseMigrationPassword()` para `flyway.migrate()`. A aplicacao continua nao deve herdar credenciais de migracao como identidade de runtime; a migracao efetiva exige gate futuro especifico.

## Arquivos do pacote

1. `infra/postgres/security/FASE_21_C2_E_D_D_D_A_FUNCOES_BOOTSTRAP.ps1`: contrato de fontes e verificacoes locais sem segredos.
2. `infra/postgres/security/FASE_21_C2_E_D_D_D_A_PRECHECK_BOOTSTRAP.ps1`: orquestra inspecoes e chama o gate ja validado da fase C-C.
3. Este documento: criterios e registro da decisao de nao ligar o daemon prematuramente.

## Garantias e limites do gate A

O gate A confere:

- se a branch local contem os pontos de entrada Java esperados, incluindo separacao JDBC/Flyway;
- se o daemon possui inicializacao de schedule e barreira de recovery (efeitos colaterais reconhecidos explicitamente);
- se o `ContinuousProcessingMain`/`Bootstrap` nao invoca diretamente `DatabaseMigration.migrate()`;
- se o dump original previamente testado conserva tamanho e SHA256;
- se o processo de daemon nao foi encontrado na lista local de processos Windows, pelo nome Java (nao identifica instancias remotas nem todos os wrappers possiveis);
- se Java, Maven, Git e Docker estao disponiveis;
- se a politica inicial JSON, credenciais DPAPI e restricao de porta continuam satisfazendo o precheck da fase C-C.

Nao executa comandos Maven, Java, Flyway ou SQL; apenas consultas locais de arquivos/processos e o `docker inspect` **somente leitura** herdado do precheck anterior. Nao grava arquivo secreto, nao altera HBA, `.env`, banco ou roles. Nao altera valores operacionais da politica.

## Procedimento: executar somente o precheck

Extrair o ZIP na raiz do projeto, preservando as pastas. Com aplicativo, scheduler e worker parados, executar no Windows PowerShell:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_A_PRECHECK_BOOTSTRAP.ps1'
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-A reprovada; nao iniciar nenhum processo produtivo.' }
git diff --check
git status
```

Marcadores finais esperados:

```text
=== C2-E-D-D-D-A PRECHECK PASS - analise de bootstrap concluida, sem execucao produtiva ===
=== C2-E-D-D-D-A BLOQUEIO ATIVO - NAO iniciar daemon, worker, scheduler ou Flyway migrate ===
```

## Proximos gates obrigatorios

Se o gate A passar, preparar e testar **um launcher supervisionado** que carregue somente as credenciais necessarias em ambiente efemero e registre auditoria sem vazar segredos. A fase de migração Flyway (se necessaria) deve ser isolada, com backup e confirmacao explicita. O primeiro start de daemon exigira validação do restante das variaveis de configuracao, recursos externos, recuperacao e tratamento de falhas. Aprovacao do gate A **nao** autoriza executar `ContinuousProcessingMain`.

Nao modificar `.env`, nao iniciar scheduler/worker, nao reprovisionar DPAPI, nao publicar push nem fazer commit antes da revisao do resultado deste gate.
