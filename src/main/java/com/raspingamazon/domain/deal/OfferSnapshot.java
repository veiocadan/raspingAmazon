package com.raspingamazon.domain.deal;

import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa uma fotografia de uma oferta em um determinado momento.
 *
 * Diferentemente de Product, que representa a identidade do produto,
 * OfferSnapshot representa os dados observados durante uma coleta.
 *
 * Isso permite preservar o histórico de alterações de preço, desconto,
 * percentual vendido e demais informações da oferta ao longo do tempo.
 *
 * A entidade pertence exclusivamente ao domínio:
 *
 * - não conhece PostgreSQL;
 * - não conhece JDBC;
 * - não conhece HTML;
 * - não conhece a API da Amazon;
 * - não conhece Excel;
 * - não conhece canais de publicação.
 *
 * A infraestrutura será responsável posteriormente por transformar
 * este objeto em dados persistíveis.
 */
public final class OfferSnapshot {

    /**
     * Identificador interno do snapshot.
     *
     * Assim como Product, pode ser nulo enquanto o objeto ainda não
     * recebeu um identificador da camada de persistência.
     */
    private final Long id;

    /**
     * Produto ao qual esta fotografia da oferta pertence.
     *
     * O snapshot não substitui o Product: ele representa uma ocorrência
     * temporal dos dados de oferta desse produto.
     */
    private final Product product;

    /**
     * Momento em que os dados foram coletados.
     *
     * collected_at é um campo obrigatório definido na modelagem da
     * FASE 2 porque cada snapshot precisa possuir uma referência temporal.
     */
    private final OffsetDateTime collectedAt;

    /**
     * Preço atual observado na coleta.
     *
     * É obrigatório porque representa o principal valor comercial
     * observado da oferta.
     */
    private final Money currentPrice;

    /**
     * Preço-base/lista observado na fonte, quando disponível.
     *
     * Este valor possui semântica própria e não deve ser confundido
     * com previousPrice, que representa um preço anterior histórico
     * quando houver evidência explícita dessa informação.
     */
    private final Money basisPrice;

    /**
     * Preço anterior, quando houver evidência dessa informação.
     *
     * O campo é opcional porque a fonte pode não fornecer esse dado.
     */
    private final Money previousPrice;

    /**
     * Percentual de desconto derivado dos preços quando houver dados
     * suficientes para realizar essa derivação.
     *
     * O domínio recebe o valor já determinado; o cálculo específico
     * será tratado posteriormente por uma regra própria.
     */
    private final Percentage discountPercentage;

    /**
     * Percentual vendido/reivindicado durante a promoção, quando houver
     * evidência suficiente na fonte.
     *
     * Importante: null significa "não disponível", e não zero.
     * O projeto determina que esse valor não seja estimado quando
     * não houver evidência confiável.
     */
    private final Percentage soldPercentage;

    /**
     * Avaliação média do produto, quando disponível.
     *
     * O significado exato e as regras de validação desse campo poderão
     * ser refinados quando a avaliação comercial for implementada.
     */
    private final Double rating;

    /**
     * Quantidade de avaliações/reviews, quando disponível.
     */
    private final Long reviewCount;

    /**
     * Nome do vendedor observado.
     *
     * Este campo mantém o texto observado, enquanto SellerType representa
     * a classificação normalizada utilizada pelas regras de negócio.
     */
    private final String sellerName;

    /**
     * Nome do responsável pela entrega observado na fonte.
     *
     * DeliveryType representa a classificação normalizada correspondente.
     */
    private final String deliveryProvider;

    /**
     * Classificação normalizada do vendedor.
     *
     * UNKNOWN será utilizado quando não houver evidência suficiente.
     */
    private final SellerType sellerType;

    /**
     * Classificação normalizada da entrega.
     *
     * UNKNOWN será utilizado quando não houver evidência suficiente.
     */
    private final DeliveryType deliveryType;

    /**
     * Identifica a origem dos dados coletados.
     *
     * A origem é importante para auditoria e rastreabilidade, mas
     * o domínio não interpreta aqui o conteúdo específico da origem.
     */
    private final String source;

    /**
     * Construtor principal do snapshot.
     *
     * As regras aqui são apenas invariantes estruturais do conceito.
     * Regras de elegibilidade, filtros e scoring não pertencem a esta
     * entidade e serão implementados posteriormente.
     */
    public OfferSnapshot(
            Long id,
            Product product,
            OffsetDateTime collectedAt,
            Money currentPrice,
            Money basisPrice,
            Money previousPrice,
            Percentage discountPercentage,
            Percentage soldPercentage,
            Double rating,
            Long reviewCount,
            String sellerName,
            String deliveryProvider,
            SellerType sellerType,
            DeliveryType deliveryType,
            String source
    ) {
        /*
         * O identificador pode ser nulo antes da persistência.
         */
        this.id = id;

        /*
         * Um snapshot precisa estar associado a um produto existente
         * dentro do domínio.
         */
        this.product = Objects.requireNonNull(
                product,
                "OfferSnapshot product must not be null"
        );

        /*
         * Sem o momento da coleta não existe uma fotografia temporal
         * suficientemente definida.
         */
        this.collectedAt = Objects.requireNonNull(
                collectedAt,
                "OfferSnapshot collectedAt must not be null"
        );

        /*
         * O preço atual é obrigatório conforme o modelo do snapshot.
         */
        this.currentPrice = Objects.requireNonNull(
                currentPrice,
                "OfferSnapshot currentPrice must not be null"
        );

        /*
         * basisPrice é opcional porque a fonte pode não fornecer
         * o preço-base/lista.
         *
         * Ele não substitui previousPrice.
         */
        this.basisPrice = basisPrice;

        /*
         * previousPrice, discountPercentage e soldPercentage podem ser
         * nulos porque a fonte pode não fornecer essas informações.
         */
        this.previousPrice = previousPrice;
        this.discountPercentage = discountPercentage;
        this.soldPercentage = soldPercentage;

        /*
         * Rating e reviewCount são campos opcionais definidos como tal
         * na especificação inicial do projeto.
         */
        this.rating = rating;
        this.reviewCount = reviewCount;

        /*
         * O nome textual do vendedor e o responsável pela entrega são
         * preservados separadamente das classificações normalizadas.
         */
        this.sellerName = sellerName;
        this.deliveryProvider = deliveryProvider;

        /*
         * A classificação não deve ser nula. Quando não houver evidência,
         * utiliza-se UNKNOWN, mantendo a política "fail closed".
         */
        this.sellerType = Objects.requireNonNull(
                sellerType,
                "OfferSnapshot sellerType must not be null"
        );

        this.deliveryType = Objects.requireNonNull(
                deliveryType,
                "OfferSnapshot deliveryType must not be null"
        );

        /*
         * source é obrigatório porque a origem da coleta faz parte
         * da rastreabilidade do snapshot.
         */
        this.source = requireText(
                source,
                "OfferSnapshot source must not be blank"
        );
    }

    /**
     * Valida textos obrigatórios.
     *
     * A validação fica centralizada para evitar duplicação e garantir
     * comportamento consistente entre os campos textuais obrigatórios.
     */
    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    public Long id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public OffsetDateTime collectedAt() {
        return collectedAt;
    }

    public Money currentPrice() {
        return currentPrice;
    }

    public Money basisPrice() {
        return basisPrice;
    }

    public Money previousPrice() {
        return previousPrice;
    }

    public Percentage discountPercentage() {
        return discountPercentage;
    }

    public Percentage soldPercentage() {
        return soldPercentage;
    }

    public Double rating() {
        return rating;
    }

    public Long reviewCount() {
        return reviewCount;
    }

    public String sellerName() {
        return sellerName;
    }

    public String deliveryProvider() {
        return deliveryProvider;
    }

    public SellerType sellerType() {
        return sellerType;
    }

    public DeliveryType deliveryType() {
        return deliveryType;
    }

    public String source() {
        return source;
    }
}