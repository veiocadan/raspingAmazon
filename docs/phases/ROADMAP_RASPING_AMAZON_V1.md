# Rasping Amazon — Roadmap da versão 1.0

**Projeto:** Rasping Amazon  
**Escopo:** FASES 13 a 21  
**Objetivo:** conduzir o projeto do motor de decisão já existente até uma versão 1.0 utilizável, operável, publicadora e auditável.

---

## 1. Contexto

Este roadmap parte do estado consolidado após as FASES 0 a 11 e considera a FASE 12 como etapa imediatamente anterior.

Ao final da FASE 11, o projeto já possui:

- coleta e parsing de ofertas Amazon;
- enriquecimento de página individual;
- seller e delivery normalizados;
- condições comerciais;
- persistência PostgreSQL;
- migrations Flyway;
- transações e idempotência;
- elegibilidade estrutural Amazon;
- filtros comerciais versionados;
- score versionado;
- ranking determinístico;
- histórico por ASIN;
- evolução entre snapshots;
- MOMENTUM_V1;
- recorrência;
- detecção de publicação anterior;
- CI;
- testes herméticos;
- probe externa separada;
- rastreabilidade das decisões.

A FASE 12 deverá consolidar:

- orquestração por etapas;
- reprocessamento seguro;
- idempotência por etapa;
- separação entre falhas transitórias e permanentes;
- processamento assíncrono quando necessário;
- preparação para workers futuros.

A partir da FASE 13, o foco deixa de ser apenas construir o motor interno e passa a ser transformar esse motor em uma aplicação utilizável de ponta a ponta.

Fluxo alvo da versão 1.0:

```text
coleta
  ↓
parsing
  ↓
enriquecimento
  ↓
persistência
  ↓
elegibilidade
  ↓
filtros
  ↓
score
  ↓
ranking
  ↓
histórico / momentum
  ↓
seleção
  ↓
geração de publicação
  ↓
revisão
  ↓
aprovação
  ↓
fila / outbox
  ↓
canal
  ↓
publicação
  ↓
auditoria
```

---

# 2. FASE 13 — Geração de publicação e link de associado

## Objetivo

Transformar uma oferta selecionada pelo pipeline em uma `Publication` reproduzível, persistida e pronta para revisão.

A ordem original do projeto colocava interface operacional antes da geração de publicações.

Esta versão do roadmap inverte essa ordem.

A razão é arquitetural:

> A interface deve consumir casos de uso completos. Ela não deve ser o local onde regras de geração de publicação passam a existir.

Primeiro deve existir o caso de uso de publicação. Depois a interface será construída sobre ele.

## Responsabilidades

Criar ou consolidar:

```text
PublicationGenerator
PublicationTemplate
PublicationTemplateVersion
AffiliateLinkGenerator
Publication
```

Uma publicação deve preservar relação com os dados que a originaram.

Deve ser possível responder:

```text
qual produto originou a publicação?
qual OfferSnapshot foi utilizado?
qual DealEvaluation foi utilizada?
qual score existia?
qual condição comercial foi apresentada?
qual template foi utilizado?
qual versão do template foi utilizada?
qual link de associado foi gerado?
quando a publicação foi gerada?
```

## Templates

Templates devem ser separados dos dados da oferta.

Não deve existir formatação de mensagem espalhada pelo código.

Exemplo conceitual:

```text
Product
OfferSnapshot
DealEvaluation
PaymentConditions
PublicationTemplate
AffiliateLink
        ↓
PublicationGenerator
        ↓
Publication
```

Versão inicial sugerida:

```text
AMAZON_PUBLICATION_V1
```

Templates históricos não devem ser alterados retroativamente.

Mudanças futuras devem gerar nova versão.

## Condição comercial apresentada

A fase deverá fechar a política de apresentação das condições comerciais.

Ela deve ser separada das regras utilizadas para elegibilidade e filtros.

O sistema poderá decidir, com regras explícitas e testáveis:

```text
qual preço destacar
qual desconto destacar
quando mencionar Pix
quando mencionar NuPay
quando mencionar parcelamento
```

Dados não observados não devem ser inventados.

## Link de associado

A geração do link deve ficar encapsulada.

O restante da aplicação não deve concatenar manualmente parâmetros ou tags de afiliado.

Fluxo conceitual:

```text
ProductUrl
    ↓
AffiliateLinkGenerator
    ↓
AffiliateLink
```

Configurações necessárias permanecem fora do código.

## Fora do escopo

Ainda não entram:

```text
Telegram
WhatsApp
scheduler
envio automático
```

Geração e entrega de conteúdo permanecem responsabilidades diferentes.

## Critério de conclusão

Dada uma oferta selecionada:

```text
DealEvaluation
    ↓
PublicationGenerator
    ↓
Publication persistida
```

a publicação deve:

- conter os dados corretos;
- possuir link de associado;
- registrar versão do template;
- ser regenerável de forma previsível;
- permanecer sem dependência de canal.

---

# 3. FASE 14 — Interface operacional

## Objetivo

Permitir operar o Rasping Amazon sem acessar diretamente PostgreSQL, IDE, arquivos internos ou código Java.

## Princípio central

A interface deve apenas chamar casos de uso.

Fluxo esperado:

```text
interface
   ↓
application
   ↓
domain
   ↓
ports
   ↓
infrastructure
```

Não deve existir:

```text
interface
   ↓
SQL direto
```

nem:

```text
interface
   ↓
parser Amazon
```

## Funcionalidades mínimas

A interface deverá permitir visualizar ofertas com informações como:

```text
ASIN
produto
preço
desconto
rating
quantidade de avaliações
seller
delivery
score
momentum
data da coleta
recorrência
publicação anterior
estado atual
```

Também deve permitir:

- ordenar ofertas;
- filtrar ofertas;
- consultar histórico;
- consultar evidências;
- entender motivos de aprovação e rejeição;
- selecionar oferta;
- gerar publicação;
- revisar publicação;
- consultar estado da publicação.

## Explicabilidade

Uma oferta deve poder exibir algo equivalente a:

```text
SELLER_IS_AMAZON       PASS
DELIVERY_IS_AMAZON     PASS
MIN_CASH_DISCOUNT      PASS
MIN_RATING             PASS
MIN_REVIEW_COUNT       PASS

SCORE_V1               58.2500
MOMENTUM_V1             2.0000 p.p./hora
```

## Configuração

Filtros e score poderão ser visualizados e, quando permitido, configurados.

Mudanças devem preservar versionamento.

Exemplo correto:

```text
COMMERCIAL_FILTER_V1
        ↓
nova configuração
        ↓
COMMERCIAL_FILTER_V2
```

Não alterar silenciosamente versões históricas.

## Tecnologia

A escolha concreta entre interface web local, desktop ou outra solução Java deve ser tomada no início da fase.

Essa decisão não deve modificar os casos de uso internos.

## Fora do escopo

Não são requisitos da fase:

```text
dashboard analítico sofisticado
design visual avançado
aplicativo móvel
acesso remoto pela internet
```

## Critério de conclusão

Uma pessoa deve conseguir:

```text
abrir o sistema
→ consultar ofertas
→ entender decisões
→ visualizar ranking
→ visualizar histórico
→ selecionar oferta
→ gerar publicação
→ revisar publicação
```

sem usar IDE ou acessar o banco manualmente.

---

# 4. FASE 15 — Qualidade integrada e regressão de sistema

## Objetivo

Validar o Rasping Amazon como sistema completo e não apenas como conjunto de componentes isolados.

O CI e uma suíte significativa de testes já existem.

Esta fase deve focar nas jornadas completas.

## Fluxo principal a validar

```text
fixture Deals
      ↓
coleta
      ↓
parser
      ↓
enriquecimento
      ↓
persistência
      ↓
elegibilidade
      ↓
filtros
      ↓
score
      ↓
ranking
      ↓
histórico / momentum
      ↓
seleção
      ↓
PublicationGenerator
      ↓
Publication
```

Sem acessar a Amazon real.

## Cobertura crítica

Adicionar ou consolidar testes de:

- processamento repetido;
- idempotência;
- concorrência;
- rollback;
- reprocessamento por etapa;
- dados ausentes;
- mudanças de estado;
- regeneração de publicação;
- migrations desde banco vazio;
- upgrade sequencial de migrations;
- adapters externos;
- falhas de parser;
- falhas de HTTP;
- falhas de persistência;
- fluxo vertical completo.

## Fixtures

As fixtures permanecem pequenas e semânticas.

Capturas integrais da Amazon não devem voltar a ser dependência da suíte padrão.

## Fonte real

A probe externa deve permanecer separada da suíte hermética.

```text
mvn test
→ hermético

external probe
→ acesso real
```

## Critério de conclusão

O CI deve provar automaticamente que o fluxo crítico continua funcional após alterações relevantes.

Regressões no parser, persistência, avaliação, publicação ou idempotência devem ser detectáveis antes da integração na `main`.

---

# 5. FASE 16 — Observabilidade e auditoria operacional

## Objetivo

Permitir responder o que aconteceu durante uma execução sem reproduzir manualmente o problema.

A auditabilidade de negócio já existe.

Esta fase adiciona auditabilidade operacional.

## Correlação

Uma execução deve possuir identidade.

Conceitos esperados:

```text
runId
jobId
asin
snapshotId
evaluationId
publicationId
```

Os identificadores devem permitir acompanhar uma oferta através do pipeline.

## Logs estruturados

Logs devem carregar contexto suficiente.

Evitar mensagens isoladas como:

```text
Erro ao processar produto
```

Preferir registros com:

```text
runId
ASIN
etapa
adapter
tipo da falha
tentativa
timestamp
```

Segredos nunca devem aparecer nos logs.

## Métricas

Devem existir informações para responder:

```text
quantas ofertas foram encontradas?
quantas foram parseadas?
quantas falharam no enriquecimento?
quantas foram rejeitadas?
quantas passaram?
quantas receberam score?
quantas viraram publicação?
quantas foram publicadas?
quanto tempo cada etapa levou?
```

## Alertas

A estrutura deve possibilitar detectar:

```text
parser deixou de encontrar ofertas
seller UNKNOWN aumentou
fonte começou a falhar
nenhuma oferta foi coletada
fila parou de avançar
publicações começaram a falhar
```

Não é obrigatório introduzir plataforma pesada de observabilidade.

Primeiro devem existir dados confiáveis.

## Critério de conclusão

Dado um `runId`, deve ser possível reconstruir:

```text
o que iniciou
o que terminou
o que falhou
em qual etapa
para qual oferta
com qual resultado
```

---

# 6. FASE 17 — Agendamento e execução contínua

## Objetivo

Transformar a aplicação em um sistema capaz de executar ciclos automaticamente.

O scheduler entra apenas depois que o processamento manual e orquestrado estiver estável.

## Scheduler

Fluxo conceitual:

```text
scheduler
   ↓
ProcessingRun
   ↓
orquestração da FASE 12
```

O scheduler não deve conter regras de coleta, parser ou avaliação.

## Frequência

A frequência deve ser configurável.

Não deve ficar hardcoded.

## Execuções concorrentes

Deve existir prevenção contra execuções sobrepostas indesejadas.

O PostgreSQL pode ser utilizado para lock/lease enquanto atender às necessidades reais do sistema.

## Pausa operacional

Deve ser possível suspender novas execuções sem apagar histórico ou corromper trabalhos existentes.

## Fonte Amazon

A execução recorrente deve tratar respostas como:

```text
429
403
challenge
CAPTCHA
bloqueio
timeout
```

como falhas de fonte ou restrições operacionais.

Não serão implementados mecanismos destinados a contornar proteções.

## API futura

A versão 1.0 não depende da Amazon Creators API.

A arquitetura deve continuar aceitando futuramente:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

ou combinação das duas.

## Critério de conclusão

O sistema deve executar ciclos recorrentes sem:

```text
duplicar observações indevidamente
sobrepor execuções de forma incorreta
perder rastreabilidade
exigir intervenção em ciclos normais
```

---

# 7. FASE 18 — Contrato de canais e outbox de publicação

## Objetivo

Preparar a publicação externa sem acoplar o sistema ao Telegram ou WhatsApp.

## Contrato de canal

Criar conceito equivalente a:

```text
PublicationChannel
```

Responsabilidades esperadas:

```text
validar destino
publicar
retornar resultado
classificar erro
```

Adapters futuros:

```text
PublicationChannel
      ↑
      ├── TelegramChannel
      └── WhatsAppChannel
```

## Comando de publicação

A entrega externa deve receber um contrato específico, por exemplo:

```text
PublicationCommand
```

O canal não deve consultar diretamente `OfferSnapshot`, `DealEvaluation` ou repositories internos para montar mensagem.

A publicação já chega pronta.

## Resultado

Definir resultado estruturado.

Exemplo:

```text
SUCCESS
FAILED_TRANSIENT
FAILED_PERMANENT
```

Também registrar referência do provedor quando disponível.

## Outbox

A geração da publicação e o envio externo não devem depender da mesma transação externa.

Modelo esperado:

```text
Publication READY
        ↓
Outbox
        ↓
worker
        ↓
PublicationChannel
```

O PostgreSQL continua sendo a primeira opção para essa persistência enquanto atender ao volume real.

## Idempotência

Uma mesma publicação não deve ser enviada duas vezes por simples reinício de worker.

A identidade deverá considerar, no mínimo:

```text
publication
channel
destination
```

## Aprovação manual

O sistema deve suportar:

```text
gerar
→ revisar
→ aprovar
→ enviar
```

Mesmo que futuramente alguns fluxos permitam aprovação automática.

## Critério de conclusão

Deve existir um adapter fake/de teste capaz de validar:

```text
Publication READY
→ outbox
→ worker
→ PublicationChannel
→ resultado persistido
```

sem utilizar Telegram ou WhatsApp reais.

---

# 8. FASE 19 — Telegram e WhatsApp

## Objetivo

Implementar os primeiros adapters concretos de canal.

A lógica de negócio continua independente dos provedores.

## Telegram

Implementar:

```text
TelegramChannel
```

Responsabilidades:

- validar configuração;
- enviar publicação;
- interpretar resposta;
- registrar referência retornada;
- classificar falhas.

## WhatsApp

Implementar:

```text
WhatsAppChannel
```

Utilizando método oficial ou compatível com as regras do serviço escolhido.

Credenciais e tokens permanecem externos ao código.

## Destinos

Destinos devem ser configuráveis.

Exemplo conceitual:

```text
Canal: TELEGRAM
Destino: ofertas-principais

Canal: WHATSAPP
Destino: grupo-X
```

Não espalhar identificadores hardcoded.

## Tentativas

Cada tentativa deverá registrar, quando aplicável:

```text
publication
channel
destination
attempt
status
providerReference
startedAt
finishedAt
errorCode
```

## Retry

Falhas transitórias podem ser repetidas.

Falhas permanentes não entram em loop infinito.

## Formatação por canal

Cada adapter pode adaptar o conteúdo às regras do canal.

Exemplo:

```text
Telegram
→ Markdown/HTML permitido

WhatsApp
→ formatação suportada pelo provedor
```

A transformação de canal não altera os fatos da oferta.

## Critério de conclusão

Fluxo esperado:

```text
Publication
   ↓
outbox
   ↓
worker
   ↓
Telegram ou WhatsApp
   ↓
PublicationAttempt
   ↓
PUBLISHED ou FAILED
```

Com rastreabilidade e idempotência.

---

# 9. FASE 20 — Resiliência, recuperação e falhas de produção

## Objetivo

Garantir que falhas externas, reinicializações e erros operacionais não deixem o sistema em estado inconsistente.

## Taxonomia de falhas

Definir categorias suficientes para orientar comportamento.

Exemplo:

```text
TRANSIENT
PERMANENT
SOURCE_CHANGED
AUTHENTICATION
RATE_LIMIT
DATA_UNAVAILABLE
DATABASE
CHANNEL
CONFIGURATION
```

Cada categoria deve permitir decidir entre:

```text
retry
rejeitar
pausar
alertar
reprocessar
intervenção humana
```

## Amazon

Casos relevantes:

```text
timeout
erro HTTP
rate limit
layout alterado
campo crítico desapareceu
CAPTCHA/challenge
produto removido
seller ausente
delivery ausente
```

Mudanças de layout não devem gerar dados falsos silenciosamente.

Quando faltar evidência, preservar o comportamento fail closed.

## Canais

Tratar:

```text
timeout
rate limit
token inválido
destino inexistente
mensagem rejeitada
resposta desconhecida
```

## Reinicialização

O sistema deve conseguir reiniciar e descobrir trabalho pendente.

Estado operacional não pode depender apenas de memória.

PostgreSQL continua sendo a fonte de verdade.

## Dead-letter / reprocessamento

Falhas que excederem sua política de retry devem continuar visíveis e reprocessáveis.

Fila externa não é obrigatória para a versão 1.0.

## Circuit breaker

Só introduzir se houver necessidade operacional demonstrada.

Não implementar apenas por padrão arquitetural.

## Critério de conclusão

Deve ser possível provocar falhas controladas em:

```text
fonte Amazon
PostgreSQL
processamento
canal
```

e comprovar que o sistema termina em estado conhecido, rastreável e recuperável.

---

# 10. FASE 21 — Segurança, governança e fechamento da versão 1.0

## Objetivo

Fechar a primeira versão utilizável do Rasping Amazon.

Esta fase não adiciona novo motor de negócio.

Ela transforma o sistema existente em um release administrável, reproduzível e seguro para o escopo definido.

## Segredos

Todos os segredos permanecem fora do repositório.

Incluindo:

```text
DB_PASSWORD
credenciais Amazon futuras
Telegram token
WhatsApp credentials
demais tokens externos
```

Logs não devem expor segredos.

## Privilégios

Banco e integrações devem operar com privilégios mínimos necessários.

## Interface

Se a interface da v1.0 for exclusivamente local, isso deve ser documentado explicitamente.

Ela não deve ser exposta indiscriminadamente à rede por padrão.

Caso passe a ser acessível remotamente antes da v1.0, autenticação se torna obrigatória.

## Auditoria de configuração

Mudanças relevantes devem permanecer rastreáveis.

Especialmente:

```text
FilterProfile
ScoreProfile
PublicationTemplate
destinos
configurações de publicação
```

## Retenção

Definir política inicial para:

```text
snapshots
evaluations
logs
processing runs
publication attempts
diagnostics
```

Sem exclusões agressivas sem necessidade.

## Backup e restauração

PostgreSQL é a fonte principal de estado.

A v1.0 precisa possuir procedimento documentado de:

```text
backup
restore
```

Também deve existir ao menos uma validação prática de restauração.

## Empacotamento e operação

Documentar procedimento reproduzível para:

```text
compilar
configurar
subir PostgreSQL
executar migrations
iniciar aplicação
parar aplicação
atualizar aplicação
```

O Maven Wrapper continua sendo a referência do build.

## Documentação

Estrutura recomendada:

```text
docs/
├── architecture/
├── adr/
├── phases/
├── research/
└── roadmap.md
```

O README deve representar o estado operacional atual.

## Gate funcional da versão 1.0

Antes da tag final, executar o fluxo completo:

```text
Amazon
  ↓
coleta
  ↓
parser
  ↓
enriquecimento
  ↓
PostgreSQL
  ↓
elegibilidade
  ↓
filtros
  ↓
score
  ↓
ranking
  ↓
histórico
  ↓
momentum
  ↓
seleção
  ↓
PublicationGenerator
  ↓
revisão
  ↓
aprovação
  ↓
outbox
  ↓
canal
  ↓
PUBLISHED
  ↓
auditoria
```

## Critério de conclusão

A versão 1.0 estará pronta quando for possível:

```text
instalar/configurar
→ iniciar
→ coletar ofertas automaticamente
→ avaliar
→ ranquear
→ acompanhar histórico
→ selecionar/revisar
→ gerar publicação
→ publicar em canais configurados
→ evitar duplicações
→ diagnosticar falhas
→ recuperar processamento
→ preservar histórico
```

sem depender da IDE, de Excel ou de manipulação manual do banco.

Após gate local e remoto:

```text
v1.0.0
```

---

# 11. Itens removidos do roadmap da versão 1.0

## Excel / CSV

Excel deixa de possuir fase própria.

Não participa:

```text
coleta
estado
filtros
score
seleção
publicação
operação
```

Caso exista necessidade futura, exportação CSV/Excel poderá ser tratada como funcionalidade auxiliar.

Nunca como banco ou estado operacional.

## Escalabilidade pesada

A antiga fase de escalabilidade deixa de ser condição para a v1.0.

Princípio:

```text
primeiro medir
depois otimizar
```

Não antecipar sem necessidade comprovada:

```text
Kafka
RabbitMQ
microservices
read replicas
particionamento
cache distribuído
clusters
filas dedicadas
separação em múltiplos serviços
```

A arquitetura deve continuar preparada para evoluir, mas não implementar escala hipotética.

---

# 12. Itens explicitamente pós-v1.0

## Amazon Creators API

A v1.0 não depende da API.

A fonte operacional atual permanece baseada em HTML.

A arquitetura deve aceitar futuramente:

```text
AmazonHtmlSource
AmazonCreatorsApiSource
```

ou combinação entre elas.

A API deve entrar como novo adapter, sem contaminar o domínio.

## Mercado Livre

Integração com Mercado Livre fica para versão posterior.

Ela deve entrar como novo marketplace/fonte, sem espalhar condicionais Amazon/Mercado Livre pelo domínio existente.

Antes de abstrair marketplace, a implementação do segundo marketplace deve mostrar quais conceitos são realmente comuns.

## Raspberry Pi / servidor remoto

Hospedagem contínua em Raspberry Pi ou outro servidor fica para o pós-v1.0.

Primeiro comprovar localmente:

```text
aplicação funcional
execução contínua
scheduler
recuperação
logs
canais
backup
```

Depois tratar:

```text
Linux service
containers de produção
reverse proxy
TLS
firewall
deploy
restart automático
monitoramento da máquina
backup remoto
```

## Escala guiada por métricas

Após a v1.0, usar métricas reais como:

```text
tempo de processamento
volume de ofertas
CPU
memória
latência PostgreSQL
crescimento de snapshots
tempo de fila
retries
limites de provedores
```

para decidir se são necessários:

```text
batch
pool maior
cache
particionamento
mais workers
fila externa
réplica
multi-módulo
serviços independentes
```

---

# 13. Macrovisão final

```text
FASES 0–8
fundação + aquisição + validação
        ↓
FASE 8.5
consolidação estrutural
        ↓
FASES 9–11
motor de decisão
        ↓
FASE 12
orquestração
        ↓
FASE 13
geração de publicação
        ↓
FASE 14
interface operacional
        ↓
FASE 15
qualidade integrada
        ↓
FASE 16
observabilidade
        ↓
FASE 17
execução contínua
        ↓
FASE 18
contrato de canais
        ↓
FASE 19
Telegram + WhatsApp
        ↓
FASE 20
resiliência e recuperação
        ↓
FASE 21
segurança + governança + release
        ↓
v1.0.0
```

---

# 14. Definição prática da versão 1.0

A versão 1.0 do Rasping Amazon estará atingida quando o sistema conseguir:

> encontrar ofertas, tomar decisões reproduzíveis, permitir revisão humana, gerar conteúdo de afiliado, publicar, repetir o processo continuamente e explicar o que aconteceu quando algo falha.

Nesse ponto, o projeto deixa de ser apenas um pipeline em desenvolvimento e passa a constituir uma aplicação utilizável.

Expansões horizontais como:

```text
novos marketplaces
infraestrutura remota
Raspberry Pi
Amazon Creators API
escala distribuída
integrações auxiliares
```

passam a ser tratadas como evolução pós-v1.0.

---

# 15. Regra de manutenção deste roadmap

Este documento passa a ser a referência operacional para as FASES 13 a 21.

Os relatórios históricos das fases anteriores devem permanecer preservados.

Quando uma fase for concluída:

1. seu relatório de resultado deve registrar o que realmente foi implementado;
2. este roadmap só deve ser alterado quando houver mudança deliberada de planejamento;
3. decisões arquiteturais permanentes devem ser registradas em ADR quando apropriado;
4. diferenças entre planejamento e execução devem ser explícitas;
5. não reescrever retrospectivamente relatórios históricos para fazê-los coincidir com decisões posteriores.

O roadmap representa:

```text
para onde o projeto está indo
```

Os relatórios de fase representam:

```text
o que efetivamente aconteceu
```
