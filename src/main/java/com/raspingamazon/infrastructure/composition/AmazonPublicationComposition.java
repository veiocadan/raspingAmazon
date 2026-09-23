package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.application.publication.PublicationGenerator;
import com.raspingamazon.application.publication.PublicationRepository;
import com.raspingamazon.application.publication.affiliate.AffiliateLinkGenerator;
import com.raspingamazon.application.publication.affiliate.AmazonAffiliateLinkGeneratorV1;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.application.publication.presentation.AmazonCommercialPresentationV1;
import com.raspingamazon.application.publication.presentation.CommercialPresentationPolicy;
import com.raspingamazon.application.publication.template.AmazonPublicationV1;
import com.raspingamazon.application.publication.template.PublicationTemplate;
import com.raspingamazon.infrastructure.config.AmazonAffiliateConfig;
import com.raspingamazon.infrastructure.config.AmazonAffiliateConfigProvider;
import com.raspingamazon.infrastructure.persistence.PublicationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcOfferSnapshotEvaluationLoadAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationDataQueryAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root do caso de uso de geração de publicação Amazon.
 *
 * <p>A classe somente conecta implementações concretas aos contratos
 * da aplicação. Nenhuma regra de apresentação, template, geração de
 * link ou persistência deve ser implementada aqui.</p>
 *
 * <p>A mesma Connection é compartilhada pelos adapters JDBC para que
 * o chamador possa controlar a unidade de trabalho externamente.</p>
 */
public final class AmazonPublicationComposition {

    private AmazonPublicationComposition() {
    }

    /**
     * Monta o caso de uso com configuração e relógio de produção.
     */
    public static PublicationGenerator create(
        Connection connection
    ) {

        return create(
            connection,
            AmazonAffiliateConfigProvider.load(),
            Clock.systemUTC()
        );
    }

    /**
     * Variante injetável para testes e outros composition roots.
     */
    public static PublicationGenerator create(
        Connection connection,
        AmazonAffiliateConfig affiliateConfig,
        Clock clock
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        Objects.requireNonNull(
            affiliateConfig,
            "affiliateConfig must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        /*
         * ---------------------------------------------------------
         * LEITURA DOS DADOS DE PUBLICAÇÃO
         * ---------------------------------------------------------
         */
        OfferSnapshotEvaluationLoadPort snapshotLoadPort =
            new JdbcOfferSnapshotEvaluationLoadAdapter(
                connection
            );

        PublicationDataQueryPort publicationDataQueryPort =
            new JdbcPublicationDataQueryAdapter(
                connection,
                snapshotLoadPort
            );

        /*
         * ---------------------------------------------------------
         * POLÍTICA COMERCIAL
         * ---------------------------------------------------------
         */
        CommercialPresentationPolicy commercialPresentationPolicy =
            new AmazonCommercialPresentationV1();

        /*
         * ---------------------------------------------------------
         * LINK DE ASSOCIADO
         * ---------------------------------------------------------
         */
        AffiliateLinkGenerator affiliateLinkGenerator =
            new AmazonAffiliateLinkGeneratorV1(
                affiliateConfig.associateTag()
            );

        /*
         * ---------------------------------------------------------
         * TEMPLATE
         * ---------------------------------------------------------
         */
        PublicationTemplate publicationTemplate =
            new AmazonPublicationV1();

        /*
         * ---------------------------------------------------------
         * PERSISTÊNCIA
         * ---------------------------------------------------------
         */
        PublicationRepository publicationRepository =
            new PublicationJdbcRepository(
                connection
            );

        /*
         * ---------------------------------------------------------
         * CASO DE USO
         * ---------------------------------------------------------
         */
        return new PublicationGenerator(
            publicationDataQueryPort,
            commercialPresentationPolicy,
            affiliateLinkGenerator,
            publicationTemplate,
            publicationRepository,
            clock
        );
    }
}
