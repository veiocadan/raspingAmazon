package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import com.raspingamazon.application.observability.OperationalLogContext;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fronteira específica da coleta HTML de promoções da Amazon Brasil.
 *
 * <p>O transporte HTTP permanece delegado a um CollectionCollector
 * genérico. Esta classe acrescenta somente semântica própria da fonte
 * Amazon.</p>
 *
 * <p>Responsabilidades:</p>
 *
 * <ul>
 *     <li>preservar a fonte histórica de /deals;</li>
 *     <li>delegar a aquisição ao collector configurado;</li>
 *     <li>preservar correlação operacional quando fornecida;</li>
 *     <li>detectar páginas de challenge, CAPTCHA ou bloqueio;</li>
 *     <li>interromper a coleta quando uma proteção for encontrada.</li>
 * </ul>
 *
 * <p>Esta classe não tenta resolver CAPTCHA, não executa challenge,
 * não modifica fingerprint, não rotaciona identidade e não contém
 * qualquer mecanismo destinado a contornar proteções da fonte.</p>
 *
 * <p>Parsing de ofertas continua pertencendo ao DealsParser.</p>
 */
public final class AmazonDealsCollector
    implements CollectionCollector {

    /**
     * Fonte funcional histórica das promoções Amazon Brasil.
     */
    private static final URI DEALS_SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    /*
     * Marcadores conservadores.
     *
     * Evitamos termos excessivamente genéricos como apenas "captcha"
     * ou "robot", pois eles poderiam existir legitimamente dentro de
     * scripts ou conteúdo normal.
     */
    private static final String CAPTCHA_FORM_MARKER =
        "/errors/validatecaptcha";

    private static final String CAPTCHA_INSTRUCTION_MARKER =
        "enter the characters you see below";

    private static final String CAPTCHA_IMAGE_INSTRUCTION_MARKER =
        "type the characters you see in this image";

    private static final String ROBOT_CHALLENGE_MARKER =
        "sorry, we just need to make sure you're not a robot";

    private static final String ROBOT_CHECK_TITLE_MARKER =
        "<title>robot check</title>";

    private static final String AUTOMATED_ACCESS_BLOCK_MARKER =
        "automated access to amazon data";

    private final CollectionCollector collectionCollector;

    public AmazonDealsCollector(
        CollectionCollector collectionCollector
    ) {

        this.collectionCollector =
            Objects.requireNonNull(
                collectionCollector,
                "Collection collector must not be null"
            );
    }

    /**
     * Conveniência histórica para coleta da página /deals.
     *
     * <p>Novos fluxos orquestrados podem utilizar diretamente o
     * contrato CollectionCollector e fornecer a URI persistida na
     * ProcessingRun.</p>
     */
    public CollectionResult collect() {

        return collect(
            new CollectionRequest(
                DEALS_SOURCE
            )
        );
    }

    /**
     * Executa uma coleta através da fronteira Amazon.
     *
     * <p>A URI recebida não é substituída. Isso permite que o
     * ProcessingRun continue sendo a fonte da identidade da coleta no
     * fluxo orquestrado.</p>
     */
    @Override
    public CollectionResult collect(
        CollectionRequest request
    ) {

        Objects.requireNonNull(
            request,
            "Collection request must not be null"
        );

        return executeAndValidate(
            () -> collectionCollector.collect(
                request
            )
        );
    }

    /**
     * Variante contextual utilizada pela orquestração observável.
     *
     * <p>A correlação é repassada integralmente ao collector delegado.
     * A fronteira Amazon não inventa runId, jobId, ASIN ou qualquer
     * outra identidade.</p>
     */
    @Override
    public CollectionResult collect(
        CollectionRequest request,
        OperationalLogContext context
    ) {

        Objects.requireNonNull(
            request,
            "Collection request must not be null"
        );

        Objects.requireNonNull(
            context,
            "Operational log context must not be null"
        );

        return executeAndValidate(
            () -> collectionCollector.collect(
                request,
                context
            )
        );
    }

    private CollectionResult executeAndValidate(
        Supplier<CollectionResult> collection
    ) {

        try {

            CollectionResult result =
                Objects.requireNonNull(
                    collection.get(),
                    "Collection collector must not return null"
                );

            SourceRestrictionType restriction =
                detectRestriction(
                    result.content()
                );

            if (restriction != null) {

                throw new SourceRestrictionException(
                    restriction
                );
            }

            return result;

        } catch (CollectionException exception) {

            /*
             * CollectionException já representa o contrato funcional
             * correto, inclusive:
             *
             * - HTTP 403;
             * - HTTP 429;
             * - timeout;
             * - SourceRestrictionException.
             *
             * Não substituímos a exceção nem perdemos seus metadados.
             */
            throw exception;

        } catch (Exception exception) {

            throw new CollectionException(
                "Amazon deals collection failed",
                exception
            );
        }
    }

    /**
     * Detecta apenas evidências explícitas conhecidas de proteção.
     *
     * <p>A função é propositalmente conservadora: uma palavra isolada
     * como "robot" ou "captcha" não é suficiente.</p>
     *
     * <p>Whitespace é normalizado antes das comparações porque HTML
     * equivalente pode distribuir uma mesma mensagem em múltiplas
     * linhas, tabs ou grupos de espaços.</p>
     */
    private SourceRestrictionType detectRestriction(
        String content
    ) {

        String normalized =
            normalizeContent(
                content
            );

        /*
         * CAPTCHA é testado antes de challenge porque algumas páginas
         * de CAPTCHA também podem possuir linguagem relacionada a
         * verificação de robô.
         */
        if (normalized.contains(
            CAPTCHA_FORM_MARKER
        )
            || normalized.contains(
            CAPTCHA_INSTRUCTION_MARKER
        )
            || normalized.contains(
            CAPTCHA_IMAGE_INSTRUCTION_MARKER
        )) {

            return SourceRestrictionType.CAPTCHA;
        }

        if (normalized.contains(
            ROBOT_CHALLENGE_MARKER
        )
            || normalized.contains(
            ROBOT_CHECK_TITLE_MARKER
        )) {

            return SourceRestrictionType.CHALLENGE;
        }

        if (normalized.contains(
            AUTOMATED_ACCESS_BLOCK_MARKER
        )) {

            return SourceRestrictionType.BLOCKED;
        }

        return null;
    }

    /**
     * Normaliza somente características irrelevantes para os marcadores
     * textuais utilizados nesta fronteira.
     *
     * <p>Não existe parsing, interpretação de DOM ou transformação do
     * conteúdo funcional. O resultado serve exclusivamente para
     * detecção defensiva de restrições da fonte.</p>
     */
    private String normalizeContent(
        String content
    ) {

        Objects.requireNonNull(
            content,
            "Collection content must not be null"
        );

        return content
            .toLowerCase(
                Locale.ROOT
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }
}
