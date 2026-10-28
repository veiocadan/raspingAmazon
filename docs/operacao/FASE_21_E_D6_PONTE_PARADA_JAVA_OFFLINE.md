# FASE 21-E-D6 — Ponte real PowerShell/Java, somente de teste

## Objetivo

Validar que o **mesmo** `SupervisedStopSignal` utilizado pela aplicacao Java reconhece
um arquivo de pedido de parada criado por PowerShell. A D5 ja provou um filho
PowerShell sintetico; a D6 examina a fronteira de linguagem, mas **nao** executa
`ContinuousProcessingMain` nem o supervisor Windows.

## Componentes novos

1. `src/test/java/com/raspingamazon/infrastructure/bootstrap/Phase21EJavaStopProbeMain.java`
   — fixture Java sem JDBC, rede, scheduler ou qualquer integracao externa.
2. `infra/retencao/FASE_21_E_D6_TESTAR_PONTE_JAVA.ps1` — unico executor de gate.

Nao sao substituidos arquivos antigos. A fixture importa apenas a classe
`SupervisedStopSignal` do codigo existente e bibliotecas padrao Java.

## Requisitos e limites

- Windows PowerShell 5.1 e Java 25, conforme contrato Maven do projeto.
- `config/operacao/continuous-startup-policy.v1.json` em `preparation_only` com flags
  desligadas; politica de retencao `inventory_only` com captura/exclusoes desligadas.
- `src/main/java/.../SupervisedStopSignal.java` sem alteracoes tracked locais.
- Branch `feat/fase-21-seguranca-governanca-v1`.
- `target/` existente; cenarios privados criados pelo contrato B1.
- Nenhuma escrita fora de `target/retencao-21eb1-testes/` alem da compilacao normal
  Maven em `target/`.
- O subprocesso Java tem prazo autonomo de 1,5 a 5 segundos. Nao usar `Kill()` ou
  `taskkill`; se ele permanecer ativo, interromper e inspecionar manualmente.

## Gate

Depois de instalar os arquivos em seus caminhos originais:

```powershell
Set-Location -LiteralPath 'C:\Dev\rasping-amazon'
.\mvnw.cmd -o -q -DskipTests test-compile
if ($LASTEXITCODE -ne 0) { throw 'Compilacao D6 reprovada' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra\retencao\FASE_21_E_D6_TESTAR_PONTE_JAVA.ps1'
if ($LASTEXITCODE -ne 0) { throw 'Gate D6 reprovado' }
```

A flag Maven `-o` proibe downloads de dependencias durante o gate. A compilacao
inclui os testes, mas **nao os executa**, nao abre banco e nao inicia o daemon.
Se artefatos Maven nao estiverem em cache, a compilacao offline pode falhar: nao
retirar `-o` sem revisar previamente as dependencias ausentes.

## Provas esperadas

1. Pedido `CreateNew` em sandbox privado reconhecido pelo watcher Java,
   encerramento voluntario codigo 0.
2. Nenhum pedido criado: expiracao autonoma codigo 75.
3. Pedido preexistente: startup da fixture recusado codigo 76 e sem readiness.
4. Nenhuma saida bruta do subprocesso, nenhum arquivo apagado,
   nenhuma operacao produtiva.

## Nao demonstrado

- Captura de logs do `ContinuousProcessingMain` real;
- sistema de parada do runtime, workers e suas conexoes JDBC;
- ACL e roteamento do diretorio operacional externo;
- propagacao de falha real do writer Java (`PrintWriter`);
- autorizacao para iniciar o daemon ou habilitar exclusoes.

O contrato produtivo permanece **bloqueado**. A D6 e uma evidencia de compatibilidade,
nao uma autorizacao para mudanca operacional.
