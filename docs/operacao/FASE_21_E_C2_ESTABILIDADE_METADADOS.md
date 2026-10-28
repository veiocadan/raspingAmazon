# FASE 21-E-C2 — Estabilidade de metadados antes de qualquer politica destrutiva

## Objetivo

Verificar, **somente por leitura**, se os arquivos dos dois escopos permitidos pela politica de retencao permanecem com os mesmos nomes/caminhos, tamanhos, datas de criacao/modificacao e atributos em duas passagens sucessivas. Esta e uma evidencia preparatoria, **nao uma autorizacao para apagar**.

## Contratos preservados

- `execution_mode=preparation_only` na politica de startup, com as quatro autorizacoes falsas;
- `execution_mode=inventory_only`, `destructive_actions_authorized=false` e `continuous_log_capture_authorized=false` na politica de retencao;
- dois escopos: `target/diagnostics` e `target/surefire-reports`;
- sete dias como **prazo proposto**, nao prazo de exclusao autorizado.

## Testes e auditoria

- `FASE_21_E_C2_TESTAR_ESTABILIDADE.ps1`: testes hermeticos de alteracao de metadados, caminho, quantidade, link simbolico, origem externa, fuso ambiguo e alteracao de autorizacao. Usa objetos em memoria; nenhum arquivo artificial e criado.
- `FASE_21_E_C2_CONFERIR_ESTABILIDADE.ps1`: duas fotografias sucessivas dos metadados reais em memoria. Compara todos os campos considerados e confronta os totais com a simulacao C1. Exibe somente totais agregados.
- `FASE_21_E_C2_FUNCOES_ESTABILIDADE.ps1`: funcoes de verificacao e leitura. Nao possui limpeza, remocao, copia, renomeacao, alteracao de permissao ou partida de processo.

Caminhos de arquivos concretos sao mantidos somente na memoria do processo durante a comparacao, nunca escritos no disco nem impressos na saida. Um link simbolico/reparse point encontrado na enumeracao e ignorado; um redirecionamento na ancestralidade do escopo e rejeitado.

## Limites de seguranca

- Metadados iguais **nao demonstram identidade fisica**: pode ocorrer substituicao por outro arquivo com iguais datas, tamanho e atributos.
- O conteudo **nao e lido nem comparado**; portanto alteracoes de conteudo que preservem os metadados podem passar despercebidas.
- Uma mudanca pode ocorrer apos a segunda leitura. Esta prova **nao elimina TOCTOU** (*time of check to time of use*).
- A comparacao entre C2 e C1 tambem nao constitui um travamento do sistema de arquivos.
- Qualquer mecanismo futuro de exclusao exigiria decisao explicita separada, validacao de identidade de arquivo por handle no momento da acao, politica de concorrencia, testes adversariais de seguranca e uma autorizacao versionada especifica. **Nenhum desses recursos e implementado em C2.**
- O inventario de 708 relatórios Maven anterior ao prazo permanece preservado.

## Execucao

No repositorio, Windows PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_C2_TESTAR_ESTABILIDADE.ps1'
if ($LASTEXITCODE -ne 0) { throw '21-E-C2 reprovada' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/retencao/FASE_21_E_C2_CONFERIR_ESTABILIDADE.ps1'
if ($LASTEXITCODE -ne 0) { throw '21-E-C2 leitura real reprovada' }
```

**Resultado esperado:** somente mensagens de aprovacao e contadores agregados, sem qualquer exclusao e sem modificar `preparation_only` ou `inventory_only`.
