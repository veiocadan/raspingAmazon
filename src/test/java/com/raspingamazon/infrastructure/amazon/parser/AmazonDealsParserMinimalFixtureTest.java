package com.raspingamazon.infrastructure.amazon.parser;

import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testes das fixtures HTML mínimas mantidas no repositório.
 *
 * <p>Estas fixtures representam cenários estruturais estáveis necessários
 * para validar o parser sem depender de capturas completas da Amazon.</p>
 *
 * <p>O objetivo é manter no Git apenas o menor conteúdo necessário para
 * reproduzir os contratos relevantes:</p>
 *
 * <ul>
 *     <li>uma oferta básica válida;</li>
 *     <li>uma oferta equivalente repetida, que deve ser deduplicada;</li>
 *     <li>uma oferta inválida, que deve ser descartada.</li>
 * </ul>
 */
class AmazonDealsParserMinimalFixtureTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-19T03:00:00Z"
        );

    private static final String SOURCE =
        "https://www.amazon.com.br/deals";

    private static final String BASIC_FIXTURE =
        "amazon/fixtures/deals/basic-deal.html";

    private static final String DUPLICATE_FIXTURE =
        "amazon/fixtures/deals/duplicate-deal.html";

    private static final String INVALID_FIXTURE =
        "amazon/fixtures/deals/invalid-deal.html";

    private final AmazonDealsParser parser =
        new AmazonDealsParser();

    @Test
    void shouldParseBasicDealFixture()
        throws Exception {

        CollectionResult collectionResult =
            collectionResult(
                loadFixture(
                    BASIC_FIXTURE
                )
            );

        List<ParsedDeal> deals =
            parser.parse(
                collectionResult
            );

        assertEquals(
            1,
            deals.size()
        );

        ParsedDeal deal =
            deals.getFirst();

        assertEquals(
            "B087WLJH8Y",
            deal.asin()
        );

        assertEquals(
            new BigDecimal(
                "79.90"
            ),
            deal.currentPrice()
        );

        assertEquals(
            new BigDecimal(
                "99.90"
            ),
            deal.basisPrice()
        );
    }

    @Test
    void shouldDeduplicateEquivalentDealsFromMinimalFixture()
        throws Exception {

        CollectionResult collectionResult =
            collectionResult(
                loadFixture(
                    DUPLICATE_FIXTURE
                )
            );

        List<ParsedDeal> deals =
            parser.parse(
                collectionResult
            );

        assertEquals(
            1,
            deals.size()
        );

        ParsedDeal deal =
            deals.getFirst();

        assertEquals(
            "B012345678",
            deal.asin()
        );

        assertEquals(
            new BigDecimal(
                "49.90"
            ),
            deal.currentPrice()
        );

        assertEquals(
            new BigDecimal(
                "79.90"
            ),
            deal.basisPrice()
        );

        assertEquals(
            new BigDecimal(
                "55"
            ),
            deal.soldPercentage()
        );
    }

    @Test
    void shouldDiscardInvalidDealFromMinimalFixture()
        throws Exception {

        CollectionResult collectionResult =
            collectionResult(
                loadFixture(
                    INVALID_FIXTURE
                )
            );

        List<ParsedDeal> deals =
            parser.parse(
                collectionResult
            );

        assertEquals(
            0,
            deals.size()
        );
    }

    /**
     * Cria o contrato usado como entrada do parser.
     */
    private CollectionResult collectionResult(
        String content
    ) {

        return new CollectionResult(
            content,
            COLLECTED_AT,
            SOURCE
        );
    }

    /**
     * Carrega uma fixture textual pelo classpath.
     */
    private String loadFixture(
        String resourcePath
    ) throws Exception {

        ClassLoader classLoader =
            getClass()
                .getClassLoader();

        try (InputStream inputStream =
                 classLoader.getResourceAsStream(
                     resourcePath
                 )) {

            assertNotNull(
                inputStream,
                "Fixture not found: "
                    + resourcePath
            );

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
