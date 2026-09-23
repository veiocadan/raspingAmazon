package com.raspingamazon.application.publication.template;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.presentation.CommercialPresentation;

import java.util.Objects;

/**
 * Entrada completa necessária para renderizar um template
 * de publicação.
 *
 * <p>O objeto reúne:</p>
 *
 * <ul>
 *     <li>dados persistidos que identificam produto e avaliação;</li>
 *     <li>decisão comercial de apresentação;</li>
 *     <li>link de associado já construído externamente.</li>
 * </ul>
 *
 * <p>O template não recebe configuração de associado e não
 * conhece como o link foi produzido.</p>
 */
public record PublicationTemplateInput(
    PublicationData publicationData,
    CommercialPresentation commercialPresentation,
    String affiliateUrl
) {

    public PublicationTemplateInput {

        Objects.requireNonNull(
            publicationData,
            "publicationData must not be null"
        );

        Objects.requireNonNull(
            commercialPresentation,
            "commercialPresentation must not be null"
        );

        affiliateUrl =
            requireText(
                affiliateUrl,
                "affiliateUrl must not be blank"
            );

        /*
         * A apresentação comercial deve ter sido produzida
         * especificamente para os dados utilizados no template.
         *
         * currentPrice é obrigatório em OfferSnapshot e também
         * em CommercialPresentation, permitindo uma verificação
         * estrutural simples contra combinações acidentais de
         * objetos não relacionados.
         */
        if (!publicationData.offerSnapshot()
            .currentPrice()
            .equals(
                commercialPresentation.currentPrice()
            )) {

            throw new IllegalArgumentException(
                "Commercial presentation current price must match publication data"
            );
        }
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
