# FASE 0 — Revisitação v2
## Preços, Pix e Parcelamento

**Projeto:** Rasping Amazon  
**Objetivo:** complementar a FASE 0 original antes de avançar com alterações de código relacionadas a preços e publicação.

---

## 1. Motivo da revisitação

A FASE 0 original identificou corretamente o campo `current_price` como o preço atual da oferta, além de `previous_price` e `discount_percentage`.

Entretanto, a investigação original **não fechou a semântica comercial do preço** e não investigou condições de pagamento como Pix e parcelamento.

A busca na documentação da FASE 0 não encontrou levantamento específico sobre:

- parcelamento;
- número de parcelas;
- valor da parcela;
- preço Pix;
- desconto específico para Pix;
- distinção entre preço cheio e preço promocional.

Portanto, esta revisitação deve tratar especificamente dessas lacunas.

---

## 2. Estado atual identificado na FASE 0

O mapa Fonte → Campo original contém:

- `current_price` — API / oferta — direto — obrigatório — preço atual da oferta;
- `previous_price` — API / oferta — direto — opcional — preço anterior/base quando disponível;
- `discount_percentage` — derivado de preço atual + preço anterior / oferta;
- `sold_percentage` — `dealDetails.percentClaimed`;
- `seller_name`;
- `delivery_provider`;
- demais campos de produto e coleta.

A FASE 0 também estabelece que dados não devem ser inventados quando faltarem evidências.

### Ponto crítico

O campo `current_price` foi identificado tecnicamente, mas a FASE 0 **não determinou de forma suficiente qual condição comercial esse valor representa**.

Ainda não está comprovado se o valor capturado é:

- preço cheio;
- preço promocional;
- preço à vista;
- preço Pix;
- menor preço disponível;
- preço associado a alguma condição específica de pagamento.

---

## 3. Parcelamento — PENDENTE

A nova investigação deve verificar se a fonte utilizada pelo projeto fornece informações equivalentes a:

```text
installment_count
installment_amount
installment_total
interest
```

Exemplo desejado:

```text
12x de R$ 100,00
```

Quando disponível, também deve ser possível distinguir:

```text
12x de R$ 100,00 sem juros
```

de:

```text
12x de R$ 100,00 com juros
```

Não calcular ou inferir parcelamento a partir do preço total se a fonte não fornecer evidência suficiente.

---

## 4. Pix — PENDENTE

A investigação deve verificar se existe um preço específico condicionado ao pagamento via Pix.

Exemplo:

```text
Preço normal: R$ 1.000,00
Pix: R$ 950,00
```

Não assumir automaticamente que existe desconto de 5%.

O eventual desconto de Pix deve ser capturado somente quando houver evidência da própria fonte.

Caso exista preço Pix, registrar também a condição que caracteriza esse valor.

---

## 5. Relação com `current_price`

Antes de alterar o modelo, determinar a relação entre:

```text
current_price
previous_price
discount_percentage
```

e eventuais novos dados:

```text
cash_price
pix_price
installment_count
installment_amount
installment_total
interest
```

A decisão de modelagem deve ser tomada **depois** de verificar quais informações a fonte realmente fornece.

Não criar campos apenas por hipótese.

---

## 6. Impacto esperado em `OfferSnapshot`

A FASE 3 atualmente define `OfferSnapshot` com:

```text
id
product
collectedAt
currentPrice
previousPrice
discountPercentage
soldPercentage
rating
reviewCount
sellerName
deliveryProvider
sellerType
deliveryType
source
```

A investigação de preços poderá exigir evolução desse modelo.

### Importante

**Não alterar `OfferSnapshot` antes de concluir a investigação da FASE 0 v2.**

Depois da investigação, revisar:

1. modelo de domínio;
2. persistência PostgreSQL;
3. migration Flyway;
4. parser/normalização;
5. testes;
6. geração da publicação.

---

## 7. Impacto esperado na publicação

O objetivo futuro é permitir uma publicação que apresente, quando houver evidência:

```text
Produto
Preço anterior
Preço atual
Preço Pix
Parcelamento
Quantidade de parcelas
Valor da parcela
Condição de juros
Desconto
```

Exemplo conceitual:

```text
🔥 Produto X

De: R$ 1.499,00
Por: R$ 999,00
💰 Pix: R$ 949,05
💳 12x de R$ 83,25 sem juros
```

Esse exemplo é apenas ilustrativo.

O sistema **não deve produzir nenhum desses valores derivados sem evidência suficiente da fonte**.

---

## 8. Critérios de conclusão da FASE 0 v2

A revisitação deve ser considerada concluída somente quando for possível responder claramente:

### Preço atual

- O que exatamente `current_price` representa?
- Qual é a condição de pagamento associada?
- A fonte identifica explicitamente essa condição?

### Preço Pix

- Existe preço específico para Pix?
- Onde ele aparece?
- Qual é a fonte estruturada ou visual?
- É possível identificar a condição?
- Existe percentual de desconto explícito?

### Parcelamento

- Existe parcelamento?
- Qual é o número de parcelas?
- Qual é o valor de cada parcela?
- Qual é o total parcelado?
- Há juros?
- A informação é explícita ou precisa ser rejeitada por falta de evidência?

### Persistência e domínio

- Quais campos devem realmente ser adicionados?
- Quais campos pertencem a `OfferSnapshot`?
- É necessário alterar o schema PostgreSQL?
- É necessária uma nova migration?

### Publicação

- Quais informações podem ser exibidas com segurança?
- Qual preço deve ser tratado como principal?
- Como representar Pix e parcelamento sem criar informação enganosa?

---

## 9. Decisão de desenvolvimento

**Não alterar o código atual antes da conclusão desta revisitação.**

A FASE 2 já possui `offer_snapshot` com:

```text
current_price
previous_price
discount_percentage
```

e a FASE 3 possui `OfferSnapshot` correspondente.

A possível evolução dessas estruturas deve ocorrer somente depois que a fonte for novamente investigada.

Se novos campos forem necessários, deverão ser introduzidos por evolução versionada do schema, preservando Flyway e o histórico de migrations.

---

## 10. Resultado esperado

Ao final da FASE 0 v2 deverá existir um novo mapa:

```text
Fonte
  ↓
Campo original
  ↓
Campo de preço/condição
  ↓
Evidência
  ↓
Regra de interpretação
  ↓
Obrigatório / Opcional / Derivado
  ↓
Destino no domínio
  ↓
Destino na persistência
  ↓
Uso na publicação
```

A conclusão deve deixar explícito também qualquer informação que **não seja possível obter de forma confiável**.

---

## 11. Relação com as fases já concluídas

- **FASE 0 original:** concluída, mas preço/condições de pagamento ficaram incompletos para o objetivo atual.
- **FASE 1:** não precisa ser alterada.
- **FASE 2:** o schema atual permanece válido até que a investigação determine novas necessidades.
- **FASE 3:** `OfferSnapshot` permanece como está até a conclusão da investigação.
- **FASES posteriores:** devem consumir somente os dados efetivamente definidos e validados.

---

## 12. Regra principal

> **Não assumir que o preço capturado é preço cheio, Pix ou menor preço sem evidência.**
>
> **Não calcular desconto de Pix de 5% por hipótese.**
>
> **Não calcular parcelamento por hipótese.**
>
> **Primeiro investigar a fonte; depois modelar; depois alterar o código.**

---

**Status:** ABERTA — revisitação da FASE 0 necessária antes de fechar definitivamente o modelo de preços.
