# FASE 21-E-B2 — Captura de subprocesso sintético, separada por canal

## Escopo

Este gate exercita o consumo concorrente de `stdout` e `stderr` de **um processo PowerShell temporário, criado exclusivamente pelo teste**. Integra a captura ao escritor sintético B1, sem modificar os scripts do supervisor C, Java, PostgreSQL, Flyway ou a política de startup.

Não existe autorização de captura contínua de daemon. `retention-policy.v1.json` continua `inventory_only`, `continuous_log_capture_authorized=false` e `destructive_actions_authorized=false`; o startup permanece `preparation_only`, com as quatro autorizações `false`.

## Mecanismo

- O teste cria um filho `powershell.exe` do sistema Windows, exclusivamente com código sintético pré-determinado; nenhum executável da aplicação é iniciado.
- `ProcessStartInfo` redireciona `StandardOutput` e `StandardError` separadamente.
- Um `ReadLineAsync` por canal consome os pipes sem esperar o término do outro. A captura **não** faz `ReadToEnd`, evitando acumular toda a saída na memória.
- O escritor B1 mantém integridade de uma linha JSON por evento, criação exclusiva de segmentos, ACL privada, limites de segmento e ausência de exclusões.
- O teste verifica ordem **dentro de cada fluxo**; ordem global entre stdout e stderr **não é garantida** pelo sistema operacional e não é declarada como validada.
- Contabilidade adicional limita o total **combinado** de stdout+stderr em cada cenário sintético. Ao atingir a quota, a captura falha e preserva os segmentos existentes.
- Saída não zero, evento inválido e timeout falham fechado. Apenas o processo filho criado pelo próprio teste pode ser encerrado pela rotina de proteção.
- Nunca escreve conteúdos de `stdout` ou `stderr` no console do operador.

## Limites intencionais

Os arquivos de teste permanecem em subpastas aleatórias, privadas e isoladas de `target/retencao-21eb1-testes/`, pois o escritor B1 restringe seu destino a esse sandbox. Nenhum arquivo é apagado automaticamente.

O modo B2 **não está pronto para produção**. `ReadLineAsync()` só impõe um limite de comprimento **após** receber uma linha; antes da futura integração real deve existir leitura incremental limitada, tratamento de saídas não JSON, término e drenagem sob falhas, robustez frente a quedas de energia, quotas globais compartilhadas entre sessões e eventual proteção contra perda de logs. Não encaminhar saídas reais do daemon para estes testes.

A retenção por idade ainda é apenas uma proposta, não uma rotina ativa. O diretório `C:\dev\rasping-amazon-operacao\logs` deve permanecer intocado.

## Evidências esperadas

- 64 eventos JSONL em stdout e 64 em stderr, com mais de um segmento por canal;
- integridade e sequência individuais preservadas;
- filho encerrado com código 17 produz reprovação, preservando os eventos existentes;
- campo proibido e tentativa de atribuir evento ao fluxo errado não são persistidos;
- quota combinada rejeita novas gravações sem eliminar arquivos;
- timeout de subprocesso sintético é identificado e o processo filho próprio é encerrado;
- startup e retenção continuam inativos.

## Execução

Executar **na raiz do repositório** com Windows PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra/retencao/FASE_21_E_B2_TESTAR_CAPTURA_SINTETICA.ps1"
if ($LASTEXITCODE -ne 0) { throw "Gate 21-E-B2 reprovado" }
git status --short
```

Sem branch nova e sem commit até a consolidação da FASE 21-E. Registro do resultado real será incluído após a execução.
