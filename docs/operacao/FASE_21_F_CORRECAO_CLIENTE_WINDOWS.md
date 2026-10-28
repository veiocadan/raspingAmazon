# FASE 21-F — Correcao de argumentos Docker no Windows PowerShell

## Motivo

A tentativa inicial em `BackupERestauracao` falhou na linha de invocacao
`docker run ... sh -c $sh` com `Syntax error: end of file unexpected`.
A concatenacao de argumentos nativos com aspas internas no Windows
PowerShell 5.1 nao e confiavel para passar um programa shell completo.

## Alteracoes concentradas

1. `FASE_21_F_BACKUP_RESTAURACAO.ps1` (substituido por completo):
   invoca `sh /scripts/FASE_21_F_CLIENTE_POSTGRES.sh backup|counts`, sem `sh -c`.
   Usa `--mount` de `infra/postgres/backup` somente para leitura. A consulta
   da restauracao tambem utiliza `psql -f`, sem passar SQL em `-c`.
2. `FASE_21_F_CLIENTE_POSTGRES.sh` (novo): recebe a credencial DPAPI
   exclusivamente por stdin do cliente Docker; restringe a operacao a
   backup ou contagem somente leitura, e nao grava a senha em disco.
3. `FASE_21_F_CONTAGENS_READONLY.sql` (novo): consulta somente leitura,
   usada pela origem e pela restauracao.

A image continua `postgres:18.6`. O banco original nao recebe comandos de
escrita. O destino do backup continua fora do Git com ACL privada. A
restauracao continua em container sem rede e com nome independente.

## Evidencias anteriores e recuperacao

A tentativa que falhou pode ter criado diretorios ou arquivo parcial.
Nao remover, substituir, reutilizar ou sobrescrever tais evidencias.
Cada nova tentativa cria um nome de backup e container de restauracao novos.

O modo `Verificar` nao acessa dados. O modo `BackupERestauracao` continua
exigindo `-ConfirmarBackupRestauracao` e requer autorizacao consciente,
porque acessa o banco original para leitura e cria backup/restauracao real.

## Validacao minima

1. Instalar o pacote com verificacao de SHA-256 do ZIP e dos arquivos.
2. Executar `-Modo Verificar` (sem banco).
3. Executar **uma vez** `-Modo BackupERestauracao -ConfirmarBackupRestauracao`,
   somente com autorizacao explicita, e conferir os `PASS` de dump,
   restauracao, contagens e SHA-256.

Nao fazer commit/push nem excluir backups antes dos gates finais da FASE 21.
