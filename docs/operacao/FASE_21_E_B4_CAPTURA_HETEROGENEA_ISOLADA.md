# FASE 21-E-B4 — Captura heterogênea de filho sintético (Windows)

## Escopo

Gate de integração de duas capacidades **já aprovadas isoladamente**: B1 (escrita segmentada e quotas) e B3 (classificação segura das saídas de console). B2 fundamenta a leitura concorrente de stdout/stderr e a detecção de código de saída/timeout.

**Não é um launcher de produção e não será reutilizado diretamente como supervisor.** O executor B4 inicia exclusivamente um processo `powershell.exe` local com script sintético fornecido pelo teste. Não inicia Java, não abre JDBC, não acessa PostgreSQL, não inspeciona backups, não modifica `continuous-startup-policy.v1.json` e não chama o supervisor.

## Arquivos adicionados

- `infra/retencao/FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1`: leitor simultâneo de stdout e stderr para processo de teste controlado. Normaliza linhas com `Convert-21EB3-LinhaConsole` e escreve com `Write-21EB1-EventoSintetico`.
- `infra/retencao/FASE_21_E_B4_TESTAR_CAPTURA_MISTA.ps1`: casos sintéticos de saída heterogênea, ordenação por canal, quota combinada, saída não zero e timeout.

## Segurança e comportamento esperado

1. `Assert-21EB1-Contexto` valida a política `preparation_only` e os dois flags de retenção desautorizados.
2. O processo filho de teste não herda variáveis de ambiente cujos nomes indiquem credenciais, tokens ou configurações sensíveis conhecidas.
3. Leitura simultânea de stdout/stderr, preservando a ordem **dentro de cada canal**; não há promessa de ordem global entre canais.
4. `stdout` estruturado compatível com a lista permitida passa como JSON; saída desconhecida vira marcador `runtime.stdout.unclassified` sem copiar conteúdo bruto.
5. `stderr` nunca é persistido como texto bruto; falhas conhecidas viram marcadores tipados, outras linhas viram `runtime.stderr.unclassified`.
6. Cada canal escreve em um cenário NTFS privado sob `target/retencao-21eb1-testes` com máximo de 512 bytes por segmento de teste.
7. Limite total combinado falha fechado, sem apagar arquivos já criados.
8. Código de saída diferente de zero falha com evidências parciais preservadas.
9. Timeout encerra **somente** o filho sintético criado pela chamada, mantendo o ambiente protegido.

## Limitações explícitas antes de eventual integração produtiva

- A implementação B4 é **um ensaio de orquestração**, não uma API de captura generalizada. A futura implementação de captura deverá consolidar a leitura concorrente para evitar duplicação estrutural com B2.
- `ReadLineAsync` não impõe limite de memória para uma linha sem quebra; será necessário implementar leitura com limite de tamanho **antes de qualquer uso produtivo**.
- A aceitação de valores estruturados por whitelist e expressão regular não oferece garantia absoluta contra dados sensíveis disfarçados em campos permitidos. A proteção deve ser reforçada/validada na fonte e no destino, com cenários adversariais e prova de não exposição de segredos.
- Marcadores não substituem stack traces e outras evidências brutas; a política de diagnóstico deve preservar o fato observável sem colocar texto não confiável no log protegido.
- Captura de produção continua `false`; nenhuma rotação com exclusão, TTL de 30 dias ou integração com o supervisor é ativada neste gate.
- Testes de concorrência entre múltiplos processos escritores, travamento/crash e recuperação de arquivos parcialmente escritos ainda são gates futuros.

## Execução no Windows

```powershell
Set-Location 'C:\Dev\rasping-amazon'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_B4_TESTAR_CAPTURA_MISTA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'FASE 21-E-B4 reprovada' }
git diff --check
git status --short
```

Somente dados sintéticos no sandbox privado `target/` são gravados. Evidências persistem até descarte posterior expressamente aprovado. **Não executar daemon, `mvn clean`, `git commit`, push ou limpeza nesta etapa.**
