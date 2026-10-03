package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.collection.contract.SourceChangedException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonProductPageDocumentValidatorTest {

    private final AmazonProductPageDocumentValidator validator =
        new AmazonProductPageDocumentValidator();

    @Test
    void shouldAcceptDocumentWithProductTitle() {

        assertDoesNotThrow(
            () -> validator.validate(
                """
                <html>
                    <body>
                        <span id="productTitle">
                            Notebook
                        </span>
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void shouldAcceptDocumentWithAsinInput() {

        assertDoesNotThrow(
            () -> validator.validate(
                """
                <html>
                    <body>
                        <input
                            id="ASIN"
                            value="B000000001">
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void shouldAcceptProductDocumentWithoutSellerOrDelivery() {

        assertDoesNotThrow(
            () -> validator.validate(
                """
                <html>
                    <body>
                        <span id="productTitle">
                            Produto sem oferta principal disponível
                        </span>
                    </body>
                </html>
                """
            )
        );
    }

    @Test
    void shouldRejectCaptchaBeforeStructuralValidation() {

        SourceRestrictionException exception =
            assertThrows(
                SourceRestrictionException.class,
                () -> validator.validate(
                    """
                    <html>
                        <head>
                            <title>Robot Check</title>
                        </head>
                        <body>
                            <form action="/errors/validateCaptcha">
                            </form>
                        </body>
                    </html>
                    """
                )
            );

        assertEquals(
            SourceRestrictionType.CAPTCHA,
            exception.restrictionType()
        );
    }

    @Test
    void shouldRejectRobotChallengeBeforeStructuralValidation() {

        SourceRestrictionException exception =
            assertThrows(
                SourceRestrictionException.class,
                () -> validator.validate(
                    """
                    <html>
                        <head>
                            <title>Robot Check</title>
                        </head>
                        <body>
                            Sorry, we just need to make sure
                            you're not a robot
                        </body>
                    </html>
                    """
                )
            );

        assertEquals(
            SourceRestrictionType.CHALLENGE,
            exception.restrictionType()
        );
    }

    @Test
    void shouldFailClosedWhenProductStructureIsNotRecognized() {

        SourceChangedException exception =
            assertThrows(
                SourceChangedException.class,
                () -> validator.validate(
                    """
                    <html>
                        <body>
                            <main>
                                Unexpected Amazon document
                            </main>
                        </body>
                    </html>
                    """
                )
            );

        assertEquals(
            AmazonProductPageDocumentValidator
                .STRUCTURE_UNRECOGNIZED_ERROR_CODE,
            exception.errorCode()
        );
    }

    @Test
    void shouldNotAcceptCommercialTextWithoutProductIdentity() {

        assertThrows(
            SourceChangedException.class,
            () -> validator.validate(
                """
                <html>
                    <body>
                        <div>
                            Vendido por Amazon.com.br
                        </div>
                        <div>
                            Enviado por Amazon
                        </div>
                    </body>
                </html>
                """
            )
        );
    }
}
