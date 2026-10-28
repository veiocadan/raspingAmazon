# FASE 21-E — Política operacional de logs e diagnósticos V1

## 1. Identificação

Projeto: Rasping Amazon.

Data do levantamento: 2026-10-15.

Fase: 21-E — Retenção e classificação dos dados.

Branch: feat/fase-21-seguranca-governanca-v1.

Referências arquiteturais:
- ADR-0011 — Observabilidade;
- ADR-0018 — Segurança, governança e fechamento da v1.0.

Documento complementar:
FASE_21_E_INVENTARIO_RETENCAO_V1.md.

Estado: política proposta, parcialmente implementada.
Nenhuma rotina de exclusão local está autorizada.

## 2. Separação das categorias

O sistema distingue:

1. Dados duráveis de negócio.
2. Eventos persistidos de auditoria.
3. Estado operacional necessário à execução.
4. Logs estruturados de execução.
5. Diagnósticos temporários da fonte externa.
6. Relatórios temporários de testes.
7. Arquivos de controle e supervisão.
8. Backups.

Essas categorias possuem requisitos diferentes e não
devem compartilhar uma rotina genérica de exclusão.

## 3. Dados duráveis PostgreSQL

As 29 tabelas inventariadas permanecem protegidas contra
expiração automática por idade.

Não existe autorização para:

- DELETE genérico de histórico;
- TRUNCATE de tabelas de negócio;
- DROP de estruturas operacionais;
- exclusão de versões históricas;
- exclusão de auditoria;
- descarte de estado de recovery ou idempotência.

A tabela flyway_schema_history também não participa de
rotinas de limpeza.

Uma eventual política futura de expiração exigirá nova
decisão arquitetural, análise de dependências, testes
e validação em ambiente isolado.

## 4. Logs estruturados Java

Origem confirmada:

ContinuousProcessingBootstrap configura o
JsonStructuredOperationalLogAdapter utilizando System.out.

Formato:
JSON Lines.

Fonte de verdade:
PostgreSQL, não os logs.

Situação atual:

Não foi confirmado mecanismo de captura persistente
com rotação e retenção no supervisor Windows.

Política proposta para futura implementação:

- retenção máxima de 30 dias corridos;
- arquivos fora do repositório Git;
- diretório de acesso restrito;
- rotação por tamanho e idade;
- limite total de armazenamento a ser definido e testado;
- proteção de evidências associadas a incidentes;
- proibição de segredos, tokens e credenciais;
- nenhuma dependência do processamento normal
  em relação ao sucesso do armazenamento de logs.

O prazo de 30 dias ainda não está operacionalmente
garantido e não deverá ser anunciado como implementado.

## 5. Diagnósticos locais Amazon

Destino identificado no código:

target/diagnostics/

A probe externa pode produzir HTML renderizado
e relatórios diagnósticos.

O HTML deve ser considerado potencialmente sensível,
pois pode conter dados de página, sessão ou contexto
de execução que não devem ser divulgados indiscriminadamente.

Resultado do inventário em 2026-10-15:

Pasta target/diagnostics ausente.

Política proposta:

- retenção máxima local de 7 dias corridos;
- arquivos fora do controle de versão;
- proteção contra acesso não autorizado;
- preservação quando associados a incidente aberto;
- descarte somente após validação do caminho e do tipo;
- proibição de exclusão recursiva genérica;
- confirmação de que não há evidência sob retenção especial.

Nenhum mecanismo automático local foi implementado.

## 6. Relatórios Maven

Destino:

target/surefire-reports/

Inventário observado:

- 708 arquivos;
- tamanho total aproximado de 3,78 MB.

Classificação:

Artefatos temporários de verificação e diagnóstico.

Política proposta:

- conservação operacional de até 7 dias;
- preservação de resultados necessários a incidentes;
- não utilizar relatórios temporários como único
  registro de um gate de release;
- registrar resultados consolidados na documentação;
- não confundir Maven clean com política de retenção.

O comando Maven clean pode remover esses arquivos
antes do prazo proposto.

Por isso, não existe garantia de conservação mínima
de 7 dias em target/.

O controle efetivo de retenção ainda deverá ser
implementado ou formalizado para artefatos exportados.

## 7. Diagnósticos remotos GitHub Actions

Workflow:

.github/workflows/amazon-source-probe.yml

Destino dos artefatos enviados:

target/diagnostics/

Configuração observada:

retention-days: 3

Essa é uma configuração existente do workflow.

Ela não representa prova de que um artefato
específico tenha sido produzido ou excluído.

Não determina a retenção dos arquivos locais.

## 8. Arquivos de supervisão Windows

Destino operacional identificado:

C:\dev\rasping-amazon-backups\supervisao

Inventário observado:

- diretório existente;
- um arquivo;
- tamanho total inferior a 0,01 MB.

O conteúdo e a finalidade específica desse arquivo
não foram inspecionados nesta etapa.

Arquivos de registro, reserva de partida e pedido
de parada podem possuir semântica de controle.

Regras:

- não aplicar TTL;
- não executar limpeza automática;
- não excluir arquivos por tamanho;
- não excluir arquivos por idade;
- preservar os mecanismos de validação existentes;
- utilizar somente procedimentos supervisionados
  e previamente autorizados para seu ciclo de vida.

O diretório de supervisão não é diretório de logs.

## 9. Backups

Backups estão fora do escopo de descarte desta política.

A definição de frequência, retenção, integridade,
proteção e restauração pertence à FASE 21-F.

Nenhum backup será apagado durante a FASE 21-E.

## 10. Preservação de evidências de incidentes

Um artefato associado a incidente aberto não poderá
ser descartado apenas porque atingiu um prazo.

Antes de implementar expiração deverão existir:

- identificação confiável do artefato;
- registro de sua classificação;
- mecanismo explícito de retenção especial;
- confirmação de encerramento do incidente;
- auditoria da decisão de descarte.

A ausência dessas informações deverá impedir
a exclusão automática.

## 11. Segurança da implementação futura

Uma rotina de retenção deverá:

- operar exclusivamente em diretórios permitidos;
- rejeitar caminhos relativos inseguros;
- rejeitar caminhos fora das raízes autorizadas;
- tratar links simbólicos e pontos de redirecionamento;
- evitar seguir links para fora do diretório;
- impedir exclusões durante investigação;
- executar inicialmente apenas em modo de simulação;
- produzir relatório sem revelar conteúdo sensível;
- possuir testes de caminhos, permissões e exceções;
- exigir autorização explícita para exclusão efetiva.

Não será utilizado apagamento recursivo genérico.

## 12. Restrições de operação

A política atual de startup permanece:

execution_mode = preparation_only

Não alterar:

- daemon_start_authorized;
- flyway_migrate_authorized;
- external_actions_authorized;
- automatic_restart_authorized.

Não iniciar daemon para testar retenção.

Não executar Flyway no banco operacional.

Não alterar migrations históricas.

## 13. Critérios pendentes para fechamento

1. Aprovar os prazos e limites operacionais propostos.
2. Projetar a captura persistente dos logs do daemon.
3. Definir limite de tamanho e rotação verificáveis.
4. Estabelecer caminhos externos seguros.
5. Implementar a proteção de evidências de incidentes.
6. Implementar testes automatizados do contrato.
7. Validar procedimentos em ambiente isolado.
8. Registrar os gates e resultados reais.
9. Consolidar a FASE 21-E em um commit único.

Até lá, nenhum prazo proposto poderá ser declarado
como descarte automático efetivamente implementado.
