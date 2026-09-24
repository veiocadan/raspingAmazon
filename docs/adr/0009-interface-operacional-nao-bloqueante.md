# ADR-0009 — Interface operacional não bloqueante

* **Status:** Aceita
* **Data:** 2026-09-24
* **Projeto:** Rasping Amazon
* **Fase relacionada:** FASE 14
* **Complementa:** ADR-0004

## Contexto

Ao final da FASE 13, o Rasping Amazon possui um pipeline capaz de produzir uma `Publication` persistida, reproduzível e auditável a partir de uma `DealEvaluation` já processada.

O objetivo estratégico do projeto permanece sendo a operação automatizada.

A seleção de ofertas, priorização por score, geração da publicação e posterior entrega aos canais não devem depender da intervenção de um operador para prosseguir.

A existência de uma interface administrativa não pode transformar o operador em parte obrigatória do caminho crítico do pipeline.

A interface da FASE 14 existe para observabilidade operacional, consulta histórica, diagnóstico e execução de comandos administrativos explícitos.

Ela não existe para aprovar individualmente as decisões comerciais tomadas automaticamente pelo pipeline.

## Decisão

### 1. A interface operacional não fará parte do caminho crítico de publicação

O pipeline deverá continuar funcionando mesmo quando nenhuma interface operacional estiver em execução.

Fluxo conceitual:

```text
coleta
  ↓
parsing
  ↓
enriquecimento
  ↓
filtros
  ↓
score
  ↓
seleção automática
  ↓
geração de Publication
  ↓
despacho futuro
  ↓
canal
```

A interface operacional observará e administrará esse fluxo externamente.

```text
                     ┌──────────────────────┐
                     │ Interface operacional│
                     │ consulta / controle  │
                     └──────────┬───────────┘
                                │
                                ▼
coleta → avaliação → publicação → entrega
```

Nenhuma etapa automática deverá aguardar interação humana da interface para continuar.

### 2. Aprovação humana não será requisito para publicação

A geração ou liberação de uma publicação não deverá exigir clique, confirmação ou aprovação manual de um operador.

A presença de estados como `CREATED`, `READY`, `PUBLISHED` e `FAILED` representa estados do ciclo de vida da publicação, não necessariamente estados de aprovação humana.

A semântica operacional adotada será:

```text
CREATED
Publication gerada e persistida.

READY
Publication liberada pelas regras automáticas aplicáveis
para prosseguir para o mecanismo de despacho.

PUBLISHED
Entrega confirmada pelo mecanismo responsável pelo canal.

FAILED
Falha ocorrida em etapa posterior que permita
tratamento ou nova tentativa conforme regras próprias.
```

A transição de `CREATED` para `READY` poderá ser realizada automaticamente por um caso de uso do sistema.

A interface operacional poderá exibir esses estados, mas não deverá ser necessária para a transição normal do pipeline.

### 3. A interface será uma camada de observação e administração

A FASE 14 deverá permitir progressivamente consultas como:

* ofertas processadas;
* avaliações persistidas;
* score e seus componentes;
* momentum;
* motivos de elegibilidade ou rejeição;
* histórico de produtos e ofertas;
* execuções do pipeline;
* jobs e estados operacionais;
* erros persistidos;
* publicações geradas;
* estados das publicações;
* configurações ativas que possuam representação persistida apropriada.

A interface também poderá disponibilizar comandos administrativos quando existir um caso de uso explícito para isso.

Exemplos:

* iniciar uma execução manual;
* solicitar nova tentativa de um job elegível;
* consultar diagnóstico de uma execução;
* no futuro, pausar ou retomar a automação.

Esses comandos não deverão reimplementar regras de domínio.

### 4. A primeira interface será CLI

A primeira apresentação operacional será implementada como uma interface de linha de comando Java.

A escolha evita introduzir prematuramente um framework HTTP ou uma aplicação web quando os requisitos atuais são essencialmente operacionais.

A CLI será apenas um adaptador da camada de apresentação.

Fluxo:

```text
operador
   ↓
CLI
   ↓
caso de uso / query da application
   ↓
ports
   ↓
infrastructure
   ↓
PostgreSQL ou demais recursos
```

A CLI não poderá acessar JDBC diretamente.

A CLI não poderá consultar tabelas diretamente.

A CLI não poderá acessar páginas da Amazon.

A CLI não poderá conhecer HTML da Amazon.

A CLI não poderá reproduzir regras de filtros, score, momentum ou publicação.

### 5. Casos de consulta operacional serão explícitos

Consultas destinadas à interface deverão utilizar contratos próprios na camada `application`.

Esses contratos poderão utilizar read models específicos para operação.

Não será necessário reconstruir agregados completos do domínio quando a necessidade for somente de leitura administrativa.

Isso permite:

* paginação;
* filtros;
* ordenação;
* projeções específicas;
* consultas eficientes;
* evolução futura para outras interfaces.

A camada de apresentação deverá depender desses contratos, e não de implementações JDBC.

### 6. A interface não recalculará decisões já persistidas

A interface deverá exibir o resultado persistido das decisões do pipeline.

Ela não deverá recalcular:

* elegibilidade;
* filtros comerciais;
* score;
* ranking;
* momentum;
* política de apresentação comercial;
* template de publicação;
* link de associado.

Quando o operador consultar uma avaliação histórica, o sistema deverá apresentar os dados correspondentes à avaliação persistida.

### 7. Geração de publicação permanece caso de uso da aplicação

Quando necessário diagnosticar ou executar explicitamente uma geração já suportada pela aplicação, a interface deverá invocar o caso de uso existente.

A interface não deverá montar uma `Publication` diretamente.

A interface não deverá aplicar template diretamente.

A interface não deverá gerar link de associado diretamente.

A interface não deverá persistir publicação diretamente.

### 8. Pausa e retomada não serão simuladas nesta fase

O conceito de pausar ou retomar a automação é desejável para a operação futura.

Entretanto, a implementação real depende da existência do mecanismo responsável pelo agendamento contínuo.

A FASE 14 não deverá criar uma falsa implementação de pausa baseada somente em estado de interface.

Quando o scheduler for implementado em sua fase específica, o controle correspondente deverá ser exposto por meio de um caso de uso ou porta apropriada e poderá então ser consumido pela CLI.

### 9. Retry somente poderá ocorrer quando houver semântica definida

A interface poderá solicitar retry somente para processos cujo domínio ou mecanismo de orquestração já defina:

* quais estados aceitam retry;
* idempotência;
* concorrência;
* ownership;
* quantidade ou política de tentativas.

A CLI não deverá transformar qualquer falha em nova execução arbitrária.

### 10. Interface web permanece uma possibilidade futura

A implementação em CLI não impede uma futura interface web.

Uma eventual interface HTTP deverá consumir os mesmos contratos da camada `application`.

Assim:

```text
application
   ↑
   ├── presentation/cli
   └── presentation/web
```

A adição futura de uma interface web não deverá exigir reescrita das regras de negócio ou das consultas operacionais centrais.

## Consequências positivas

* o projeto preserva sua natureza automatizada;
* indisponibilidade da interface não interrompe o pipeline;
* não existe gargalo humano para publicação;
* a interface permanece simples e substituível;
* consultas operacionais podem ser otimizadas independentemente dos comandos;
* uma interface web poderá ser adicionada posteriormente;
* regras de negócio continuam fora da apresentação;
* o sistema permanece compatível com execução não assistida.

## Consequências e cuidados

* consultas administrativas precisarão de read models próprios;
* comandos operacionais deverão possuir casos de uso explícitos;
* ações destrutivas ou de controle deverão ser introduzidas somente quando sua semântica estiver definida;
* uma futura exposição remota da interface exigirá autenticação e autorização adequadas;
* a CLI não deverá se transformar em um acesso direto ao banco de dados.

## Fora do escopo da FASE 14

Não pertencem a esta fase:

* aprovação humana obrigatória de ofertas;
* aprovação humana obrigatória de publicações;
* Telegram;
* WhatsApp;
* outros canais de publicação;
* outbox de entrega para canais;
* retry específico de canais;
* scheduler definitivo;
* autenticação completa para uma futura interface web;
* alteração das fórmulas de score;
* alteração das regras de filtro;
* alteração da política de momentum;
* alteração da apresentação comercial;
* alteração dos templates de publicação;
* alteração da geração do link de associado.

## Critérios de aceite

A FASE 14 somente será considerada arquiteturalmente correta quando:

1. o pipeline puder funcionar sem a interface;
2. nenhuma publicação normal depender de aprovação manual;
3. a interface acessar funcionalidades através da camada `application`;
4. não houver JDBC na camada de apresentação;
5. não houver regra comercial na camada de apresentação;
6. consultas operacionais utilizarem contratos próprios;
7. a implementação inicial puder operar integralmente por CLI;
8. a futura substituição ou complementação da CLI por uma interface web não exigir alteração das regras do domínio;
9. comandos administrativos respeitarem idempotência e regras já existentes;
10. estados de `Publication` forem tratados como estados do fluxo automatizado e não como etapas obrigatórias de aprovação humana.

## Resultado esperado

A FASE 14 adiciona capacidade de observação e administração ao Rasping Amazon sem transformar o operador em participante obrigatório da automação.

O princípio final é:

```text
A interface observa e administra o pipeline.
A interface não autoriza o pipeline a funcionar.
```
