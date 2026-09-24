package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.RatingEvidence;
import com.raspingamazon.application.enrichment.contract.ReviewCountEvidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonCustomerReviewParserTest {

    private static final String ASIN =
        "B0GVT7QXF7";

    private final AmazonCustomerReviewParser parser =
        new AmazonCustomerReviewParser();

    @Test
    void shouldParseMatchingAsinAndIgnoreDistractorProduct() {

        String html =
            """
            <html>
              <body>
                <div
                    id="averageCustomerReviews_feature_div"
                    data-csa-c-asin="B000000001">
                  <div id="averageCustomerReviews" data-asin="B000000001">
                    <span id="acrPopover" title="2,1 de 5 estrelas"></span>
                    <span
                        id="acrCustomerReviewText"
                        aria-label="9.999 Análises">
                    </span>
                  </div>
                </div>

                <div
                    id="averageCustomerReviews_feature_div"
                    data-csa-c-asin="B0GVT7QXF7">
                  <div id="averageCustomerReviews" data-asin="B0GVT7QXF7">
                    <span id="acrPopover" title="4,8 de 5 estrelas"></span>
                    <span
                        id="acrCustomerReviewText"
                        aria-label="618 Análises">
                    </span>
                  </div>
                </div>
              </body>
            </html>
            """;

        AmazonCustomerReviewParser.ParsedCustomerReviews result =
            parser.parse(
                html,
                ASIN
            );

        RatingEvidence rating =
            result.ratingEvidence();

        ReviewCountEvidence reviews =
            result.reviewCountEvidence();

        assertTrue(
            rating.available()
        );

        assertEquals(
            4.8d,
            rating.rating()
        );

        assertEquals(
            "4,8 de 5 estrelas",
            rating.rawValue()
        );

        assertEquals(
            "averageCustomerReviews_feature_div/acrPopover@title",
            rating.source()
        );

        assertTrue(
            reviews.available()
        );

        assertEquals(
            618L,
            reviews.reviewCount()
        );

        assertEquals(
            "618 Análises",
            reviews.rawValue()
        );

        assertEquals(
            "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label",
            reviews.source()
        );
    }

    @Test
    void shouldNormalizeLocalizedThousandsSeparator() {

        String html =
            """
            <div
                id="averageCustomerReviews_feature_div"
                data-csa-c-asin="B0GVT7QXF7">
              <div id="averageCustomerReviews" data-asin="B0GVT7QXF7">
                <span id="acrPopover" title="4,5 de 5 estrelas"></span>
                <span
                    id="acrCustomerReviewText"
                    aria-label="1.607 Análises">
                </span>
              </div>
            </div>
            """;

        AmazonCustomerReviewParser.ParsedCustomerReviews result =
            parser.parse(
                html,
                ASIN
            );

        assertEquals(
            4.5d,
            result.ratingEvidence().rating()
        );

        assertEquals(
            1607L,
            result.reviewCountEvidence().reviewCount()
        );
    }

    @Test
    void shouldUseAsinScopedFallbackContainer() {

        String html =
            """
            <div id="averageCustomerReviews" data-asin="B0GVT7QXF7">
              <span id="acrPopover" title="4,7 de 5 estrelas"></span>
              <span
                  id="acrCustomerReviewText"
                  aria-label="211 Análises">
              </span>
            </div>
            """;

        AmazonCustomerReviewParser.ParsedCustomerReviews result =
            parser.parse(
                html,
                ASIN
            );

        assertEquals(
            4.7d,
            result.ratingEvidence().rating()
        );

        assertEquals(
            211L,
            result.reviewCountEvidence().reviewCount()
        );

        assertEquals(
            "averageCustomerReviews/acrPopover@title",
            result.ratingEvidence().source()
        );

        assertEquals(
            "averageCustomerReviews/acrCustomerReviewText@aria-label",
            result.reviewCountEvidence().source()
        );
    }

    @Test
    void shouldRemainUnavailableWhenOnlyAnotherAsinExists() {

        String html =
            """
            <div
                id="averageCustomerReviews_feature_div"
                data-csa-c-asin="B000000001">
              <div id="averageCustomerReviews" data-asin="B000000001">
                <span id="acrPopover" title="5,0 de 5 estrelas"></span>
                <span
                    id="acrCustomerReviewText"
                    aria-label="999 Análises">
                </span>
              </div>
            </div>
            """;

        AmazonCustomerReviewParser.ParsedCustomerReviews result =
            parser.parse(
                html,
                ASIN
            );

        assertFalse(
            result.ratingEvidence().available()
        );

        assertNull(
            result.ratingEvidence().rawValue()
        );

        assertFalse(
            result.reviewCountEvidence().available()
        );

        assertNull(
            result.reviewCountEvidence().rawValue()
        );
    }

    @Test
    void shouldPreserveRawEvidenceWhenValueCannotBeNormalized() {

        String html =
            """
            <div
                id="averageCustomerReviews_feature_div"
                data-csa-c-asin="B0GVT7QXF7">
              <div id="averageCustomerReviews" data-asin="B0GVT7QXF7">
                <span id="acrPopover" title="rating indisponível"></span>
                <span
                    id="acrCustomerReviewText"
                    aria-label="sem contagem">
                </span>
              </div>
            </div>
            """;

        AmazonCustomerReviewParser.ParsedCustomerReviews result =
            parser.parse(
                html,
                ASIN
            );

        assertFalse(
            result.ratingEvidence().available()
        );

        assertEquals(
            "rating indisponível",
            result.ratingEvidence().rawValue()
        );

        assertFalse(
            result.reviewCountEvidence().available()
        );

        assertEquals(
            "sem contagem",
            result.reviewCountEvidence().rawValue()
        );
    }

    @Test
    void shouldRejectInvalidInput() {

        assertThrows(
            NullPointerException.class,
            () -> parser.parse(
                null,
                ASIN
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                "   ",
                ASIN
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                "<html></html>",
                "INVALID"
            )
        );
    }
}
