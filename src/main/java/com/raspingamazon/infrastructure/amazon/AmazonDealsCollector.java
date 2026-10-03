package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import com.raspingamazon.application.observability.OperationalLogContext;

import java.net.URI;
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

    private final CollectionCollector collectionCollector;

    private final AmazonSourceRestrictionDetector
        restrictionDetector;

    public AmazonDealsCollector(
        CollectionCollector collectionCollector
    ) {

        this(
            collectionCollector,
            new AmazonSourceRestrictionDetector()
        );
    }

    AmazonDealsCollector(
        CollectionCollector collectionCollector,
        AmazonSourceRestrictionDetector restrictionDetector
    ) {

        this.collectionCollector =
            Objects.requireNonNull(
                collectionCollector,
                "Collection collector must not be null"
            );

        this.restrictionDetector =
            Objects.requireNonNull(
                restrictionDetector,
                "restrictionDetector must not be null"
            );
    }

    /**
     * Conveniência histórica para coleta da página /deals.
     */
    public CollectionResult collect() {

        return collect(
            new CollectionRequest(
                DEALS_SOURCE
            )
        );
    }

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
                restrictionDetector.detect(
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
             * correto, incluindo:
             *
             * - falha HTTP;
             * - timeout;
             * - SourceRestrictionException.
             */
            throw exception;

        } catch (Exception exception) {

            throw new CollectionException(
                "Amazon deals collection failed",
                exception
            );
        }
    }
}
