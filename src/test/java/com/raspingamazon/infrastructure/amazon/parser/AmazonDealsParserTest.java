package com.raspingamazon.infrastructure.amazon.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do parser da página de ofertas da Amazon.
 *
 * <p>Os testes verificam somente responsabilidades de parsing e normalização
 * estrutural dos dados encontrados na fonte. Regras comerciais, elegibilidade,
 * filtros, score, persistência e publicação não fazem parte deste componente.</p>
 */
class AmazonDealsParserTest {

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2026-09-16T12:00:00Z");

    private static final String SOURCE =
            "https://www.amazon.com.br/deals";

    private final AmazonDealsParser parser = new AmazonDealsParser();

    @Test
    void shouldParseDealWithCompleteData() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B087WLJH8Y",
                        "title": "Creatina Turbo 300g",
                        "link": "/Creatina-Turbo-300g/dp/B087WLJH8Y",
                        "image": {
                          "hiRes": {
                            "baseUrl": "https://m.media-amazon.com/images/I/6165ojDGBPL",
                            "extension": "jpg"
                          }
                        },
                        "price": {
                          "priceToPay": {
                            "price": "15.99"
                          },
                          "basisPrice": {
                            "price": "53.46"
                          }
                        },
                        "dealDetails": {
                          "percentClaimed": 89.0
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());

        ParsedDeal deal = deals.getFirst();

        assertEquals("B087WLJH8Y", deal.asin());
        assertEquals(
                "https://www.amazon.com.br/Creatina-Turbo-300g/dp/B087WLJH8Y",
                deal.productUrl()
        );
        assertEquals("Creatina Turbo 300g", deal.title());
        assertEquals(
                "https://m.media-amazon.com/images/I/6165ojDGBPL.jpg",
                deal.imageUrl()
        );
        assertEquals(new BigDecimal("15.99"), deal.currentPrice());
        assertEquals(new BigDecimal("53.46"), deal.basisPrice());

        /*
         * O basisPrice representa o preço de referência apresentado pela
         * fonte. Ele não deve ser interpretado automaticamente como
         * previousPrice.
         */
        assertNull(deal.previousPrice());

        assertEquals(new BigDecimal("89.0"), deal.soldPercentage());
        assertEquals(COLLECTED_AT, deal.collectedAt());
        assertEquals(SOURCE, deal.source());
    }

    @Test
    void shouldParseAnotherRealisticAmazonDeal() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B08R93TVRG",
                        "title": "Fritadeira Philco Air Fryer Oven 12L PFR2200P - 127V",
                        "link": "/Fritadeira-Oven-PFR2200P-Philco-127v/dp/B08R93TVRG",
                        "price": {
                          "priceToPay": {
                            "price": "399.0"
                          },
                          "basisPrice": {
                            "price": "1199.9"
                          }
                        },
                        "dealDetails": {
                          "percentClaimed": 91.0
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());

        ParsedDeal deal = deals.getFirst();

        assertEquals("B08R93TVRG", deal.asin());
        assertEquals(
                "https://www.amazon.com.br/Fritadeira-Oven-PFR2200P-Philco-127v/dp/B08R93TVRG",
                deal.productUrl()
        );
        assertEquals(
                "Fritadeira Philco Air Fryer Oven 12L PFR2200P - 127V",
                deal.title()
        );
        assertEquals(new BigDecimal("399.0"), deal.currentPrice());
        assertEquals(new BigDecimal("1199.9"), deal.basisPrice());
        assertEquals(new BigDecimal("91.0"), deal.soldPercentage());
    }

    @Test
    void shouldParseMultipleProducts() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B087WLJH8Y",
                        "title": "Produto A",
                        "link": "/produto-a/dp/B087WLJH8Y",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      },
                      {
                        "asin": "B08R93TVRG",
                        "title": "Produto B",
                        "link": "/produto-b/dp/B08R93TVRG",
                        "price": {
                          "priceToPay": {
                            "price": "20.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(2, deals.size());

        assertEquals("B087WLJH8Y", deals.get(0).asin());
        assertEquals("B08R93TVRG", deals.get(1).asin());
    }

    @Test
    void shouldParseCommaDecimalPrice() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B087WLJH8Y",
                        "title": "Produto",
                        "link": "/produto/dp/B087WLJH8Y",
                        "price": {
                          "priceToPay": {
                            "price": "15,99"
                          },
                          "basisPrice": {
                            "price": "53,46"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());

        ParsedDeal deal = deals.getFirst();

        assertEquals(new BigDecimal("15.99"), deal.currentPrice());
        assertEquals(new BigDecimal("53.46"), deal.basisPrice());
    }

    @Test
    void shouldThrowWhenProductSearchResponseIsMissing() {
        String content = """
                {
                  "otherData": {
                    "products": []
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        assertThrows(
                AmazonDealsParsingException.class,
                () -> parser.parse(result)
        );
    }

    @Test
    void shouldThrowWhenProductsArrayIsMissing() {
        String content = """
                {
                  "productSearchResponse": {
                    "otherData": []
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        assertThrows(
                AmazonDealsParsingException.class,
                () -> parser.parse(result)
        );
    }

    @Test
    void shouldDiscardProductWithoutAsin() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "title": "Produto sem ASIN",
                        "link": "/Produto/dp/B000000000",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(0, deals.size());
    }

    @Test
    void shouldDiscardProductWithInvalidAsin() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "INVALIDO",
                        "title": "Produto",
                        "link": "/produto/dp/INVALIDO",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(0, deals.size());
    }

    @Test
    void shouldDiscardProductWithoutTitle() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B012345678",
                        "link": "/produto/dp/B012345678",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(0, deals.size());
    }

    @Test
    void shouldDiscardProductWithoutProductUrl() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B012345678",
                        "title": "Produto",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(0, deals.size());
    }

    @Test
    void shouldDiscardProductWithoutCurrentPrice() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "B012345678",
                        "title": "Produto",
                        "link": "/produto/dp/B012345678"
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(0, deals.size());
    }

    @Test
    void shouldAllowMissingSoldPercentage() {
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
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());
        assertNull(deals.getFirst().soldPercentage());
    }

    @Test
    void shouldSetSoldPercentageToNullWhenSourceValueIsInvalid() {
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
                          "percentClaimed": "nao-disponivel"
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());
        assertNull(deals.getFirst().soldPercentage());
    }

    @Test
    void shouldKeepBasisPriceSeparateFromPreviousPrice() {
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
                          },
                          "basisPrice": {
                            "price": "20.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());

        ParsedDeal deal = deals.getFirst();

        assertEquals(new BigDecimal("10.00"), deal.currentPrice());
        assertEquals(new BigDecimal("20.00"), deal.basisPrice());

        /*
         * A fonte não forneceu explicitamente um preço anterior.
         * Portanto, o parser não deve inferir previousPrice a partir
         * de basisPrice.
         */
        assertNull(deal.previousPrice());
    }

    @Test
    void shouldDeduplicateEquivalentProducts() {
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
                        }
                      },
                      {
                        "asin": "B012345678",
                        "title": "Produto",
                        "link": "/produto/dp/B012345678",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());
        assertEquals("B012345678", deals.getFirst().asin());
    }

    @Test
    void shouldKeepDifferentOffersForSameAsinWhenOfferContextChanges() {
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
                        }
                      },
                      {
                        "asin": "B012345678",
                        "title": "Produto",
                        "link": "/produto/dp/B012345678",
                        "price": {
                          "priceToPay": {
                            "price": "12.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(2, deals.size());

        assertEquals(new BigDecimal("10.00"), deals.get(0).currentPrice());
        assertEquals(new BigDecimal("12.00"), deals.get(1).currentPrice());
    }

    @Test
    void shouldAcceptLowercaseAsinAndNormalizeIt() {
        String content = """
                {
                  "productSearchResponse": {
                    "products": [
                      {
                        "asin": "b012345678",
                        "title": "Produto",
                        "link": "/produto/dp/b012345678",
                        "price": {
                          "priceToPay": {
                            "price": "10.00"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());
        assertEquals("B012345678", deals.getFirst().asin());
    }

    @Test
    void shouldUseLowResolutionImageWhenHighResolutionImageIsUnavailable() {
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
                        "image": {
                          "lowRes": {
                            "baseUrl": "https://m.media-amazon.com/images/I/example",
                            "extension": "jpg"
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        CollectionResult result = collectionResult(content);

        List<ParsedDeal> deals = parser.parse(result);

        assertEquals(1, deals.size());

        assertEquals(
                "https://m.media-amazon.com/images/I/example.jpg",
                deals.getFirst().imageUrl()
        );
    }

    /**
     * Cria o contrato de entrada usado pelos testes do parser.
     */
    private CollectionResult collectionResult(String content) {
        return new CollectionResult(
                content,
                COLLECTED_AT,
                SOURCE
        );
    }
}