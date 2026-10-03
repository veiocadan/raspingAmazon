package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.collection.contract.SourceChangedException;
import com.raspingamazon.application.collection.contract.SourceDataUnavailableException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import com.raspingamazon.infrastructure.amazon.AmazonSourceRestrictionDetector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Objects;

/**
 * Valida o documento renderizado da página individual antes que qualquer
 * parser comercial seja autorizado a interpretá-lo.
 *
 * <p>A responsabilidade desta classe é estabelecer se o documento pode
 * entrar no pipeline normal de parsing.</p>
 *
 * <p>Há quatro resultados possíveis:</p>
 *
 * <ol>
 *     <li>documento de produto reconhecido: processamento continua;</li>
 *     <li>restrição explícita da fonte:
 *         SourceRestrictionException;</li>
 *     <li>recurso explicitamente inexistente:
 *         SourceDataUnavailableException;</li>
 *     <li>estrutura incompatível com página de produto conhecida:
 *         SourceChangedException.</li>
 * </ol>
 *
 * <p>A ausência isolada de seller, delivery, rating, reviews ou
 * condições comerciais não é considerada SOURCE_CHANGED nem
 * DATA_UNAVAILABLE. Esses fatos possuem suas próprias semânticas de
 * ausência.</p>
 */
public final class AmazonProductPageDocumentValidator {

    public static final String
        STRUCTURE_UNRECOGNIZED_ERROR_CODE =
        "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED";

    public static final String
        PRODUCT_NOT_FOUND_ERROR_CODE =
        "AMAZON_PRODUCT_PAGE_NOT_FOUND";

    private final AmazonSourceRestrictionDetector
        restrictionDetector;

    private final AmazonProductPageUnavailabilityDetector
        unavailabilityDetector;

    public AmazonProductPageDocumentValidator() {

        this(
            new AmazonSourceRestrictionDetector(),
            new AmazonProductPageUnavailabilityDetector()
        );
    }

    AmazonProductPageDocumentValidator(
        AmazonSourceRestrictionDetector restrictionDetector,
        AmazonProductPageUnavailabilityDetector unavailabilityDetector
    ) {

        this.restrictionDetector =
            Objects.requireNonNull(
                restrictionDetector,
                "restrictionDetector must not be null"
            );

        this.unavailabilityDetector =
            Objects.requireNonNull(
                unavailabilityDetector,
                "unavailabilityDetector must not be null"
            );
    }

    /**
     * Valida o DOM renderizado.
     *
     * <p>A ordem também possui significado operacional:</p>
     *
     * <pre>
     * proteção da fonte
     *       ↓
     * recurso inexistente
     *       ↓
     * estrutura reconhecida
     *       ↓
     * SOURCE_CHANGED
     * </pre>
     */
    public void validate(
        String html
    ) {

        Objects.requireNonNull(
            html,
            "html must not be null"
        );

        if (html.isBlank()) {

            throw new IllegalArgumentException(
                "html must not be blank"
            );
        }

        SourceRestrictionType restriction =
            restrictionDetector.detect(
                html
            );

        if (restriction != null) {

            throw new SourceRestrictionException(
                restriction
            );
        }

        Document document =
            Jsoup.parse(
                html
            );

        /*
         * Uma página conhecida de "not found" não representa mudança de
         * layout. A estrutura pode estar perfeitamente estável e apenas
         * o produto requisitado ter deixado de existir.
         */
        if (unavailabilityDetector.isUnavailable(
            document
        )) {

            throw new SourceDataUnavailableException(
                PRODUCT_NOT_FOUND_ERROR_CODE,
                "Amazon product page reports "
                    + "that the requested product does not exist"
            );
        }

        if (isRecognizedProductDocument(
            document
        )) {

            return;
        }

        throw new SourceChangedException(
            STRUCTURE_UNRECOGNIZED_ERROR_CODE,
            "Amazon product page no longer exposes "
                + "a recognized product-document structure"
        );
    }

    /**
     * Reconhece evidências estruturais mínimas da identidade de uma
     * página de produto.
     *
     * <p>Seller e delivery deliberadamente não aparecem aqui.</p>
     */
    private boolean isRecognizedProductDocument(
        Document document
    ) {

        if (document.getElementById(
            "productTitle"
        ) != null) {

            return true;
        }

        if (document.selectFirst(
            "input#ASIN"
        ) != null) {

            return true;
        }

        return document.getElementById(
            "dp"
        ) != null;
    }
}
