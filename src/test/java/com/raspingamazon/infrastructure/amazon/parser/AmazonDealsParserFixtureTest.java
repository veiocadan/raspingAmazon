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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração do parser utilizando uma fixture mínima
 * representativa da estrutura Amazon Deals.
 *
 * <p>A fixture não é uma captura integral da página real.
 * Ela preserva somente a estrutura e os campos consumidos pelo parser.</p>
 *
 * <p>Com isso, o teste permanece hermético, legível e independente
 * de conteúdo irrelevante da página da Amazon.</p>
 */
class AmazonDealsParserFixtureTest {

    private static final String FIXTURE_PATH =
        "amazon/fixtures/deals/basic-deal.html";

    private static final String SOURCE =
        "https://www.amazon.com.br/deals";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-16T12:00:00Z"
        );

    private final AmazonDealsParser parser =
        new AmazonDealsParser();

    @Test
    void shouldParseMinimalAmazonDealsFixture()
        throws IOException {

        List<ParsedDeal> deals =
            parseFixture();

        assertFalse(
            deals.isEmpty(),
            "A fixture mínima deve produzir pelo menos uma oferta válida."
        );

        assertEquals(
            1,
            deals.size()
        );
    }

    @Test
    void shouldExtractValidAsinsFromFixture()
        throws IOException {

        List<ParsedDeal> deals =
            parseFixture();

        assertFalse(
            deals.isEmpty()
        );

        for (ParsedDeal deal : deals) {

            assertNotNull(
                deal.asin()
            );

            assertTrue(
                deal.asin()
                    .matches(
                        "[A-Z0-9]{10}"
                    ),
                "ASIN inválido encontrado: "
                    + deal.asin()
            );
        }
    }

    @Test
    void shouldExtractCurrentPriceFromFixture()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertNotNull(
            deal.currentPrice()
        );

        assertTrue(
            deal.currentPrice()
                .signum() > 0
        );

        assertEquals(
            "79.90",
            deal.currentPrice()
                .toPlainString()
        );
    }

    @Test
    void shouldExtractBasisPriceFromFixture()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertNotNull(
            deal.basisPrice()
        );

        assertEquals(
            "99.90",
            deal.basisPrice()
                .toPlainString()
        );
    }

    @Test
    void shouldExtractProductIdentityFromFixture()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertEquals(
            "B087WLJH8Y",
            deal.asin()
        );

        assertEquals(
            "Creatina Monohidratada 300g",
            deal.title()
        );

        assertEquals(
            "https://www.amazon.com.br/creatina-monohidratada/dp/B087WLJH8Y",
            deal.productUrl()
        );
    }

    @Test
    void shouldExtractSoldPercentageFromFixture()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertNotNull(
            deal.soldPercentage()
        );

        assertEquals(
            "37",
            deal.soldPercentage()
                .toPlainString()
        );

        assertTrue(
            deal.soldPercentage()
                .signum() >= 0
        );

        assertTrue(
            deal.soldPercentage()
                .doubleValue() <= 100.0
        );
    }

    @Test
    void shouldKeepPreviousPriceSeparateFromBasisPrice()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertNotNull(
            deal.basisPrice()
        );

        assertNull(
            deal.previousPrice(),
            "basisPrice não deve ser inferido como previousPrice."
        );
    }

    @Test
    void shouldPreserveCollectionMetadata()
        throws IOException {

        ParsedDeal deal =
            parseSingleDeal();

        assertEquals(
            COLLECTED_AT,
            deal.collectedAt()
        );

        assertEquals(
            SOURCE,
            deal.source()
        );
    }

    /**
     * Executa o parser sobre a fixture mínima.
     */
    private List<ParsedDeal> parseFixture()
        throws IOException {

        CollectionResult collectionResult =
            new CollectionResult(
                loadFixture(),
                COLLECTED_AT,
                SOURCE
            );

        return parser.parse(
            collectionResult
        );
    }

    /**
     * Obtém a única oferta válida presente na fixture.
     */
    private ParsedDeal parseSingleDeal()
        throws IOException {

        List<ParsedDeal> deals =
            parseFixture();

        assertEquals(
            1,
            deals.size(),
            "A fixture deve possuir exatamente uma oferta válida."
        );

        return deals.getFirst();
    }

    /**
     * Carrega a fixture mínima do classpath.
     */
    private String loadFixture()
        throws IOException {

        ClassLoader classLoader =
            getClass()
                .getClassLoader();

        try (InputStream inputStream =
                 classLoader.getResourceAsStream(
                     FIXTURE_PATH
                 )) {

            assertNotNull(
                inputStream,
                "Fixture não encontrada no classpath: "
                    + FIXTURE_PATH
            );

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
