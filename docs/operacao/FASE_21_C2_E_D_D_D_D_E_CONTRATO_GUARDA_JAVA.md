# FASE 21-C2-E-D-D-D-D-E — Guarda Java anterior ao bootstrap

## Risco controlado

A etapa D-D confirmou que o agendamento `amazon-deals` foi persistido pausado
(`enabled=false`), mas o bootstrap real ainda executa a barreira de recovery.
Antes desta etapa, a politica `continuous-startup-policy.v1.json` era aplicada
pelo launcher PowerShell; executar `ContinuousProcessingMain` diretamente
poderia contornar a validacao do launcher.

## Contrato Java

`ContinuousProcessingMain.run` executa `DaemonStartupPolicyGate.requireAuthorized`
antes de chamar `ContinuousProcessingBootstrap.open()`. Assim, nem o bootstrap,
nem a criacao/reconciliacao de jobs, nem a abertura de conexoes JDBC, nem
as integracoes externas podem iniciar quando a politica estiver bloqueada.

A guarda exige explicitamente as oito chaves e respectivos tipos de dados,
rejeita JSON invalido, duplicado, links simbolicos do arquivo e campos extras.
Somente aceita os valores previstos para uma futura autorizacao supervisionada:

- `schema_version = 1`;
- `execution_mode = supervised`;
- `initial_schedule_interval = PT1H`;
- `daemon_start_authorized = true`;
- `external_actions_authorized = true`;
- `flyway_migrate_authorized = false`;
- `automatic_restart_authorized = false`;
- `shutdown_grace_seconds = 30`.

Esses valores **nao devem ser aplicados agora**. A politica versionada permanece
`preparation_only` e com todos os quatro indicadores de autorizacao falsos.

Nao existe override publico de caminho por argumento de linha de comando ou
variavel de ambiente. O caminho customizado e o `Supplier` de abertura sao
visiveis apenas ao pacote para injecao controlada nos testes.

## Testes

`DaemonStartupPolicyGateTest` verifica negacao antes do bootstrap simulado,
falha fechada para JSON malformado/duplicado, atributos e tipos inesperados,
negacao de efeitos externos, migracao, restart, intervalo e timeout invalidos.
O caso de autorizacao positiva usa um `Supplier` simulado que lanca uma excecao;
**nao executa um bootstrap real**. O erro permanece sanitizado.

`FASE_21_C2_E_D_D_D_D_E_VALIDAR_GUARDA_JAVA.ps1` executa somente testes JUnit,
confere a ordem da autorizacao em relacao a abertura e revalida a ausencia de
processo produtivo. O teste nao altera politica, segredos, `env`, banco ou
agendamento pausado. Nenhum Maven Flyway migrate e executado.

## Limites

Esta entrega adiciona defesa em profundidade, mas nao testa autorizacao
produtiva real, concorrencia do supervisor, recovery, coleta ou publicacao.
Permanecem bloqueados daemon produtivo, efeitos externos, Flyway migrate e
reinicio automatico. Atualizar a politica exige fase posterior com aprovacao
auditavel e verificacao da seguranca dos efeitos reais.
