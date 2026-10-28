# FASE 21-G — Operacao reproduzivel e distribuicao pre-release da CLI

## Escopo e limites

O Rasping Amazon permanece em um unico modulo Maven. Esta distribuicao empacota o JAR gerado pelo Maven, bibliotecas de runtime e um inicializador PowerShell da CLI. Nao inclui segredos nem dados de negocio. Nao equivale ao release `v1.0.0`, que depende da FASE 21-H.

O supervisor do processamento continuo continua no checkout versionado, em `infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1`, com validacao de PID, reserva e parada cooperativa. O empacotamento nao cria um segundo supervisor e nao autoriza sua partida.

## Requisitos

- Windows com PowerShell 5.1 ou mais recente, Java 25 e Git.
- Maven Wrapper versionado (`mvnw.cmd`) e dependencias ja disponiveis no cache Maven para o modo offline.
- Docker Desktop/Compose e PostgreSQL 18.6 conforme `infra/compose.yml` para operacao do banco; a montagem da distribuicao nao consulta o banco.
- Segredos somente fora do Git. As identidades `rasping_runtime` e `rasping_migrator` ja foram preparadas na FASE 21-C e seus DPAPI ficam em diretorio externo privado. Nao copiar `.env`, `.dpapi` ou `.dump` para o ZIP.

## Montar a distribuicao, sem iniciar a aplicacao

Executar na raiz `C:\Dev\rasping-amazon`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra\release\FASE_21_G_MONTAR_DISTRIBUICAO.ps1"
```

O script exige `preparation_only` e `inventory_only`, executa `mvnw.cmd -o -q -DskipTests package`, obtém o classpath de runtime pelo plugin Maven, copia o JAR e as bibliotecas para um diretório **novo** sob `target/`, gera manifesto com SHA-256 e compacta o diretório em ZIP. O gate abre apenas a **ajuda** da CLI empacotada, sem composição JDBC. O projeto e o sistema de dados não são alterados.

O artefato permanece pré-release: versão Maven `1.0-SNAPSHOT`. Não renomear para `1.0.0` ou criar tag.

## Usar a CLI empacotada

Dentro do diretório extraído do ZIP:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\INICIAR_CLI.ps1"
```

Sem argumentos, imprime ajuda. Para qualquer comando administrativo real, o inicializador exige `-ConfirmarOperacao`, mas esse parâmetro **não constitui por si só autorização de operação**. O operador deve assegurar previamente ambiente, permissões, variáveis de conexão e autorização adequada; não utilizar comandos que alteram agendas ou executam ações externas na preparação atual.

A CLI utiliza as variáveis de ambiente do processo, não carrega `.env` automaticamente. O arquivo `.env.example` é somente referência e não contém segredos válidos.

## PostgreSQL, migrations e privilégios

O banco PostgreSQL é gerenciado pela composição existente `infra/compose.yml`, restrita a `127.0.0.1:5432`. Os papéis de migração e runtime permanecem separados. Não executar `flyway:migrate`, não alterar HBA ou grants e não iniciar o daemon durante `preparation_only`. Os scripts da FASE 21-C contêm os procedimentos já auditados para preparação autorizada.

## Supervisor Windows e ciclo de vida

Após autorização específica futura, a operação contínua será feita pelo supervisor versionado do checkout, não pela CLI empacotada. O supervisor já contém controles de identidade, startup bloqueado por política, logs catalogados e pedido de parada cooperativa. A execução real de `ContinuousProcessingMain` permanece proibida neste gate; não ajustar manualmente a política para contornar a proibição.

Para inspeção do contrato de startup sem partida, utilizar `infra/postgres/security/FASE_21_C2_E_D_D_D_B_EXIBIR_PLANO_SUPERVISAO.ps1`. A primeira inicialização e a parada reais somente poderão constar de um procedimento autorizado após os gates da FASE 21-H.

## Logs e retenção

O catálogo operacional está em `config/operacao/operational-log-catalog.v1.json`. A retenção se mantém `inventory_only`, sem deleção, rotação destrutiva ou captura produtiva. Registros de negócio e auditoria duráveis não expiram automaticamente. Os relatórios e cenários sob `target/` são preservados durante esta fase.

## Backup e restauração

A FASE 21-F validou backup lógico PostgreSQL e restauração em contêiner isolado. Artefatos estão sob `C:\dev\rasping-amazon-backups\postgres`, fora do Git. O comando passivo é:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra\postgres\backup\FASE_21_F_BACKUP_RESTAURACAO.ps1" -Modo Verificar
```

Executar `-Modo BackupERestauracao -ConfirmarBackupRestauracao` somente quando a operação real estiver explicitamente autorizada. Nunca restaurar sobre o banco de origem. Preservar backup, manifesto e prova de restauração.

## Diagnóstico e atualização

- Conferir `git status --short`, branch e política versionada; não imprimir valores de segredos.
- Antes de release, rodar suíte hermética e testes de segurança consolidados. Probes externas são separadas e dependem da disponibilidade da Amazon.
- Para uma atualização, preservar backups e configurações, testar a distribuição nova e proceder por mudança versionada, sem sobrescrever histórico nem executar migrações sem autorização.
- Em falha da captura ou supervisor, preservar evidências e investigar antes de nova partida. Não usar `taskkill` como parada normal.

## Gate de fechamento

A FASE 21-G comprova empacotamento reproduzível da CLI e documentação de operação. A FASE 21-H ainda precisa de testes integrados, validação operacional autorizada, CI, revisão do release e confirmação remota antes do `v1.0.0`.
