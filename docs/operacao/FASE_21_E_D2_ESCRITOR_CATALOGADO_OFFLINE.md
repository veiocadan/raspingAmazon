# FASE 21-E-D2 — Escritor segmentado catalogado (prototipo offline)

## Estado

**Prototipo aprovado apenas apos os testes em Windows.** O pacote nao contem instrucao para ativar captura continua, executar daemon, alterar supervisor ou excluir arquivos. `preparation_only`, `inventory_only` e os quatro bloqueios de startup continuam obrigatorios.

## Escopo implementado

- Escrita em segmentos JSON Lines com `FileMode.CreateNew`, `FileShare.Read`, `Flush(true)` e limite por segmento/total.
- Validacao independente dos seis eventos do catalogo B8 e de marcadores seguros `runtime.stdout.redacted` e `runtime.stderr.redacted`.
- Somente campos textuais fixos do catalogo. Identificadores e duracao sao numericos; `errorCode` de falha e sempre `REDACTED`.
- Rejeicao de propriedades adicionais, chaves duplicadas, JSON com escapes/formatos ambiguos e texto livre.
- Sessao lacrada apos falhas; nao faz limpeza, truncamento, reabertura de diretorio com evidencias, nem exclusao.
- Cada gravacao revalida a politica, o escopo `target/retencao-21eb1-testes/cenario-<GUID>` e a ACL NTFS privada.
- Testes criam unicamente cenarios privados pelo helper B1; evidencias e sentinelas de testes ficam preservadas em `target/`.

## Nao implementado nem autorizado

1. **Escritor externo produtivo**: `New-21ED2-SessaoProdutiva` sempre recusa, independentemente dos argumentos. A pasta `C:\dev\rasping-amazon-operacao\logs` nao e criada nem lida pelo pacote (somente se verifica sua existencia para garantir que ela nao mudou).
2. Captura de `stdout` ou `stderr` de Java, supervisao de processo e parada cooperativa por falha.
3. Rotacao operacional real de 16 MiB/512 MiB, isolamento entre instancias, recovery de sessoes interrompidas ou recuperacao de falhas de disco ao longo de dias.
4. Retencao destrutiva, exclusao de relatórios, logs ou backups.

## Dependencias

- `FASE_21_E_A_FUNCOES_RETENCAO.ps1`
- `FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1`
- `FASE_21_E_B8_FUNCOES_CATALOGO.ps1`
- Os contratos versionados `continuous-startup-policy.v1.json`, `retention-policy.v1.json` e `operational-log-catalog.v1.json`.

## Testes

Executar `FASE_21_E_D2_TESTAR_ESCRITOR_OFFLINE.ps1` em novo PowerShell 5.1 com `-NoProfile -ExecutionPolicy Bypass -File`. O teste valida: catalogo, ordem, segmentacao, seguranca de campos, rejeicao de caminhos externos, bloqueio de sessao, limite de 512 bytes, colisao de nomes e preservacao de bytes anteriores.

## Limite do gate

D2 **nao e um componente pronto para producao**. O proximo gate precisara desenhar um escritor externo com exclusividade de instancia, ACL de diretorio operacional, verificacao de espaco, protecao contra falhas ao gravar sob volume cheio, correlacao com PID/registro de supervisao, e parada cooperativa do Java. A transicao exigira testes especificos e nova autorizacao separada. Nenhum teste D2 autoriza alterar o supervisor.
