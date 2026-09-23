package com.raspingamazon.infrastructure.config;

/**
 * Carrega a configuração do Programa de Associados Amazon
 * a partir do ambiente de execução.
 */
public final class AmazonAffiliateConfigProvider {

    public static final String ASSOCIATE_TAG_VARIABLE =
        "AMAZON_ASSOCIATE_TAG";

    private AmazonAffiliateConfigProvider() {
    }

    public static AmazonAffiliateConfig load() {

        String associateTag =
            System.getenv(
                ASSOCIATE_TAG_VARIABLE
            );

        if (associateTag == null
            || associateTag.isBlank()) {

            throw new IllegalStateException(
                "Required environment variable is missing: "
                    + ASSOCIATE_TAG_VARIABLE
            );
        }

        return new AmazonAffiliateConfig(
            associateTag
        );
    }
}
