# FASE 21-C2-E-D-D-C-B — Politica de alertas explicita para o gate temporario

## Diagnostico confirmado

A primeira execucao da CLI real concluiu `EVALUATIONS`, `RUNS`, `JOBS` e `PUBLICATIONS`, mas falhou na rota `ALERTS`.

O diagnostico `FASE_21_C2_E_D_D_C_B_DIAGNOSTICAR_ALERTS_SEM_SEGREDOS.ps1` confirmou a ausencia de **todas as cinco variaveis `ALERT_*`** no ambiente do processo e como chaves do arquivo `.env`.

O arquivo de exemplo da FASE 21-B2 apresenta esses parametros com `CHANGE_ME`, sem valores definidos. O Java `EnvironmentOperationalAlertPolicyProvider.load()` exige todos e **nao escolhe defaults**. Assim, uma execucao sem configuracao de alertas nao demonstra problema de autenticacao nem de ACL no PostgreSQL; a politica falha antes de consultar os alertas.

## Decisao arquitetural

A politica de alertas representa uma decisao operacional da equipe, nao um valor de teste escolhido implicitamente por scripts. O gate nao altera esse contrato Java, nao altera SQL de alertas e nao inventa valores de producao.

O script `FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_COM_POLITICA_TEMPORARIA.ps1` coleta cinco valores escolhidos pelo operador no terminal, valida os limites do contrato Java e solicita a confirmacao literal `CONFIRMO`. Depois, fornece os valores **somente ao processo atual e seus filhos**, executando o executor CLI ja existente. Por ultimo, restaura todas as variaveis `ALERT_*` e verifica a integridade de `.env`.

## Significado dos campos

| Variavel | Tipo/limite exigido | Decisao que controla |
|---|---|---|
| `ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD` | inteiro >= 2 | Quantidade de falhas externas repetidas que gera alerta. |
| `ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS` | inteiro longo > 0 | Janela temporal, em segundos, para considerar as falhas. |
| `ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS` | inteiro >= 1 | Quantas runs COMPLETED anteriores participam da media de referencia. |
| `ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION` | decimal estritamente entre 0 e 1 | Fracao de queda de candidatos necessaria para sinalizar reducao suspeita. |
| `ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES` | inteiro longo >= 1 | Media historica minima para avaliar queda suspeita. |

Os parametros acima **nao foram definidos pelas fontes**. Se o operador ainda nao pode decidir os valores, esta fase permanece pendente; nao substituir por numeros arbitrarios, nem por defaults codificados no Java.

## Arquivos completos

1. `infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_COM_POLITICA_TEMPORARIA.ps1` — exige definicao e confirmacao manual dos cinco limiares, injeta valores somente na sessao e reexecuta o gate existente.
2. Este documento operacional.

**Dependencias preexistentes** (nao incluidas ou substituidas no ZIP):

- `infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1`
- `infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1`

## Uso

Na raiz do repositorio, com aplicacao, worker e scheduler parados:

```powershell
Set-Location 'C:\dev\rasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_COM_POLITICA_TEMPORARIA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Gate ALERTS pendente; nao ativar a aplicacao nem fazer commit.' }
git diff --check
git status
```

O script pedira os cinco numeros. Nenhum default sera oferecido; o operador podera cancelar antes de confirmar. Para fracao decimal, aceita ponto ou virgula, convertendo para ponto para a leitura Java.

O `C2-E-D-D-C-B ALERTS COM POLITICA PASS` depende de cinco rotas da CLI aprovadas; nao e mostrado se houver falha de autenticacao, consulta ou qualquer etapa anterior. O teste executa apenas consultas de leitura por `OperationalCliMain`, com as credenciais DPAPI da fase C2-E-D-C-B (fora do Git).

## Limites e continuidade

- A politica fornecida **nao e gravada no `.env`** nem em documento com valores; serve apenas para este teste. Antes do funcionamento permanente dos alertas, deve existir decisao operacional documentada e configuracao explicita na inicializacao do servico.
- Nao executar `migrate()`, iniciar daemon, worker ou scheduler nesta etapa.
- Nao reprovisionar credenciais, alterar roles, ACLs ou HBA.
- Nao exibir saida bruta Maven/CLI, senhas ou arquivos DPAPI.
- Se houver nova falha na rota ALERTS com politica completa, trata-la como incidente distinto e nao transformar o gate em PASS.
- Nao fazer commit/push ate o gate de cinco consultas ser aprovado e revisado.
