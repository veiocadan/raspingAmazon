package com.raspingamazon.infrastructure.amazon.parser;

import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do contrato de rating e reviewCount da fonte Amazon Deals.
 */
class AmazonDealsParserCustomerReviewsContractTest {

    private static final String SOURCE =
            "https://www.amazon.com.br/deals";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2026-09-18T12:00:00Z");

    private static final String FIXTURE_PATH =
            "amazon/deals-sample.html";

    private final AmazonDealsParser parser =
            new AmazonDealsParser();

    @Test
    void shouldExtractRatingAndReviewCount() {
        ParsedDeal deal =
                parseSingleDeal(
                        """
                        "customerReviews": {
                          "count": {
                            "displayString": "1.282",
                            "value": 1282
                          },
                          "rating": {
                            "fullStarCount": 4,
                            "shortDisplayString": "4,6",
                            "hasHalfStar": true
                          }
                        }
                        """
                );

        assertEquals(4.6, deal.rating());
        assertEquals(1282L, deal.reviewCount());
    }

    @Test
    void shouldKeepRatingAndReviewCountNullWhenCustomerReviewsAreMissing() {
        ParsedDeal deal =
                parseSingleDeal(null);

        assertNull(deal.rating());
        assertNull(deal.reviewCount());
    }

    @Test
    void shouldTreatInvalidRatingAsMissingWithoutDiscardingReviewCount() {
        ParsedDeal deal =
                parseSingleDeal(
                        """
                        "customerReviews": {
                          "count": {
                            "value": 10
                          },
                          "rating": {
                            "shortDisplayString": "indisponivel"
                          }
                        }
                        """
                );

        assertNull(deal.rating());
        assertEquals(10L, deal.reviewCount());
    }

    @Test
    void shouldTreatRatingAboveFiveAsMissing() {
        ParsedDeal deal =
                parseSingleDeal(
                        """
                        "customerReviews": {
                          "count": {
                            "value": 10
                          },
                          "rating": {
                            "shortDisplayString": "5,1"
                          }
                        }
                        """
                );

        assertNull(deal.rating());
        assertEquals(10L, deal.reviewCount());
    }

    @Test
    void shouldTreatNegativeReviewCountAsMissingWithoutDiscardingRating() {
        ParsedDeal deal =
                parseSingleDeal(
                        """
                        "customerReviews": {
                          "count": {
                            "value": -1
                          },
                          "rating": {
                            "shortDisplayString": "4,7"
                          }
                        }
                        """
                );

        assertEquals(4.7, deal.rating());
        assertNull(deal.reviewCount());
    }

    @Test
    void shouldExtractKnownCustomerReviewValuesFromRealFixture()
            throws IOException {

        List<ParsedDeal> deals =
                parser.parse(
                        new CollectionResult(
                                loadFixture(),
                                COLLECTED_AT,
                                SOURCE
                        )
                );

        ParsedDeal creatine =
                deals.stream()
                        .filter(
                                deal -> "B087WLJH8Y".equals(
                                        deal.asin()
                                )
                        )
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError(
                                        "Expected fixture ASIN B087WLJH8Y was not parsed"
                                )
                        );

        assertEquals(4.6, creatine.rating());
        assertEquals(58363L, creatine.reviewCount());
    }

    @Test
    void shouldPreserveDealsThatHaveNoCustomerReviews() {
        ParsedDeal deal =
                parseSingleDeal(
                        """
                        "customerReviews": {}
                        """
                );

        assertTrue(deal.currentPrice().signum() > 0);
        assertNull(deal.rating());
        assertNull(deal.reviewCount());
    }

    private ParsedDeal parseSingleDeal(
            String customerReviewsFragment
    ) {
        String optionalFragment =
                customerReviewsFragment == null
                        ? ""
                        : ",\n" + customerReviewsFragment;

        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B012345678",
                        "title": "Produto",
                        "link": "/produto/dp/B012345678",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }%s
                      }
                    ]
                  }
                }
                """.formatted(optionalFragment);

        List<ParsedDeal> deals =
                parser.parse(
                        new CollectionResult(
                                content,
                                COLLECTED_AT,
                                SOURCE
                        )
                );

        assertEquals(1, deals.size());

        return deals.getFirst();
    }

    private String loadFixture()
            throws IOException {

        ClassLoader classLoader =
                getClass().getClassLoader();

        try (InputStream inputStream =
                     classLoader.getResourceAsStream(
                             FIXTURE_PATH
                     )) {

            if (inputStream == null) {
                throw new IOException(
                        "Fixture not found: " + FIXTURE_PATH
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
