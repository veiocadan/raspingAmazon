package com.raspingamazon.infrastructure.amazon.parser;

import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração do parser com o fixture real da página de ofertas.
 *
 * <p>Este teste não acessa a Amazon. O objetivo é garantir que o parser
 * consiga interpretar um conteúdo bruto previamente capturado e armazenado
 * no classpath do projeto.</p>
 *
 * <p>A utilização de um fixture local torna o teste reproduzível e evita
 * dependência de rede durante a execução da suíte automatizada.</p>
 */
class AmazonDealsParserFixtureTest {

    private static final String FIXTURE_PATH =
            "amazon/deals-sample.html";

    private static final String SOURCE =
            "https://www.amazon.com.br/deals";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2026-09-16T12:00:00Z");

    private final AmazonDealsParser parser = new AmazonDealsParser();

    /**
     * Verifica que o parser consegue processar o conteúdo real capturado
     * da página de ofertas e produzir pelo menos uma oferta válida.
     */
    @Test
    void shouldParseRealAmazonDealsFixture() throws IOException {
        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture real deveria produzir pelo menos uma oferta válida."
        );
    }

    /**
     * Verifica que as ofertas produzidas pelo fixture possuem ASIN válido.
     *
     * <p>O ASIN é o identificador externo utilizado pelo projeto para
     * identificar o produto na fonte Amazon.</p>
     */
    @Test
    void shouldExtractValidAsinsFromRealAmazonDealsFixture()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas para validar os ASINs."
        );

        for (ParsedDeal deal : deals) {
            assertNotNull(deal.asin());

            assertTrue(
                    deal.asin().matches("[A-Z0-9]{10}"),
                    "ASIN inválido encontrado: " + deal.asin()
            );
        }
    }

    /**
     * Verifica que as ofertas válidas possuem preço atual.
     */
    @Test
    void shouldExtractCurrentPriceFromRealAmazonDealsFixture()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas para validar preços."
        );

        for (ParsedDeal deal : deals) {
            assertNotNull(
                    deal.currentPrice(),
                    "Toda oferta válida do parser deve possuir preço atual."
            );

            assertTrue(
                    deal.currentPrice().signum() > 0,
                    "O preço atual deve ser maior que zero."
            );
        }
    }

    /**
     * Verifica que as ofertas válidas possuem título e URL de produto.
     */
    @Test
    void shouldExtractProductIdentityFromRealAmazonDealsFixture()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas para validar identidade."
        );

        for (ParsedDeal deal : deals) {
            assertNotNull(deal.title());
            assertFalse(
                    deal.title().isBlank(),
                    "O título não deveria estar vazio."
            );

            assertNotNull(deal.productUrl());
            assertTrue(
                    deal.productUrl().startsWith("https://www.amazon.com.br/"),
                    "A URL do produto deveria estar normalizada: "
                            + deal.productUrl()
            );
        }
    }

    /**
     * Verifica que o percentual vendido é extraído quando a fonte
     * disponibiliza essa informação.
     *
     * <p>Não exigimos que todas as ofertas possuam o campo, pois a própria
     * estrutura da fonte pode não disponibilizá-lo para todos os registros.
     * Quando presente, entretanto, o valor deve estar dentro do intervalo
     * percentual esperado.</p>
     */
    @Test
    void shouldExtractSoldPercentageWhenAvailable()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas para validar percentual vendido."
        );

        boolean foundSoldPercentage = false;

        for (ParsedDeal deal : deals) {
            if (deal.soldPercentage() != null) {
                foundSoldPercentage = true;

                assertTrue(
                        deal.soldPercentage().signum() >= 0,
                        "O percentual vendido não pode ser negativo."
                );

                assertTrue(
                        deal.soldPercentage().doubleValue() <= 100.0,
                        "O percentual vendido não pode ultrapassar 100."
                );
            }
        }

        assertTrue(
                foundSoldPercentage,
                "O fixture real deveria conter pelo menos uma oferta "
                        + "com percentual vendido disponível."
        );
    }

    /**
     * Verifica que o parser não transforma automaticamente o preço de
     * referência em previousPrice.
     *
     * <p>Essa separação é importante porque basisPrice e previousPrice
     * representam conceitos diferentes no modelo do projeto.</p>
     */
    @Test
    void shouldKeepPreviousPriceSeparateFromBasisPrice()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas para validar preços."
        );

        for (ParsedDeal deal : deals) {
            /*
             * O parser atual não possui uma fonte explícita de previousPrice
             * no objeto da oferta da página de promoções.
             *
             * Portanto, basisPrice não deve ser copiado para previousPrice.
             */
            assertNull(
                    deal.previousPrice(),
                    "basisPrice não deve ser inferido como previousPrice."
            );
        }
    }

    /**
     * Verifica que os dados de coleta são preservados nos objetos produzidos.
     */
    @Test
    void shouldPreserveCollectionMetadata()
            throws IOException {

        String content = loadFixture();

        CollectionResult collectionResult = new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );

        List<ParsedDeal> deals = parser.parse(collectionResult);

        assertFalse(
                deals.isEmpty(),
                "O fixture deveria produzir ofertas."
        );

        for (ParsedDeal deal : deals) {
            assertNotNull(deal.collectedAt());
            assertNotNull(deal.source());

            assertTrue(
                    COLLECTED_AT.equals(deal.collectedAt()),
                    "A data de coleta deveria ser preservada."
            );

            assertTrue(
                    SOURCE.equals(deal.source()),
                    "A fonte deveria ser preservada."
            );
        }
    }

    /**
     * Carrega o fixture real do classpath.
     *
     * @return conteúdo textual do fixture
     * @throws IOException caso o recurso não possa ser lido
     */
    private String loadFixture() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        try (InputStream inputStream =
                     classLoader.getResourceAsStream(FIXTURE_PATH)) {

            assertNotNull(
                    inputStream,
                    "Fixture não encontrado no classpath: " + FIXTURE_PATH
            );

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}