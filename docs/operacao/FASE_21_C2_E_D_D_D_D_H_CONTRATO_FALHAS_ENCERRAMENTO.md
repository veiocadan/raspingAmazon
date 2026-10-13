# FASE 21-C2-E-D-D-D-D-H — Falhas do lifecycle e semântica de encerramento

## Estado

Validação de laboratório. **A política produtiva continua em `preparation_only`** e o
agendamento `amazon-deals` continua pausado. Não há autorização de coleta,
publicação, migração, recovery efetivo ou startup produtivo.

## Risco corrigido

Em `ContinuousProcessingMain.run`, o encerramento bem-sucedido dos loops antes
retornava código `0` mesmo se `ContinuousProcessingApplication.close()` falhasse
no bloco `finally`. Essa situação poderia induzir o supervisor a registrar
sucesso apesar de falha no fechamento de JDBC ou recurso gerenciado.

Agora o retorno é calculado **depois** do fechamento. Falha de limpeza após
execução normal implica código `1`; interrupção mantém código `130`; falha
anterior continua sendo falha. Todas as mensagens continuam com
`SafeOperationalError.describe`, sem mensagens de exceções ou credenciais.

## Ensaios sem efeitos externos

Os testes JUnit usam `ContinuousProcessingRuntime` real com loops falsos,
`Connection` JDBC com proxy em memória, objetos `AutoCloseable` simulados e
política autorizada **somente em arquivo temporário de teste**. Cobrem:

1. Falha simulada de bootstrap/recovery antes de transferir recursos ao entrypoint;
2. falha de loop, parada do outro loop e ordem reversa de liberação;
3. encerramento normal, sem liberação duplicada;
4. falha de recurso externo durante o fechamento, código diferente de zero;
5. falha ao fechar conexão JDBC, fechamento das demais conexões e sigilo.

Os ensaios não invocam `ContinuousProcessingBootstrap.open`, Flyway, rede ou
PostgreSQL. Não modificam o JSON operacional, o `.env` ou o banco.

## Gates

Executar `FASE_21_C2_E_D_D_D_D_H_VALIDAR_FALHAS_ENCERRAMENTO.ps1`, seguido de
`git diff --check` e `git status`. O gate requer política bloqueada, ausência
de daemon supervisionado e relatório JUnit íntegro. Não realizar commit antes
do resultado completo no Windows.

## Pendente antes de liberar operação

O mecanismo Windows ainda precisa de uma revisão específica de falhas **entre
criação do processo filho e persistência do registro de identidade**, incluindo
orfandade e concorrência. A D-H não valida esse caso; não representa aprovação
para iniciar o daemon produtivo.
