# FASE 21-C2-E-D-D-D-D-I - Reserva e recuperacao de partida supervisionada

## Problema

O launcher antigo criava a JVM antes de gravar `rasping-amazon-daemon.v1.json`.
Se o supervisor falhasse nessa janela, o Java poderia permanecer ativo sem
registro verificavel. Uma segunda partida seria negada pelo inventario de
processos, mas a parada supervisionada nao possuiria identidade registrada.

## Solucao

- `Criar-ReservaDePartida-D-I` utiliza `FileMode.CreateNew` para estabelecer
  exclusao mutua duravel **fora do Git**, no diretorio protegido por ACL. A
  reserva precede a leitura DPAPI e a criacao da JVM.
- A reserva permanece durante a checagem CIM de PID, classe principal e
  horario de criacao. O registro definitivo usa arquivo temporario e rename.
- A reserva so e eliminada se pertence ao lancador (token unico) e o registro
  foi criado, ou quando e comprovado que nao existe Java filho sobrevivente.
- Falha com Java nao registrado: tenta parada **cooperativa**, somente se o
  processo filho for identificado pela API Windows; aguarda ate 30 segundos.
  Nunca usa `taskkill`, `Stop-Process` ou reinicio automatico.
- Se `Start()` foi tentado mas o handle nao e confiavel, a identidade nao pode
  ser validada, ou o Java nao parou no prazo, a reserva fica preservada.
  Este estado exige investigacao manual. Nao apagar arquivos de supervisao
  nem iniciar outro daemon automaticamente.
- A remocao do registro apos termino confere tanto PID quanto ticks de criacao.

## Teste I

`infra/postgres/security/FASE_21_C2_E_D_D_D_D_I_VALIDAR_TRANSICAO.ps1`
verifica a politica bloqueada, a sequencia de codigo e o comportamento real de
`FileMode.CreateNew`/tokens em diretorio temporario. Testa a retencao da
reserva quando o resultado de `Start()` e inconclusivo. Confirma a recusa da
partida real **antes de executar Start()**. Nao acessa PostgreSQL nem DPAPI.

## Limites e proibicoes

O teste I **nao exercita** uma falha real apos criar um Java produtivo, nem
prova o sucesso do shutdown durante uma corrida real. Esse cenario requer um
teste isolado de injecao de falhas, sem efeitos externos, antes de liberar o
modo `supervised`. Enquanto isso a politica continua `preparation_only` e
todos os bloqueios permanecem ativos. Nao executar Flyway migrate nem publicar.
