package com.raspingamazon.infrastructure.amazon.enrichment;

import org.jsoup.nodes.Document;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Detecta documentos explícitos de página de produto inexistente.
 *
 * <p>Esta classe é deliberadamente conservadora. Ela não considera
 * "currently unavailable" como produto inexistente, pois um produto
 * válido pode temporariamente não possuir oferta principal, seller ou
 * fulfillment observáveis.</p>
 *
 * <p>Nesses casos o documento continua sendo uma observação legítima e
 * as evidências ausentes permanecem UNKNOWN, sendo posteriormente
 * rejeitadas pela política de elegibilidade.</p>
 */
public final class AmazonProductPageUnavailabilityDetector {

    private static final String PAGE_NOT_FOUND_TITLE =
        "page not found";

    private static final String PAGE_NOT_FOUND_TITLE_PT =
        "pagina nao encontrada";

    private static final String COULD_NOT_FIND_PAGE =
        "we couldn't find that page";

    private static final String COULD_NOT_FIND_PAGE_ALTERNATIVE =
        "we could not find that page";

    private static final String COULD_NOT_FIND_PAGE_PT =
        "nao conseguimos encontrar essa pagina";

    /**
     * Retorna true somente quando o documento apresenta evidência
     * explícita de que a página/recurso solicitado não existe.
     */
    public boolean isUnavailable(
        Document document
    ) {

        Objects.requireNonNull(
            document,
            "document must not be null"
        );

        String normalizedTitle =
            normalize(
                document.title()
            );

        if (normalizedTitle.contains(
            PAGE_NOT_FOUND_TITLE
        )
            || normalizedTitle.contains(
            PAGE_NOT_FOUND_TITLE_PT
        )) {

            return true;
        }

        String visibleText =
            document.body() == null
                ? ""
                : document.body()
                .text();

        String normalizedText =
            normalize(
                visibleText
            );

        return normalizedText.contains(
            COULD_NOT_FIND_PAGE
        )
            || normalizedText.contains(
            COULD_NOT_FIND_PAGE_ALTERNATIVE
        )
            || normalizedText.contains(
            COULD_NOT_FIND_PAGE_PT
        );
    }

    private String normalize(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return "";
        }

        String lowerCase =
            value.toLowerCase(
                Locale.ROOT
            );

        String decomposed =
            Normalizer.normalize(
                lowerCase,
                Normalizer.Form.NFD
            );

        return decomposed
            .replaceAll(
                "\\p{M}+",
                ""
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }
}
