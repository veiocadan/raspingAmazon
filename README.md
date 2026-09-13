# Rasping Amazon

Automação de ofertas Amazon para publicação em grupos de promoções.

## Estado do projeto

- FASE 0 — CONCLUÍDA
- FASE 1 — EM EXECUÇÃO

## Arquitetura

O projeto utiliza Java como motor principal e PostgreSQL como persistência
principal nas fases posteriores.

A arquitetura separa:

- presentation
- application
- domain
- infrastructure

As dependências devem apontar para dentro.

O domínio não deve conhecer:

- HTML
- detalhes específicos da Amazon
- SQL específico
- Excel
- APIs de mensageria

## Estrutura

```text
src/
├── main/
│   ├── java/
│   │   └── com/raspingamazon/
│   │       ├── application/
│   │       ├── domain/
│   │       ├── infrastructure/
│   │       └── presentation/
│   │
│   └── resources/
│
└── test/
    └── java/
        └── com/raspingamazon/