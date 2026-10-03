package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.collection.contract.SourceChangedException;
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
 * <p>A responsabilidade desta classe é exclusivamente estabelecer se o
 * documento pode entrar no pipeline normal de parsing.</p>
 *
 * <p>Há três resultados possíveis:</p>
 *
 * <ol>
 *     <li>documento de produto reconhecido: processamento continua;</li>
 *     <li>restrição explícita da fonte: SourceRestrictionException;</li>
 *     <li>estrutura incompatível com página de produto conhecida:
 *         SourceChangedException.</li>
 * </ol>
 *
 * <p>A ausência isolada de seller, delivery, rating, reviews ou condições
 * comerciais não é considerada SOURCE_CHANGED. Esses campos possuem suas
 * próprias semânticas de ausência.</p>
 */
public final class AmazonProductPageDocumentValidator {

    public static final String
        STRUCTURE_UNRECOGNIZED_ERROR_CODE =
        "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED";

    private final AmazonSourceRestrictionDetector
        restrictionDetector;

    public AmazonProductPageDocumentValidator() {

        this(
            new AmazonSourceRestrictionDetector()
        );
    }

    AmazonProductPageDocumentValidator(
        AmazonSourceRestrictionDetector restrictionDetector
    ) {

        this.restrictionDetector =
            Objects.requireNonNull(
                restrictionDetector,
                "restrictionDetector must not be null"
            );
    }

    /**
     * Valida o DOM renderizado.
     *
     * <p>A validação estrutural é propositalmente mínima e conservadora.
     * Não exigimos seller/delivery porque a ausência desses campos possui
     * significado próprio e será tratada posteriormente.</p>
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
     * Reconhece somente evidências estruturais de identidade da página.
     *
     * <p>Não utilizamos merchantInfoFeature ou fulfillerInfoFeature como
     * pré-condições, pois isso transformaria ausência legítima de seller
     * ou delivery em falso SOURCE_CHANGED.</p>
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
