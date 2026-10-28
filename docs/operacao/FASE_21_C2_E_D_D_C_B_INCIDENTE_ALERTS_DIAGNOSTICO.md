# FASE 21-C2-E-D-D-C-B - Incidente na consulta ALERTS

## Evidencia

Em 12/10/2026, o operador executou a CLI real com credenciais DPAPI separadas. `EVALUATIONS`, `RUNS`, `JOBS` e `PUBLICATIONS` passaram; `ALERTS` falhou. O executor suprimiu intencionalmente a saida bruta. O `finally` restaurou as variaveis originais. **Gate C2-E-D-D-C-B permanece reprovado**, apesar dos quatro comandos validados.

## Hipotese fundamentada

`EnvironmentOperationalAlertPolicyProvider.load()` exige explicitamente cinco variaveis `ALERT_*`, sem defaults. O executor original C2-E-D-D-C-B so define `DB_*`; portanto, se a politica de alertas nao estiver no ambiente herdado pelo Maven, `alerts list` falha antes ou durante a consulta. Isso **nao prova** ainda qual causa ocorreu: a politica pode estar completa e existir uma falha SQL ou outra falha.

## Diagnostico seguro, sem repetir execucao

1. Extrair o ZIP na raiz do repositorio, preservando caminhos.
2. Na mesma conta Windows que executa a aplicacao:

```powershell
Set-Location 'C:\devasping-amazon'
$ErrorActionPreference = 'Stop'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_DIAGNOSTICAR_ALERTS_SEM_SEGREDOS.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Diagnostico nao concluido; nao prosseguir' }
git diff --check
git status
```

3. Enviar apenas a saida do diagnostico, que **nao imprime valores**. Nenhuma alteracao de `.env`, de permissoes ou de senhas deve ser efetuada.

## Interpretacao

- `PROCESSO=AUSENTE`: Maven/Java nao recebe a variavel; uma chave existente no `.env` nao e automaticamente exportada.
- `PROCESSO=INVALIDA`: a variavel esta definida, mas fora do contrato numerico minimo.
- `.ENV=VALIDA_NAO_IMPORTADA`: existe valor plausivel no arquivo, que nao foi exportado pela etapa; ainda nao e uma aprovacao dos limiares operacionais.
- `POLITICA PROCESSO COMPLETA`: buscar a causa em SQL/execucao; **nao** conceder permissoes sem auditoria.
- `POLITICA PROCESSO INCOMPLETA`: existe ao menos uma variavel ausente/invalida; devem-se definir os valores por decisao operacional, nao por defaults inventados.

O script somente le o codigo, o ambiente de seu processo e a existencia/forma das cinco chaves no `.env`. Nao executa Docker, PostgreSQL, Java, Maven, Flyway ou CLI. Nao mostra quaisquer valores. Nao altera arquivos ou o ambiente. O diagnostico termina com `PASS` independentemente de as variaveis existirem, pois o objetivo e **identificar o motivo**.
