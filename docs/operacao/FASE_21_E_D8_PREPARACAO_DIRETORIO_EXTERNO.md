# FASE 21-E-D8 — Contrato preparatorio do diretorio de logs operacional

## Estado

**DESENHO_NAO_ATIVADO.** A D8 nao altera o supervisor, nao implementa o escritor produtivo, nao cria/inspeciona `C:\dev\rasping-amazon-operacao\logs` e nao autoriza captura. `preparation_only`, `inventory_only` e os quatro bloqueios de startup permanecem obrigatorios. Nenhum arquivo deve ser removido.

## Reutilizacao dos contratos existentes

- `config/operacao/retention-policy.v1.json` continua sendo a UNICA fonte dos valores propostos para logs continuos: diretorio `C:\dev\rasping-amazon-operacao\logs`, prazo **30 dias**, tamanho maximo por segmento **16 MiB** e quota proposta total **512 MiB**.
- `config/operacao/supervisor-log-integration-review.v1.json` (D1) continua proibindo integracao e fixando as cinco fontes examinadas; D8 exige seus hashes.
- `operational-log-catalog.v1.json` (B8) permanece `offline_validation_only`.
- A D2 continua sem permitir caminho externo; D3–D7 permanecem fixtures sintéticas, inclusive a JVM de teste com parada cooperativa.

## O que D8 comprova

1. Validacao estrita dos contratos ja versionados, da integridade das fontes D1, do caminho proposto e dos limites numericos.
2. Recusa de caminho interno ao Git, backup, UNC, traversal, alternate data stream e caminho externo divergente, por comparacao lexical; nenhum `Get-Item`, `Get-Acl` ou `Test-Path` e feito no diretorio externo.
3. Modelo de ACL NTFS testado **em memoria**: heranca bloqueada e somente operador atual, SYSTEM e Administradores com FullControl. Permissao de leitura para Usuarios ja reprova o modelo.
4. Modelo de nome de sessao nova requer PID confirmado, creation ticks confirmados, nonce de 128 bits e reserva exclusiva; reutilizacao e recusada.
5. `New-21ED8-SessaoProdutiva` falha incondicionalmente; nao e uma factory funcional.

## Limites e riscos ainda nao resolvidos

- Um modelo ACL em memoria NAO comprova a ACL real do caminho externo, seu volume, uso de NTFS ou ausencia de reparse points em ancestrais.
- Comparacao lexical NAO protege contra redirecionamento por links se a abertura futura nao for feita com verificacao por identificador/handles e verificacao apos abertura.
- Um nonce e um nome de sessao NAO criam reserva atomica real nem vinculam a sessao a PID + creation time. A prova de exclusividade continua pendente.
- Nao existe ainda escrita real em diretorio externo, validacao de disco cheio, queda brusca do supervisor, reabertura de sessoes apos falha ou evidencia de seguranca contra troca de caminho/ACL entre a verificacao e o uso.
- Prazo de 30 dias e apenas proposta, NAO rotina de exclusao. Nao ha acao de limpeza, rotacao destrutiva ou exclusao de logs.
- Nao ha qualquer integracao com `ContinuousProcessingMain`. Nao iniciar o supervisor nem trocar as autorizacoes para `true`.

## Teste

Executar em PowerShell 5.1, no repositorio e na branch `feat/fase-21-seguranca-governanca-v1`:

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/retencao/FASE_21_E_D8_TESTAR_PREPARACAO_EXTERNA.ps1`

O teste realiza apenas leituras de arquivos versionados e cria modelos de ACL e objetos adulterados exclusivamente em memoria. Nao abre o caminho externo, nao cria diretorios, nao cria processos de aplicacao, nao acessa banco ou credenciais e nao deleta dados.

## Proxima implementacao (nao autorizada pela D8)

Um adaptador operacional de logs devera possuir componentes separados para: preflight real de ACL/reparse/volume; abertura atomica de sessao exclusiva ligada ao PID e creation ticks do Java; segmentacao em writer novo e seguro com quotas e resposta fail-closed; dreno concorrente e limitado dos dois canais; pedido de parada cooperativa com supervisor retendo reserva/registro se a terminacao falhar; validacao adversarial e gate de homologacao offline. Somente apos esses gates e nova decisao explicita as politicas poderiam ser revistas.
