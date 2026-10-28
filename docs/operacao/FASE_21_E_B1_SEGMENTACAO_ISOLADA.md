# FASE 21-E-B1 — Segmentação isolada de logs estruturados

## Propósito

Provar localmente, em ambiente Windows, o mecanismo básico de escrita de eventos JSON Lines com segmentação por tamanho, preservação da ordem, limite total e controle de ACL. Esta entrega **não implementa captura contínua** de `stdout`/`stderr` do daemon nem política de expiração.

## Hierarquia de contratos

- ADR-0011: logs são complementares ao estado durável PostgreSQL.
- ADR-0018, seção 10: retenção conservadora de históricos e auditorias.
- `config/operacao/retention-policy.v1.json`: `execution_mode=inventory_only`, `destructive_actions_authorized=false`, `continuous_log_capture_authorized=false`.
- `config/operacao/continuous-startup-policy.v1.json`: `execution_mode=preparation_only`, quatro flags de autorização `false`.

## Limites deste gate

O mecanismo B1 atua exclusivamente em diretórios novos `target/retencao-21eb1-testes/cenario-<guid>`; exige `target/` pré-existente, sem redirecionamentos simbólicos na cadeia do caminho. A raiz do sandbox e cada cenário devem possuir ACL Windows não herdada, com permissões concedidas somente à conta corrente, SYSTEM e Administradores.

São rejeitados parâmetros de saída que tentem usar qualquer diretório fora de um cenário criado no sandbox, arquivos pré-existentes no destino, eventos com linhas múltiplas, JSON inválido, formato inesperado ou campos não permitidos.

Cada registro sintético é gravado em UTF-8 sem BOM, com fronteira de linha preservada. Quando não cabe no segmento atual, é criado novo segmento via `CreateNew`, sem sobrescrever os anteriores. O limite total **rejeita novas gravações**; não remove segmentos antigos. Neste gate, bloquear gravação na quota é um comportamento de teste, **não uma estratégia aceitável de continuidade do daemon**.

Os testes usam propositalmente limites pequenos em memória para exercitar rotação e quota, sem alterar a política versionada (16 MiB/segmento, 512 MiB total e 30 dias são propostas ainda não ativas).

## Arquivos

- `infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1`: funções sem efeitos na importação.
- `infra/retencao/FASE_21_E_B1_TESTAR_SEGMENTACAO.ps1`: testes sintéticos locais.

## Gate de execução

Na raiz do repositório, Windows PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/retencao/FASE_21_E_B1_TESTAR_SEGMENTACAO.ps1"
git status --short
git branch --show-current
```

O resultado é **PENDENTE** até ser executado no Windows do operador. A criação dos arquivos sintéticos de teste não constitui implementação da retenção local, captura real, isolamento de recursos do daemon ou operação de produção.

## Pendências para 21-E-B2 e seguintes

1. Captura não bloqueante de `stdout` e `stderr` em sandbox com um processo sintético controlado, incluindo condição de falha e encerramento.
2. Política explícita para falhas do sink e disco cheio, sem indisponibilizar ou alterar inadvertidamente o processamento do daemon.
3. Filtros de segurança de dados nas duas saídas. A validação apenas da estrutura JSON **não comprova ausência de segredos** nos valores.
4. Rotação e retenção após identificação de diretório operacional seguro, exceções de incidentes e política aprovada.
5. Integração revisada com o supervisor **somente depois** dos gates isolados, sem ativar `preparation_only` antes de autorização operacional expressa.
6. Registrar evidências de testes reais e consolidação da fase sem commits intermediários.

Nenhuma migration será introduzida para resolver retenção de arquivos locais; nenhuma tabela histórica será limpa por idade.
