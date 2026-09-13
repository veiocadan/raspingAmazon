# Rasping Amazon — FASE 1
## Fundação Java e Estrutura Inicial do Projeto

**Status:** CONCLUÍDA — resultado técnico documentado  
**Data:** 13/09/2026  
**Projeto:** Rasping Amazon

---

## 1. Objetivo da FASE 1

A FASE 1 teve como objetivo estabelecer a fundação técnica do projeto em Java, garantindo que:

- o projeto pudesse ser compilado pelo Maven;
- testes automatizados pudessem ser executados;
- a estrutura inicial de pacotes da arquitetura estivesse definida;
- a documentação inicial do projeto estivesse criada;
- fosse possível gerar o artefato JAR;
- a base permanecesse preparada para as fases posteriores sem antecipar persistência, coleta de dados ou regras de negócio.

A FASE 1 foi executada respeitando a orientação de trabalhar uma fase por vez.

---

# 2. Estado inicial

No início da FASE 1, o projeto já possuía a estrutura Maven básica:

```text
C:\Devasping-amazon
├── pom.xml
├── src
└── infra
```

Também foram verificadas as ferramentas do ambiente de desenvolvimento.

### Ambiente identificado

- Sistema operacional: Windows
- Java: 25.0.4 LTS, 64-bit
- `JAVA_HOME` configurado para o JDK 25.0.4.1
- Maven: 3.9.16
- Git: repositório inicializado
- Diretório do projeto: `C:\Dev\rasping-amazon`

No início, os diretórios Java existiam, mas ainda não continham classes de aplicação ou testes.

---

# 3. Estrutura arquitetural criada

Foi estabelecida a estrutura inicial de pacotes:

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── raspingamazon/
│   │           ├── application/
│   │           ├── domain/
│   │           ├── infrastructure/
│   │           └── presentation/
│   │
│   └── resources/
│
└── test/
    └── java/
        └── com/
            └── raspingamazon/
```

A separação segue a arquitetura definida para o projeto:

- `presentation` — entrada/interface;
- `application` — orquestração dos casos de uso;
- `domain` — regras e conceitos centrais do negócio;
- `infrastructure` — detalhes técnicos e integrações externas.

Nesta fase, foram criados os espaços arquiteturais, mas **não foram implementadas regras de negócio ou integrações**.

Isso foi intencional: a implementação de domínio, persistência, coleta Amazon, publicação e demais componentes pertence às fases seguintes.

---

# 4. Configuração do Maven

O `pom.xml` foi estruturado para tornar explícita a versão do Java e as ferramentas de compilação e testes.

Foram configurados:

- Java release 25;
- codificação UTF-8;
- JUnit 5;
- Maven Compiler Plugin;
- Maven Surefire Plugin;
- Maven JAR Plugin.

As versões de plugins utilizadas no build foram:

- Maven Compiler Plugin `3.15.0`;
- Maven Surefire Plugin `3.5.4`;
- Maven JAR Plugin `3.5.0`.

O projeto permanece como um artefato Maven do tipo `jar`.

---

# 5. Teste automatizado da fundação

Foi criado o teste:

```text
src/test/java/com/raspingamazon/FoundationTest.java
```

Conteúdo:

```java
package com.raspingamazon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationTest {

    @Test
    void applicationFoundationIsOperational() {
        assertTrue(true);
    }
}
```

## Objetivo do teste

O teste não representa uma regra de negócio.

Sua finalidade é verificar a cadeia mínima:

```text
Código Java
    ↓
Maven Compiler
    ↓
JUnit 5
    ↓
Maven Surefire
    ↓
Execução do teste
```

Assim, ele funciona como um teste de fumaça da fundação do projeto.

---

# 6. Teste `mvn clean test`

Foi executado:

```powershell
mvn clean test
```

Resultado:

```text
Running com.raspingamazon.FoundationTest

Tests run: 1,
Failures: 0,
Errors: 0,
Skipped: 0
```

Resultado final:

```text
BUILD SUCCESS
```

### Conclusão

O projeto conseguiu:

1. limpar o diretório `target`;
2. processar os recursos;
3. compilar os testes com Java release 25;
4. detectar o provedor JUnit Platform;
5. executar `FoundationTest`;
6. finalizar sem falhas.

Portanto, a infraestrutura mínima de testes está operacional.

---

# 7. Empacotamento com Maven

Depois do teste, foi executado:

```powershell
mvn package
```

O Maven novamente executou o teste:

```text
Running com.raspingamazon.FoundationTest

Tests run: 1,
Failures: 0,
Errors: 0,
Skipped: 0
```

Em seguida, o JAR foi criado:

```text
C:\Devasping-amazon	argetasping-amazon-1.0-SNAPSHOT.jar
```

Resultado:

```text
BUILD SUCCESS
```

### Conclusão

Além de testar, o projeto consegue ser empacotado como artefato Java.

---

# 8. Descobertas durante a FASE 1

## 8.1. O ambiente Java está funcional

O Java 25 está instalado e configurado corretamente para o projeto.

O Maven conseguiu utilizar:

```text
javac [debug release 25]
```

sem erro.

---

## 8.2. O Maven está executando os testes corretamente

O Surefire detectou automaticamente:

```text
org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
```

Isso confirmou a integração entre Maven, Surefire e JUnit 5.

---

## 8.3. A fundação compila mesmo sem implementação de negócio

A existência das camadas arquiteturais não exige que elas já contenham classes de negócio.

Isso permitiu estabelecer a estrutura sem antecipar as fases seguintes.

---

## 8.4. A geração do JAR está funcional

O projeto já produz:

```text
rasping-amazon-1.0-SNAPSHOT.jar
```

Isso fornece um artefato empacotável para as próximas etapas.

---

## 8.5. O Git está inicializado, mas ainda não há commit inicial

A inspeção do estado do Git mostrou arquivos adicionados ou modificados, incluindo:

```text
.gitignore
.idea/
infra/compose.yml
README.md
pom.xml
src/
```

O repositório ainda não possuía commit inicial durante esta fase.

Nenhuma operação destrutiva de Git foi realizada.

---

# 9. README do projeto

Também foi criado o:

```text
README.md
```

O README passa a servir como documentação inicial do projeto e registra a finalidade e a organização da solução.

A documentação acompanha a evolução por fases, evitando que decisões arquiteturais fiquem apenas no código ou no histórico da conversa.

---

# 10. O que NÃO foi implementado nesta fase

Para preservar a separação entre fases, os seguintes itens **não foram implementados como parte da FASE 1**:

- PostgreSQL;
- migrations;
- schema de banco;
- entidades de persistência;
- JPA/Hibernate;
- coleta da Amazon;
- parser;
- extração de ASIN;
- normalização de produtos;
- validação de vendedor;
- validação de entrega;
- filtros;
- cálculo de score;
- histórico;
- scheduler;
- WhatsApp;
- Telegram;
- publicação de ofertas.

Esses itens pertencem às fases posteriores do projeto.

---

# 11. Relação com a FASE 0

A FASE 0 estabeleceu as descobertas e decisões técnicas sobre a fonte Amazon, incluindo:

- página de ofertas;
- endpoint técnico observado;
- identificação por ASIN;
- campos necessários;
- percentual vendido;
- validação de vendedor e entrega;
- paginação;
- riscos e fallbacks.

A FASE 1 não alterou essas decisões.

Ela criou a fundação Java necessária para que essas decisões possam ser implementadas posteriormente de forma modular.

A fonte oficial do projeto também estabelece que a próxima etapa depois da fundação Java é a FASE 2, dedicada a PostgreSQL, schema e migrations.

---

# 12. Critérios de conclusão

| Critério | Resultado |
|---|---|
| Projeto Maven configurado | CONCLUÍDO |
| Java 25 configurado | CONCLUÍDO |
| Estrutura arquitetural inicial | CONCLUÍDO |
| JUnit configurado | CONCLUÍDO |
| Teste automatizado criado | CONCLUÍDO |
| `mvn clean test` | PASSOU |
| Testes executados | 1 |
| Falhas | 0 |
| Erros | 0 |
| `mvn package` | PASSOU |
| JAR gerado | CONCLUÍDO |
| README criado | CONCLUÍDO |

---

# 13. Resultado final

**FASE 1 — CONCLUÍDA.**

A fundação Java do Rasping Amazon está operacional.

O projeto atualmente possui:

```text
Java 25
   ↓
Maven
   ↓
Estrutura arquitetural
   ↓
JUnit 5
   ↓
Testes automatizados
   ↓
Build
   ↓
JAR
```

Os testes confirmaram que a base compila, os testes são descobertos e executados e o projeto pode ser empacotado.

---

# 14. Próxima fase

A próxima etapa é:

## FASE 2 — PostgreSQL, Schema e Migrations

A FASE 2 deverá tratar a persistência do projeto de forma separada da fundação Java.

Antes de implementar o schema definitivo, deverá ser verificado o estado atual do PostgreSQL local, especialmente porque existe um `infra/compose.yml` no projeto e houve anteriormente um estado de reinicialização do container.

A partir daí serão tratados, de forma controlada:

1. PostgreSQL;
2. conexão local;
3. banco `rasping_amazon`;
4. schema;
5. migrations;
6. estrutura inicial de persistência;
7. testes de conectividade e integridade.

**A FASE 2 somente deve começar após a confirmação do estado do ambiente PostgreSQL.**

---

## 15. Registro de encerramento

**Data:** 13/09/2026  
**Fase:** 1  
**Status:** CONCLUÍDA  
**Build:** OK  
**Testes:** OK  
**Artefato JAR:** GERADO  
**Próxima fase:** FASE 2 — PostgreSQL, Schema e Migrations
