# FASE 21-E-B5 — Limites de leitura e recuperação isolada

Data: 2026-10-15. Branch: `feat/fase-21-seguranca-governanca-v1`.
Estado inicial: aguardando gate de execução no Windows. **Nenhuma captura produtiva autorizada.**

## Fundamentação

Os gates B1–B4 demonstraram segmentação, validação JSON, classificação redigida,
concorrência em `stdout`/`stderr`, limites combinados e tratamento de timeout.
A revisão de segurança detectou uma lacuna: a leitura com `StreamReader.ReadLineAsync()`
pode acumular uma linha arbitrariamente grande **antes** de `Convert-21EB3-LinhaConsole`
examinar seu tamanho. O limite de 32.768 caracteres na etapa B3 era, portanto,
insuficiente para limitar memória durante a ingestão de saída não confiável.

## Alteração técnica da B5

O arquivo `FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1` é substituído integralmente
por uma revisão que preserva a assinatura pública e os contratos B1/B3.

- Usa `StreamReader.ReadAsync(char[], offset, count)` com blocos fixos de 1.024 caracteres em cada fluxo.
- Mantém somente uma linha parcial por fluxo; rejeita a próxima entrada ao ultrapassar 32.768 caracteres.
- Processa linhas completas após `LF`, remove somente `CR` final, e finaliza a última linha parcial em `EOF`.
- Continua separando `stdout` e `stderr` e processando ambos concorrentemente.
- Usa as regras de redação/compatibilidade existentes na B3 antes de persistir.
- Aplica limites de segmentos da B1 e de volume total conjunto; usa criação exclusiva de segmento (`CreateNew`).
- Na falha, encerra apenas o processo PowerShell sintético criado pela chamada; fecha as sessões sem excluir arquivos.
- Mantém os registros já gravados e não persiste a linha longa que provocou a falha.

## Cenários automatizados B5

1. Entrada de `stdout` com 40.000 caracteres após evento legítimo: rejeitada, evento anterior mantido.
2. Entrada de `stderr` com 40.000 caracteres após marcador legítimo: rejeitada, marcador anterior mantido.
3. Evento JSON sem `LF` no fim de arquivo, combinado com `CRLF` em `stderr`: sem perda de registro.
4. Colisão com nome de segmento já existente: falha fechada e conteúdo preexistente intacto.
5. Verificação dos segmentos: nenhum fragmento de entrada excessiva é persistido.
6. Regressão: executar novamente **B4** usando o capturador substituído antes do teste B5.

Todos os diretórios de teste são criados pelo `New-21EB1-Sandbox` em
`target/retencao-21eb1-testes`, com ACL protegida e nomes exclusivos.
Nenhum script deste pacote exclui artefatos, altera PostgreSQL ou credenciais,
abre supervisor, executa Java, altera `continuous-startup-policy.v1.json`,
modifica o destino operacional de logs ou autoriza captura contínua.

## Limitações abertas

- Uma linha maior que o teto faz a captura **falhar fechado**, em vez de criar
  automaticamente resumo de toda a linha ou continuar executando o processo.
- O limite protege a montagem de linhas, não a memória integral do processo PowerShell ou JVM.
- Não foi demonstrada integração com launcher real, nem captura de eventuais processos netos.
- Não foi comprovada resistência à alteração concorrente de diretórios por um adversário
  com permissões de escrita, falha de energia, disco cheio real ou falha catastrófica de ACL.
- A B3 não garante detecção de todas as credenciais se um valor secreto for indevidamente
  colocado em um campo JSON formalmente permitido; essa fronteira requer revisão adicional.
- Nenhuma política de descarte, rotação por idade ou recuperação pós-reinicialização
  foi ativada. Retenção efetiva permanece pendente.

## Critério de gate

Validar sintaxe da revisão B4 e novo teste B5 com `Resolve-Path`, executar testes
B4 e B5 no Windows e verificar `git status --short` e branch. Se houver falha,
interromper sem commit. Registrar saídas dos testes, não conteúdo bruto de logs.
