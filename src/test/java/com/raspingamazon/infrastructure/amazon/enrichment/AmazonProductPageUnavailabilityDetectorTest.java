package com.raspingamazon.infrastructure.amazon.enrichment;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonProductPageUnavailabilityDetectorTest {

    private final AmazonProductPageUnavailabilityDetector detector =
        new AmazonProductPageUnavailabilityDetector();

    @Test
    void shouldDetectEnglishPageNotFoundTitle() {

        assertTrue(
            detector.isUnavailable(
                Jsoup.parse(
                    """
                    <html>
                        <head>
                            <title>
                                Amazon.com Page Not Found
                            </title>
                        </head>
                        <body>
                        </body>
                    </html>
                    """
                )
            )
        );
    }

    @Test
    void shouldDetectPortuguesePageNotFoundTitle() {

        assertTrue(
            detector.isUnavailable(
                Jsoup.parse(
                    """
                    <html>
                        <head>
                            <title>
                                Amazon.com.br: Página não encontrada
                            </title>
                        </head>
                        <body>
                        </body>
                    </html>
                    """
                )
            )
        );
    }

    @Test
    void shouldDetectExplicitMissingPageMessage() {

        assertTrue(
            detector.isUnavailable(
                Jsoup.parse(
                    """
                    <html>
                        <body>
                            <h1>
                                SORRY! WE COULDN'T FIND THAT PAGE.
                            </h1>
                        </body>
                    </html>
                    """
                )
            )
        );
    }

    @Test
    void shouldNotTreatCurrentlyUnavailableProductAsMissingPage() {

        assertFalse(
            detector.isUnavailable(
                Jsoup.parse(
                    """
                    <html>
                        <head>
                            <title>
                                Example product
                            </title>
                        </head>
                        <body>
                            <span id="productTitle">
                                Example product
                            </span>
                            <div>
                                Currently unavailable.
                                We don't know when or if this item
                                will be back in stock.
                            </div>
                        </body>
                    </html>
                    """
                )
            )
        );
    }

    @Test
    void shouldNotTreatNormalProductAsUnavailable() {

        assertFalse(
            detector.isUnavailable(
                Jsoup.parse(
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
            )
        );
    }
}
