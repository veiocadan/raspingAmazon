# FASE 21-E-C1 — Simulação não destrutiva de retenção

## Estado

Implementação preparatória e **somente leitura**; não é limpeza automática. Nenhuma operação de exclusão é autorizada. Mantêm-se `preparation_only` e `inventory_only`.

## Fontes de contrato

- `config/operacao/retention-policy.v1.json`: escopos locais e prazo **proposto** de sete dias; `destructive_actions_authorized: false`.
- `config/operacao/continuous-startup-policy.v1.json`: daemon bloqueado.
- `infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1`: contrato e inventário E-A usados como referência independente.
- ADR-0018, seção 10: preservação do histórico durável e separação de logs/diagnósticos temporários.

## Escopo exato

1. `target/diagnostics` — escopo de diagnósticos locais.
2. `target/surefire-reports` — relatórios de teste Maven.

Os diretórios são apenas enumerados, sem leitura de conteúdo dos arquivos. Junctions, symlinks e reparse points são ignorados, sem segui-los. A função de validação E-A também rejeita redirecionamentos na ancestralidade do escopo.

O prazo é **proposto**, não é uma autorização de apagar. Um arquivo é classificado como candidato somente quando `LastWriteTimeUtc` é estritamente anterior ao instante UTC de referência menos sete dias. A igualdade com a fronteira não torna o arquivo candidato. Alterar a data de modificação pode alterar o inventário; isso não constitui prova de que o arquivo é dispensável.

Os resultados expõem apenas totais agregados: quantidade de arquivos, bytes, candidatos simulados, bytes correspondentes e redirecionamentos ignorados. Nenhum caminho individual de arquivo é impresso, exportado ou armazenado.

## Execução

No diretório raiz do repositório, em PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_C1_TESTAR_SIMULACAO.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Gate E-C1 reprovado' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_C1_SIMULAR_RETENCAO.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Simulacao E-C1 reprovada' }
```

## Garantias e limitações

- Todos os testes sintéticos funcionam sobre objetos em memória; não são criados ou removidos diretórios para testar o comportamento.
- A simulação real cruza os totais com a implementação do inventário E-A, usando o mesmo instante UTC.
- Arquivos existentes são **preservados**, mesmo que classificados como candidatos.
- Não se acessam logs operacionais externos, backups, credenciais, supervisão ou PostgreSQL.
- Artefatos GitHub Actions (3 dias) não são examinados, por pertencerem a serviço externo.
- A política de logs contínuos (30 dias, 16 MiB por segmento, 512 MiB total) não é ativada ou executada nesta etapa.
- Se o sistema de arquivos sofrer alterações concorrentes e as contagens divergirem, a simulação falha de modo fechado.
- Não há limpeza, agendamento, ativação de captura, rotação, mutação de configurações ou geração de manifestos.

**C1 não libera a retenção destrutiva.** Sua aprovação permite avaliar, em fase posterior, os contratos de elegibilidade e proteção de arquivos sem tocar nos dados reais.
