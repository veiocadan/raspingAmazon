# FASE 21 — Resultado consolidado e pendências do release v1.0.0

- **Projeto:** Rasping Amazon
- **Referência arquitetural:** ADR-0018 (aceita em 2026-10-12)
- **Branch de trabalho:** `feat/fase-21-seguranca-governanca-v1`
- **Última evidência local comunicada:** 2026-10-28
- **Estado:** implementação de preparação avançada; **release ainda não homologado nem publicado**

## 1. Objetivo e escopo

Consolidar a segurança, governança, retenção, recuperação, operação reproduzível e homologação da versão 1.0 sem reconstruir as FASES 0–20. A arquitetura permanece em um único módulo Maven, com PostgreSQL como autoridade do estado durável e CLI local.

Este relatório distingue **evidências de execução local**, **limitações de testes de preparação** e **gates remotos pendentes**. Os resultados a seguir foram comunicados pelo operador; arquivos e relatórios de `target/` ou de backup externo devem permanecer disponíveis para conferência.

## 2. Status por bloco

| Bloco | Situação em 2026-10-28 | Evidência e limite |
|---|---|---|
| **21-A — Contrato e baseline** | Trabalhado anteriormente | ADR-0018 aceita; histórico de implementação preservado na branch. |
| **21-B — Segredos e configuração** | Trabalhado anteriormente | Contratos de configuração e credenciais protegidas; nenhuma credencial deve integrar o commit. |
| **21-C — PostgreSQL e privilégios** | Trabalhado anteriormente | Separação de identidades, HBA e supervisão local, conforme documentação de cada gate. |
| **21-D — Governança** | Trabalhado anteriormente | Evidências de testes Java e contratos operacionais; manter os artefatos anteriores. |
| **21-E — Logs e retenção** | **Gate final de preparação aprovado** | Integração do supervisor, captura catalogada, segmentação, redação, reserva e lacre validados em ambiente controlado. Captura produtiva **não autorizada**. |
| **21-F — Backup e restauração** | **Gate real aprovado** | Backup lógico PostgreSQL 18.6 com SHA-256 e manifesto; restauração em contêiner isolado, com conferência de schema, contagens e histórico Flyway. Banco de origem preservado. |
| **21-G — Operação e empacotamento** | **Gate de distribuição aprovado** | ZIP de pré-release criado em `target/`; CLI com 15 dependências de runtime e ajuda executada sem banco. Não é release final. |
| **21-H — Homologação e release** | **Local parcialmente aprovado; remoto pendente** | Suíte Maven integral 1.879/1.879 aprovada; workflow da probe corrigido localmente. CI da branch, homologação operacional e release ainda pendentes. |

## 3. Evidências principais

### 3.1 Retenção, logs e supervisor — 21-E

- Testes sintéticos e de integração de preparação D1–D11 aprovados; colisão real entre processos PowerShell e impossibilidade de reutilização do diretório demonstradas.
- Gate final informou: supervisor integrado, contrato D1 atualizado, catálogo, redação, quotas e lacre aprovados no sandbox.
- Políticas preservadas: `config/operacao/continuous-startup-policy.v1.json` em `preparation_only`, com quatro autorizações falsas; `config/operacao/retention-policy.v1.json` em `inventory_only`, com captura produtiva e exclusões desautorizadas.
- **Limitação:** não houve captura produtiva contínua nem partida do daemon nesta etapa. Isso exige homologação operacional expressa antes de declarar o sistema em produção.

### 3.2 Backup/restauração — 21-F

- Em 2026-10-28 o procedimento criou backup lógico, manifesto e prova externos ao Git, sob `C:\dev\rasping-amazon-backups\postgres\`.
- SHA-256 informado para o backup aprovado: `1699AF7EDD060D8477A10F6A84440132E368B52A63F82BDE40CFBE388516EE06`.
- Contêiner restaurado: `rasping-21f-restore-9db93433fa04`, parado e preservado para auditoria.
- Conferidos: schema, contagens, histórico Flyway e integridade do artefato. O PostgreSQL de origem não foi alterado pelo procedimento de prova.
- **Manutenção:** estabelecer e operar frequência de backup, política de conservação e verificação recorrente de restauração antes da operação contínua; não ativar exclusões automáticas por inferência.

### 3.3 Distribuição — 21-G

- Distribuição de pré-release sob `target/`, contendo o JAR e 15 bibliotecas de runtime; CLI respondeu à opção de ajuda sem conectar ao PostgreSQL.
- Hash do ZIP informado: `658CC4BDB5F9ADB420259A5BC4417C9DFCFE4030BBE59AF807ED3728D11AC66D`.
- **Limitação:** o teste de ajuda comprova empacotamento e inicialização da CLI, mas não equivale a um ciclo produtivo supervisionado completo.

### 3.4 Suíte e banco isolado — 21-H

```text
Tests run: 1879, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- Execução local em 2026-10-28, com banco PostgreSQL isolado em Docker.
- Contêiner usado: `rasping-21h-tests-cf5d093bc989`, parado e preservado.
- Relatórios Maven anteriores foram preservados antes da nova execução.
- A suíte hermética **não** equivale à prova externa da fonte Amazon ou ao início do daemon produtivo.

## 4. Fonte Amazon e CI: pendência real

- A execução agendada no branch `main` em 2026-10-28 apresentou falha (GitHub Actions: https://github.com/veiocadan/raspingAmazon/actions/runs/40727724776).
- Três probes manuais de página de produto falharam por ausência do parâmetro obrigatório `amazon.probe.product-url` no workflow agendado.
- A probe da página de ofertas retornou `COLLECTION_HTTP_503` em três tentativas, terminando **INCONCLUSIVA**; não há evidência de coleta real aprovada nessa execução.
- O arquivo `.github/workflows/amazon-source-probe.yml` foi corrigido **localmente** para executar somente `AmazonDealsExternalProbeIT` na rotina agendada e exigir resultado conclusivo. A correção ainda depende de commit, push e validação remota.
- Não mascarar `503` como sucesso e não contornar bloqueios da fonte. Uma nova avaliação da disponibilidade externa é necessária para a decisão de release.

## 5. Requisitos restantes para v1.0.0

1. **Conferência do diff consolidado:** revisar os arquivos da FASE 21 e garantir que nenhum segredo, dump, log bruto ou arquivo de diagnóstico seja versionado.
2. **Documentação operacional final:** confirmar que o runbook cobre instalação, Playwright, migração com identidade autorizada, CLI, status/pausa/retomada, início e parada supervisionados, backup/restauração, atualização e diagnóstico; registrar frequência e conservação de backups/logs.
3. **Homologação operacional explícita:** demonstrar, em ambiente controlado e com autorização separada, o ciclo real do daemon com mínimo privilégio, parada cooperativa, captura de logs e tratamento de efeitos externos. Não alterar flags produtivas apenas para passar testes.
4. **Fonte externa:** revisar a probe Amazon após a correção do workflow, incluindo o resultado HTTP `503` e o impacto para operação real.
5. **CI/release:** revisar o staged diff; realizar commit consolidado quando aprovado; publicar a branch; confirmar CI de PR; fazer merge em `main`; validar CI pós-merge; estabelecer versão final e só então criar a tag `v1.0.0`.
6. **Estado documental:** atualizar README e este relatório para o estado efetivamente homologado antes da tag final.

## 6. Bloqueios que permanecem

- Sem partida do daemon, migração Flyway, captura externa contínua, reinício automático nem publicação externa não autorizada.
- Sem retenção destrutiva de registros de negócio e auditoria.
- Sem remoção automática de backups, contêineres ou evidências de teste.
- Sem tag ou declaração de v1.0 concluída com CI remoto ou homologação operacional pendente.

## 7. Critério de encerramento

A FASE 21 e a versão `v1.0.0` só serão marcadas **CONCLUÍDAS** depois dos gates do item 5. Até lá, a implementação local pode estar aprovada sem que a operação produtiva e o release estejam aprovados.
