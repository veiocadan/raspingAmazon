package com.raspingamazon.infrastructure.amazon.parser;

import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes específicos do contrato de soldPercentage.
 *
 * <p>O parser não deve transportar percentuais que o domínio
 * posteriormente rejeitaria.</p>
 */
class AmazonDealsParserPercentageContractTest {

    private static final String SOURCE =
            "https://www.amazon.com.br/deals";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T12:00:00Z"
            );

    private final AmazonDealsParser parser =
            new AmazonDealsParser();

    @Test
    void shouldAcceptZeroPercentage() {

        ParsedDeal deal =
                parseSingleDeal(
                        "0"
                );

        assertEquals(
                new BigDecimal("0"),
                deal.soldPercentage()
        );
    }

    @Test
    void shouldAcceptOneHundredPercentage() {

        ParsedDeal deal =
                parseSingleDeal(
                        "100"
                );

        assertEquals(
                new BigDecimal("100"),
                deal.soldPercentage()
        );
    }

    @Test
    void shouldAcceptPercentageInsideRange() {

        ParsedDeal deal =
                parseSingleDeal(
                        "37.5"
                );

        assertEquals(
                new BigDecimal("37.5"),
                deal.soldPercentage()
        );
    }

    @Test
    void shouldTreatNegativePercentageAsMissing() {

        ParsedDeal deal =
                parseSingleDeal(
                        "-1"
                );

        assertNull(
                deal.soldPercentage()
        );
    }

    @Test
    void shouldTreatPercentageAboveOneHundredAsMissing() {

        ParsedDeal deal =
                parseSingleDeal(
                        "101"
                );

        assertNull(
                deal.soldPercentage()
        );
    }

    @Test
    void shouldTreatMalformedPercentageAsMissing() {

        ParsedDeal deal =
                parseSingleDeal(
                        "indisponivel"
                );

        assertNull(
                deal.soldPercentage()
        );
    }

    /**
     * Produz uma oferta sintética variando somente percentClaimed.
     */
    private ParsedDeal parseSingleDeal(
            String percentClaimed
    ) {

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
                        },
                        "dealDetails": {
                          "percentClaimed": "%s"
                        }
                      }
                    ]
                  }
                }
                """.formatted(
                percentClaimed
        );

        CollectionResult collectionResult =
                new CollectionResult(
                        content,
                        COLLECTED_AT,
                        SOURCE
                );

        List<ParsedDeal> deals =
                parser.parse(
                        collectionResult
                );

        assertEquals(
                1,
                deals.size()
        );

        return deals.getFirst();
    }
}