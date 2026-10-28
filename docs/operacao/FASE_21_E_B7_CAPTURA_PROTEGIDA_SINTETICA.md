# FASE 21-E-B7 — Captura protegida integrada (somente sintética)

## Objetivo

Integrar **sem substituição de componentes anteriores** a leitura concorrente limitada em blocos da B4/B5, o contrato JSON/diagnóstico da B3, a minimização dos campos textuais livres da B6 e o escritor segmentado privado da B1.

Este gate é apenas preparatório. Não inicia Java, daemon, scheduler, PostgreSQL, Flyway, supervisor real, integrações externas ou rotação/limpeza. Não cria nem escreve em `C:\dev\rasping-amazon-operacao\logs`. O único destino autorizado é um cenário inédito e isolado sob `target/retencao-21eb1-testes`, sujeito à verificação de ACL NTFS e de caminho sem junction ou link simbólico realizada pela B1.

## Arquivos novos

- `infra/retencao/FASE_21_E_B7_FUNCOES_CAPTURA_PROTEGIDA.ps1`: novo ponto de captura, restrito ao executável Windows PowerShell do teste, **não** plugável no supervisor.
- `infra/retencao/FASE_21_E_B7_TESTAR_CAPTURA_PROTEGIDA.ps1`: testes independentes e adversariais.

Dependências já aprovadas: B1 funções, B3 funções, B4 funções revisadas na B5, B6 funções, contrato 21-E-A e política startup `preparation_only`.

## Contrato dos eventos

`stdout`: eventos JSON bem formados aceitos pelo classificador B3 são transformados pela B6 em projeção com `timestamp`, `level`, um marcador constante de evento e apenas IDs/duração numéricos explicitamente validados. `event`, `component`, `operation`, `outcome`, `jobType`, `asin`, `integration`, `failureOrigin`, `failureType` e `errorCode` originários **não** são copiados. Saídas não classificadas recebem apenas marcadores sem texto original.

`stderr`: apenas marcadores redigidos; nunca grava stack trace, mensagem bruta, caminho ou exceção arbitrária.

Tanto `stdout` quanto `stderr` têm limite de linha de 32.768 caracteres no leitor; gravação usa B6, respeitando a quota combinada por duas sessões e o tamanho individual de segmentos B1. Se uma classificação/gravação falha, ambos os canais são lacrados. Falhas de linha excessiva, timeout ou saída não zero encerram a captura e preservam os segmentos anteriores. Nenhum arquivo anterior é automaticamente excluído, modificado ou truncado.

## Cenários cobertos pelo teste

1. Quarenta eventos mistos: 20 por canal; 10 estruturados minimizados e 30 marcadores. Conferência de ordem em cada canal, contagem, segmentação e ausência de tokens fictícios.
2. Código de saída 17: falha detectada com evidências parciais dos dois canais preservadas.
3. Quota compartilhada reduzida: recusa novas escritas e lacra os dois canais.
4. Linha de 40.000 caracteres: rejeição com evento anterior preservado.
5. Falha de escrita injetada via `FileStream.Dispose()` após primeira evidência: hash SHA-256 conservado, duas sessões lacradas e nova escrita negada.
6. Timeout de 5 segundos de filho sintético: interrupção apenas do subprocesso próprio, sem eventos inventados.

## Limitações e não autorização

- A B7 não é um launcher para a JVM nem substitui `FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1`.
- A projeção conservadora reduz informação diagnóstica; é necessário definir um dicionário versionado de códigos seguros e um contrato de observabilidade produtiva antes da integração real.
- Não há rotação destrutiva nem política de 30 dias aplicada; os prazos continuam apenas propostos.
- Não há garantia de isolamento contra processos locais com o mesmo usuário ou administradores; permissões NTFS seguem o modelo de B1.
- Falhas de energia/disco e comportamento do `PrintWriter` Java em produção ainda não foram testados.
- Validar no Windows antes de considerar B7 concluída. Nunca ativar `continuous_log_capture_authorized`, `destructive_actions_authorized` ou modificar as quatro flags de startup para estes testes.
