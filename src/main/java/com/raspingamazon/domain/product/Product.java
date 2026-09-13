package com.raspingamazon.domain.product;

import java.util.Objects;

/**
 * Representa um produto dentro do domínio da aplicação.
 *
 * O Product representa a identidade do produto, e não uma oferta
 * específica encontrada em determinado momento.
 *
 * O ASIN é a identidade externa utilizada pelo projeto para distinguir
 * produtos. Informações que variam ao longo do tempo, como preço,
 * desconto e percentual vendido, não pertencem a esta entidade.
 *
 * Essa separação é importante porque o projeto precisa preservar
 * históricos através de OfferSnapshot, que será implementado
 * posteriormente.
 *
 * A entidade não conhece:
 *
 * - PostgreSQL;
 * - JDBC;
 * - Amazon;
 * - HTML;
 * - Excel;
 * - APIs externas;
 * - canais de publicação.
 *
 * Esses detalhes pertencem às respectivas camadas de infraestrutura
 * e aplicação.
 */
public final class Product {

    /**
     * Identificador interno do produto dentro do domínio.
     *
     * Neste momento ele é representado como Long porque o schema da
     * FASE 2 utiliza BIGINT como chave primária.
     *
     * Isso não significa que esta classe esteja acoplada ao PostgreSQL:
     * Long é apenas o tipo escolhido para representar a identidade
     * persistente da entidade no domínio.
     */
    private final Long id;

    /**
     * Identificador do produto na Amazon.
     *
     * O tipo Asin impede que um String arbitrário seja utilizado
     * diretamente como identificador do produto.
     */
    private final Asin asin;

    /**
     * Título atual conhecido para o produto.
     *
     * O título pertence ao Product porque descreve o produto, enquanto
     * os dados específicos de uma oferta pertencem ao OfferSnapshot.
     */
    private final String title;

    /**
     * URL da imagem principal do produto, quando disponível.
     *
     * A ausência da imagem não deve impedir a representação do produto,
     * pois a documentação do projeto trata image_url como campo opcional.
     */
    private final String imageUrl;

    /**
     * URL do produto.
     *
     * Diferentemente da imagem, esta informação é considerada necessária
     * para representar corretamente o produto no domínio.
     */
    private final String productUrl;

    /**
     * Construtor principal da entidade.
     *
     * As invariantes básicas são verificadas aqui para que qualquer
     * instância de Product criada pelo sistema seja válida.
     */
    public Product(
            Long id,
            Asin asin,
            String title,
            String imageUrl,
            String productUrl
    ) {
        /*
         * O id pode ser nulo durante a criação antes da persistência.
         *
         * Isso permite que a camada de persistência atribua o identificador
         * quando necessário, sem obrigar o domínio a conhecer a estratégia
         * de geração da chave.
         */
        this.id = id;

        /*
         * ASIN é obrigatório porque representa a identidade externa
         * fundamental do produto.
         */
        this.asin = Objects.requireNonNull(
                asin,
                "Product ASIN must not be null"
        );

        /*
         * Um produto sem título não possui informação mínima suficiente
         * para ser representado corretamente.
         */
        this.title = requireText(
                title,
                "Product title must not be blank"
        );

        /*
         * imageUrl é opcional conforme definido na modelagem do projeto.
         * Por isso não aplicamos requireText aqui.
         */
        this.imageUrl = imageUrl;

        /*
         * A URL do produto é obrigatória para que a aplicação consiga
         * referenciar a oferta/produto posteriormente.
         */
        this.productUrl = requireText(
                productUrl,
                "Product URL must not be blank"
        );
    }

    /**
     * Valida textos obrigatórios do domínio.
     *
     * Mantemos essa pequena regra centralizada para evitar repetir
     * a mesma combinação de null/blank em cada campo obrigatório.
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

    public Asin asin() {
        return asin;
    }

    public String title() {
        return title;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String productUrl() {
        return productUrl;
    }
}