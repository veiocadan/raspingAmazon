package com.raspingamazon.infrastructure.amazon.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementação do parser das ofertas da Amazon Brasil.
 *
 * <p>A página de Deals observada durante a investigação não contém um
 * documento JSON puro. Ela contém uma estrutura de dados incorporada ao
 * conteúdo da página, dentro da qual existe o objeto
 * {@code productSearchResponse} e seu array {@code products}.</p>
 *
 * <p>Este parser é responsável somente por:</p>
 *
 * <ul>
 *     <li>localizar a estrutura de ofertas;</li>
 *     <li>interpretar os dados estruturados;</li>
 *     <li>normalizar os campos necessários;</li>
 *     <li>descartar registros sem identificador confiável;</li>
 *     <li>evitar duplicação de registros equivalentes;</li>
 *     <li>produzir {@link ParsedDeal}.</li>
 * </ul>
 *
 * <p>Este parser NÃO:</p>
 *
 * <ul>
 *     <li>valida vendedor Amazon;</li>
 *     <li>valida entrega Amazon;</li>
 *     <li>calcula elegibilidade;</li>
 *     <li>aplica filtros;</li>
 *     <li>calcula score;</li>
 *     <li>persiste dados;</li>
 *     <li>gera publicação.</li>
 * </ul>
 */
public class AmazonDealsParser implements DealsParser {

    /**
     * Nome do campo que identifica a estrutura de resultados da página.
     */
    private static final String PRODUCT_SEARCH_RESPONSE_FIELD =
            "\"productSearchResponse\"";

    /**
     * Expressão usada para validar um ASIN depois da normalização.
     *
     * <p>O ASIN Amazon possui dez caracteres alfanuméricos em
     * maiúsculas.</p>
     */
    private static final String ASIN_REGEX = "[A-Z0-9]{10}";

    private final ObjectMapper objectMapper;

    /**
     * Cria um parser usando uma instância padrão do Jackson.
     */
    public AmazonDealsParser() {
        this(new ObjectMapper());
    }

    /**
     * Construtor que permite fornecer o ObjectMapper.
     *
     * <p>A injeção facilita testes e permite que a infraestrutura evolua
     * posteriormente para uma configuração centralizada do Jackson.</p>
     *
     * @param objectMapper ObjectMapper utilizado para interpretar JSON
     */
    public AmazonDealsParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "ObjectMapper must not be null"
        );
    }

    /**
     * Interpreta o conteúdo coletado e produz ofertas normalizadas.
     *
     * @param collectionResult conteúdo bruto da coleta
     * @return lista determinística de ofertas encontradas
     */
    @Override
    public List<ParsedDeal> parse(CollectionResult collectionResult) {
        Objects.requireNonNull(
                collectionResult,
                "Collection result must not be null"
        );

        String content = collectionResult.content();

        JsonNode productSearchResponse =
                extractProductSearchResponse(content);

        JsonNode products = productSearchResponse.get("products");

        if (products == null || !products.isArray()) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse does not contain a products array"
            );
        }

        List<ParsedDeal> parsedDeals = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (JsonNode product : products) {
            ParsedDeal parsedDeal = parseProduct(
                    product,
                    collectionResult.collectedAt(),
                    collectionResult.source()
            );

            /*
             * Um produto sem ASIN, título, URL ou preço principal não
             * representa uma oferta suficientemente confiável para o
             * contrato ParsedDeal.
             *
             * A decisão é descartar o registro, e não criar um objeto
             * parcialmente inválido.
             */
            if (parsedDeal == null) {
                continue;
            }

            /*
             * O ParsedDeal atual não possui um identificador específico
             * para contexto de oferta. Por isso, a deduplicação usa os
             * campos que identificam a mesma representação comercial
             * encontrada no mural.
             */
            String deduplicationKey = buildDeduplicationKey(parsedDeal);

            if (seenKeys.add(deduplicationKey)) {
                parsedDeals.add(parsedDeal);
            }
        }

        return List.copyOf(parsedDeals);
    }

    /**
     * Localiza o objeto productSearchResponse dentro do conteúdo bruto.
     *
     * <p>Não usamos uma regex para interpretar o JSON. Primeiro
     * encontramos o campo estrutural e depois extraímos o objeto JSON
     * completo respeitando strings e níveis de chaves.</p>
     *
     * @param content conteúdo bruto coletado
     * @return objeto JSON productSearchResponse
     */
    private JsonNode extractProductSearchResponse(String content) {
        int fieldPosition = content.indexOf(
                PRODUCT_SEARCH_RESPONSE_FIELD
        );

        if (fieldPosition < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse was not found"
            );
        }

        int colonPosition = content.indexOf(
                ':',
                fieldPosition + PRODUCT_SEARCH_RESPONSE_FIELD.length()
        );

        if (colonPosition < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse does not contain a value"
            );
        }

        int objectStart = findNextNonWhitespaceCharacter(
                content,
                colonPosition + 1
        );

        if (objectStart < 0 || content.charAt(objectStart) != '{') {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse is not a JSON object"
            );
        }

        int objectEnd = findJsonObjectEnd(
                content,
                objectStart
        );

        if (objectEnd < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse JSON object is incomplete"
            );
        }

        String json = content.substring(
                objectStart,
                objectEnd + 1
        );

        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse could not be parsed as JSON",
                    exception
            );
        }
    }

    /**
     * Encontra o primeiro caractere não branco depois de uma posição.
     *
     * @param content conteúdo analisado
     * @param start posição inicial
     * @return posição encontrada ou -1
     */
    private int findNextNonWhitespaceCharacter(
            String content,
            int start
    ) {
        for (int index = start; index < content.length(); index++) {
            if (!Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Localiza o fechamento de um objeto JSON respeitando strings.
     *
     * <p>Isso é necessário porque o conteúdo coletado não é JSON puro.
     * Uma simples busca pelo próximo {@code }} poderia terminar dentro
     * de uma string ou em um objeto interno.</p>
     *
     * @param content conteúdo analisado
     * @param objectStart posição da chave {@code \{}
     * @return posição da chave {@code \}} correspondente ou -1
     */
    private int findJsonObjectEnd(
            String content,
            int objectStart
    ) {
        int depth = 0;
        boolean insideString = false;
        boolean escaped = false;

        for (int index = objectStart; index < content.length(); index++) {
            char current = content.charAt(index);

            if (insideString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }

                if (current == '\\') {
                    escaped = true;
                    continue;
                }

                if (current == '"') {
                    insideString = false;
                }

                continue;
            }

            if (current == '"') {
                insideString = true;
                continue;
            }

            if (current == '{') {
                depth++;
                continue;
            }

            if (current == '}') {
                depth--;

                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    /**
     * Converte um elemento do array products em ParsedDeal.
     *
     * @param product nó JSON do produto
     * @param collectedAt instante da coleta
     * @param source origem da coleta
     * @return ParsedDeal válido ou null quando o registro deve ser descartado
     */
    private ParsedDeal parseProduct(
            JsonNode product,
            OffsetDateTime collectedAt,
            String source
    ) {
        if (product == null || !product.isObject()) {
            return null;
        }

        String asin = normalizeAsin(
                textValue(product, "asin")
        );

        if (asin == null) {
            return null;
        }

        String title = normalizeRequiredText(
                textValue(product, "title")
        );

        if (title == null) {
            return null;
        }

        String productUrl = normalizeProductUrl(
                textValue(product, "link"),
                source
        );

        if (productUrl == null) {
            return null;
        }

        BigDecimal currentPrice = extractPrice(
                product,
                "priceToPay"
        );

        if (currentPrice == null) {
            return null;
        }

        BigDecimal basisPrice = extractPrice(
                product,
                "basisPrice"
        );

        /*
         * Importante:
         *
         * basisPrice não é automaticamente previousPrice.
         *
         * A investigação da FASE 0 v2 estabeleceu que esses conceitos
         * possuem semânticas diferentes. Sem evidência de preço histórico,
         * previousPrice permanece null.
         */
        BigDecimal previousPrice = null;

        BigDecimal soldPercentage = extractSoldPercentage(
                product
        );

        String imageUrl = extractImageUrl(
                product
        );

        return new ParsedDeal(
                asin,
                productUrl,
                title,
                imageUrl,
                currentPrice,
                basisPrice,
                previousPrice,
                soldPercentage,
                collectedAt,
                source
        );
    }

    /**
     * Normaliza um ASIN.
     *
     * @param value valor bruto
     * @return ASIN válido ou null
     */
    private String normalizeAsin(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        if (!normalized.matches(ASIN_REGEX)) {
            return null;
        }

        return normalized;
    }

    /**
     * Normaliza um campo textual obrigatório.
     *
     * @param value valor bruto
     * @return texto normalizado ou null
     */
    private String normalizeRequiredText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    /**
     * Normaliza a URL do produto.
     *
     * <p>A Amazon fornece links relativos no mural, por exemplo:
     * {@code /Produto/dp/B087WLJH8Y}. O resultado normalizado utiliza a
     * origem da coleta para produzir uma URL absoluta.</p>
     *
     * @param link link bruto
     * @param source origem da coleta
     * @return URL normalizada ou null
     */
    private String normalizeProductUrl(
            String link,
            String source
    ) {
        if (link == null || link.isBlank()) {
            return null;
        }

        String normalizedLink = link.trim();

        try {
            URI linkUri = URI.create(normalizedLink);

            if (linkUri.isAbsolute()) {
                return linkUri.toString();
            }

            URI sourceUri = URI.create(source);

            return sourceUri
                    .resolve(normalizedLink)
                    .toString();

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Extrai um preço de dentro do objeto price.
     *
     * @param product produto JSON
     * @param priceField campo interno de preço
     * @return preço normalizado ou null
     */
    private BigDecimal extractPrice(
            JsonNode product,
            String priceField
    ) {
        JsonNode price = product.get("price");

        if (price == null || !price.isObject()) {
            return null;
        }

        JsonNode priceNode = price.get(priceField);

        if (priceNode == null || !priceNode.isObject()) {
            return null;
        }

        String value = textValue(
                priceNode,
                "price"
        );

        return parseDecimal(value);
    }

    /**
     * Extrai o percentual vendido de dealDetails.percentClaimed.
     *
     * <p>Essa é a fonte preferencial documentada na investigação da
     * FASE 0.</p>
     *
     * @param product produto JSON
     * @return percentual vendido ou null quando ausente/inválido
     */
    private BigDecimal extractSoldPercentage(JsonNode product) {
        JsonNode dealDetails = product.get("dealDetails");

        if (dealDetails == null || !dealDetails.isObject()) {
            return null;
        }

        JsonNode percentClaimed =
                dealDetails.get("percentClaimed");

        if (percentClaimed == null || percentClaimed.isNull()) {
            return null;
        }

        if (percentClaimed.isNumber()) {
            return percentClaimed.decimalValue();
        }

        return parseDecimal(
                percentClaimed.asText()
        );
    }

    /**
     * Extrai a imagem de alta resolução quando disponível.
     *
     * @param product produto JSON
     * @return URL da imagem ou null
     */
    private String extractImageUrl(JsonNode product) {
        JsonNode image = product.get("image");

        if (image == null || !image.isObject()) {
            return null;
        }

        JsonNode hiRes = image.get("hiRes");

        if (hiRes != null && hiRes.isObject()) {
            String baseUrl = textValue(
                    hiRes,
                    "baseUrl"
            );

            String extension = textValue(
                    hiRes,
                    "extension"
            );

            String url = composeImageUrl(
                    baseUrl,
                    extension
            );

            if (url != null) {
                return url;
            }
        }

        JsonNode lowRes = image.get("lowRes");

        if (lowRes != null && lowRes.isObject()) {
            String baseUrl = textValue(
                    lowRes,
                    "baseUrl"
            );

            String extension = textValue(
                    lowRes,
                    "extension"
            );

            return composeImageUrl(
                    baseUrl,
                    extension
            );
        }

        return null;
    }

    /**
     * Monta uma URL de imagem a partir da estrutura da Amazon.
     *
     * @param baseUrl base da imagem
     * @param extension extensão fornecida pela fonte
     * @return URL completa ou null
     */
    private String composeImageUrl(
            String baseUrl,
            String extension
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        if (extension == null || extension.isBlank()) {
            return baseUrl.trim();
        }

        String normalizedExtension = extension.trim();

        if (normalizedExtension.startsWith(".")) {
            return baseUrl.trim() + normalizedExtension;
        }

        return baseUrl.trim() + "." + normalizedExtension;
    }

    /**
     * Obtém um campo textual de um objeto JSON.
     *
     * @param node objeto JSON
     * @param field campo desejado
     * @return texto ou null
     */
    private String textValue(
            JsonNode node,
            String field
    ) {
        if (node == null || !node.isObject()) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    /**
     * Converte um valor textual em BigDecimal.
     *
     * <p>Os preços observados no JSON da Amazon utilizam ponto decimal.
     * A conversão também aceita vírgula para permitir normalização de
     * valores textuais localizados.</p>
     *
     * @param value valor bruto
     * @return BigDecimal ou null
     */
    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replace(',', '.');

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Constrói a chave usada para deduplicação.
     *
     * <p>O ASIN identifica o produto, enquanto URL e preço preservam
     * contexto suficiente para não colapsar cegamente representações
     * comerciais diferentes.</p>
     *
     * @param parsedDeal oferta já normalizada
     * @return chave determinística
     */
    private String buildDeduplicationKey(
            ParsedDeal parsedDeal
    ) {
        return String.join(
                "|",
                parsedDeal.asin(),
                parsedDeal.productUrl(),
                parsedDeal.currentPrice().toPlainString(),
                parsedDeal.basisPrice() == null
                        ? ""
                        : parsedDeal.basisPrice().toPlainString()
        );
    }
}