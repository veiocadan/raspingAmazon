package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.collection.contract.SourceDataUnavailableException;
import com.raspingamazon.application.collection.contract.SourceChangedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonProductPageDataUnavailableValidationTest {

    private final AmazonProductPageDocumentValidator validator =
        new AmazonProductPageDocumentValidator();

    @Test
    void missingPageShouldBeDataUnavailableInsteadOfSourceChanged() {

        SourceDataUnavailableException exception =
            assertThrows(
                SourceDataUnavailableException.class,
                () -> validator.validate(
                    """
                    <html>
                        <head>
                            <title>
                                Amazon.com Page Not Found
                            </title>
                        </head>
                        <body>
                            SORRY! WE COULDN'T FIND THAT PAGE.
                        </body>
                    </html>
                    """
                )
            );

        assertEquals(
            AmazonProductPageDocumentValidator
                .PRODUCT_NOT_FOUND_ERROR_CODE,
            exception.errorCode()
        );
    }

    @Test
    void unrecognizedDocumentShouldRemainSourceChanged() {

        assertThrows(
            SourceChangedException.class,
            () -> validator.validate(
                """
                <html>
                    <body>
                        <main>
                            Unknown Amazon document
                        </main>
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void validCurrentlyUnavailableProductShouldStillBeValidDocument() {

        assertDoesNotThrow(
            () -> validator.validate(
                """
                <html>
                    <body>
                        <span id="productTitle">
                            Existing product
                        </span>

                        <div id="availability">
                            Currently unavailable.
                        </div>
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void validProductWithoutSellerOrDeliveryShouldStillBeValidDocument() {

        assertDoesNotThrow(
            () -> validator.validate(
                """
                <html>
                    <body>
                        <span id="productTitle">
                            Existing product
                        </span>
                    </body>
                </html>
                """
            )
        );
    }
}
