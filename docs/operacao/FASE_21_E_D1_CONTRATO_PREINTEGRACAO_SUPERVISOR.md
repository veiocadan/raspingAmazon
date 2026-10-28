# FASE 21-E-D1 — Contrato de pre-integracao do supervisor

**Estado:** revisao estatica offline. **Nao e captura produtiva.**

## Fontes auditadas

Em 2026-10-27 o ZIP `FASE_21_E_D_FONTES_SUPERVISOR.zip`, SHA-256
`C322905F1266FF6F28EF5A7461D361C2590219E57B4A0E24DF09CF4B84C68873`,
forneceu 20 arquivos. O contrato D1 fixa o SHA-256 de cinco fontes criticas.
Mudancas posteriores exigem nova auditoria; a D1 nao as aceita por padrao.

## Conclusoes comprovadas por revisao

- O supervisor existente exige autorizacao de partida antes de `Process.Start`; hoje opera sob `preparation_only`. Possui reserva atomica, identificacao PID + creation time, mecanismo de parada cooperativa e recuperacao de tentativa sem registro. Os caminhos protegidos sao externos ao repositorio.
- B9 inicia PowerShell sintetico sob `target/retencao-21eb1-testes`, com encerramento forcado exclusivamente do filho sintetico. Seu escritor B1 recusa pastas externas ao sandbox. **Nunca ligar a B9 diretamente ao Java real.**
- O catalogo B8 aceita seis eventos autorizados; B9 protege texto livre, segmenta e redige `stderr` em testes.
- O Java fornece JSON Lines em `System.out`; mensagens diagnósticas podem sair em `System.err`. `PrintWriter` nao fornece falha de escrita por excecao automaticamente; a revisao do Java deve considerar observabilidade de falhas sem alterar a semantica best-effort do dominio.
- A politica de retencao continua `inventory_only`, com captura e exclusoes desautorizadas. O destino `C:\dev\rasping-amazon-operacao\logs` e apenas proposta, nao existe por exigencia desta D1.

## Dependencias para uma integracao futura segura (D2 em diante)

1. Criar escritor operacional **novo** para o diretorio externo, com criacao atomica, ACL privada validada, restricao de path/reparse e limites de segmento e total sem exclusao automatica; nao reutilizar a classe de testes B1 como escritor produtivo.
2. Capturar os dois canais do Java simultaneamente com leitura limitada por blocos e sem guardar stderr bruto. Aplicar catalogo B8 antes de persistir; falhas de leitura, conversao, ACL e esgotamento devem lacrar ambos os canais.
3. Definir contrato de falha da captura: solicitar parada cooperativa do Java, preservar reserva/registro/PID e nao usar `Kill()` do sintetico na JVM real. Se o Java nao encerrar dentro do prazo, preservar estado, bloquear nova partida e exigir inspecao humana.
4. Provar que credenciais de runtime nao chegam a argumentos ou logs; nao imprimir mensagens brutas de excecoes. Garantir que o produtor Java nao silencie falhas criticas de escrita sem observabilidade.
5. Testar concorrencia, reinicio depois de falha, queda do supervisor, diretorio cheio/sem permissao, colisoes, paths simbolicos, segmentacao sem truncamento e fechamento antes de qualquer autorizacao produtiva.
6. Atualizar o supervisor e seus testes de maneira controlada, sem contornar `Exigir-PartidaAutorizada-C-C`; depois disso, fazer novo gate e nova decisao explicita de ativacao. Nenhum gate desta D1 concede autorizacao.

## Execucao

Os scripts `FASE_21_E_D1_TESTAR_PREINTEGRACAO.ps1` e
`FASE_21_E_D1_AUDITAR_PREINTEGRACAO.ps1` executam somente leituras e
validacoes. O primeiro testa adulteracoes em memoria; o segundo verifica
os hashes das fontes e os bloqueios ainda ativos. Nao se executa o launcher
produtivo. Nao ha exclusao, migracao ou alteracao nas politicas.

## Limites da evidencia

- Comparacoes de SHA-256 demonstram integridade **em relacao ao ZIP recebido**, nao autenticidade criptografica da procedencia.
- Inspecao de codigo e testes offline nao demonstram funcionamento ou seguranca do daemon em producao.
- A D1 nao conecta a B9 ao supervisor real; essa conexao esta explicitamente pendente.
