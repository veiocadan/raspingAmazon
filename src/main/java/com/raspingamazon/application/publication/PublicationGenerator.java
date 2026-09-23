package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.affiliate.AffiliateLink;
import com.raspingamazon.application.publication.affiliate.AffiliateLinkGenerator;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.application.publication.presentation.CommercialPresentation;
import com.raspingamazon.application.publication.presentation.CommercialPresentationPolicy;
import com.raspingamazon.application.publication.template.PublicationTemplate;
import com.raspingamazon.application.publication.template.PublicationTemplateInput;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso responsável por gerar uma Publication reproduzível
 * a partir de uma DealEvaluation persistida.
 *
 * <p>Este componente somente orquestra responsabilidades já
 * separadas:</p>
 *
 * <ul>
 *     <li>carregamento dos dados persistidos;</li>
 *     <li>política comercial de apresentação;</li>
 *     <li>geração do link de associado;</li>
 *     <li>renderização do template;</li>
 *     <li>persistência idempotente da publicação.</li>
 * </ul>
 *
 * <p>Ele não acessa SQL, não reavalia elegibilidade, não conhece
 * parâmetros específicos do programa de associados e não contém
 * formatação textual da publicação.</p>
 */
public final class PublicationGenerator {

    private final PublicationDataQueryPort publicationDataQueryPort;

    private final CommercialPresentationPolicy
        commercialPresentationPolicy;

    private final AffiliateLinkGenerator affiliateLinkGenerator;

    private final PublicationTemplate publicationTemplate;

    private final PublicationRepository publicationRepository;

    private final Clock clock;

    public PublicationGenerator(
        PublicationDataQueryPort publicationDataQueryPort,
        CommercialPresentationPolicy commercialPresentationPolicy,
        AffiliateLinkGenerator affiliateLinkGenerator,
        PublicationTemplate publicationTemplate,
        PublicationRepository publicationRepository,
        Clock clock
    ) {

        this.publicationDataQueryPort =
            Objects.requireNonNull(
                publicationDataQueryPort,
                "publicationDataQueryPort must not be null"
            );

        this.commercialPresentationPolicy =
            Objects.requireNonNull(
                commercialPresentationPolicy,
                "commercialPresentationPolicy must not be null"
            );

        this.affiliateLinkGenerator =
            Objects.requireNonNull(
                affiliateLinkGenerator,
                "affiliateLinkGenerator must not be null"
            );

        this.publicationTemplate =
            Objects.requireNonNull(
                publicationTemplate,
                "publicationTemplate must not be null"
            );

        this.publicationRepository =
            Objects.requireNonNull(
                publicationRepository,
                "publicationRepository must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Gera ou recupera idempotentemente a publicação correspondente
     * à avaliação informada.
     *
     * @param dealEvaluationId id persistido da DealEvaluation
     * @return Publication persistida
     */
    public Publication generate(
        long dealEvaluationId
    ) {

        if (dealEvaluationId <= 0) {

            throw new IllegalArgumentException(
                "dealEvaluationId must be positive"
            );
        }

        PublicationData publicationData =
            publicationDataQueryPort
                .findByDealEvaluationId(
                    dealEvaluationId
                )
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "DealEvaluation not found: "
                            + dealEvaluationId
                    )
                );

        CommercialPresentation commercialPresentation =
            commercialPresentationPolicy.present(
                publicationData
            );

        AffiliateLink affiliateLink =
            affiliateLinkGenerator.generate(
                publicationData.product()
                    .productUrl()
            );

        String generatedText =
            publicationTemplate.render(
                new PublicationTemplateInput(
                    publicationData,
                    commercialPresentation,
                    affiliateLink.url()
                )
            );

        Publication publication =
            new Publication(
                null,
                publicationData.dealEvaluation(),
                publicationTemplate.version(),
                commercialPresentation.policyVersion(),
                affiliateLink.generatorVersion(),
                generatedText,
                affiliateLink.url(),
                PublicationStatus.CREATED,
                OffsetDateTime.now(
                    clock
                )
            );

        return publicationRepository.save(
            publication
        );
    }
}
