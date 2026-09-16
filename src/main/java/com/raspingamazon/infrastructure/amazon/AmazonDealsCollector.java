package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;

import java.net.URI;

/**
 * Adaptador responsável pela definição da fonte funcional de promoções
 * da Amazon Brasil.
 *
 * <p>Esta classe representa a fronteira específica da Amazon dentro
 * da infraestrutura. Ela não interpreta HTML, não extrai ASIN, não
 * aplica regras de negócio e não decide se uma oferta é elegível.</p>
 *
 * <p>A responsabilidade deste adaptador é garantir que a coleta destinada
 * à página de promoções utilize a fonte definida pelo projeto e delegar
 * a execução efetiva ao coletor HTTP genérico.</p>
 *
 * <p>O parsing e a normalização permanecem fora desta classe e serão
 * tratados na FASE 6.</p>
 */
public final class AmazonDealsCollector {

    /**
     * Fonte funcional de promoções definida durante a investigação
     * da FASE 0.
     */
    private static final URI DEALS_SOURCE =
            URI.create("https://www.amazon.com.br/deals");

    private final CollectionCollector collectionCollector;

    /**
     * Cria o adaptador da página de promoções da Amazon Brasil.
     *
     * @param collectionCollector coletor genérico responsável pela
     *                            execução da coleta
     */
    public AmazonDealsCollector(CollectionCollector collectionCollector) {
        if (collectionCollector == null) {
            throw new NullPointerException(
                    "Collection collector must not be null"
            );
        }

        this.collectionCollector = collectionCollector;
    }

    /**
     * Executa a coleta da página de promoções da Amazon Brasil.
     *
     * <p>A URL é definida exclusivamente neste adaptador. O restante
     * da aplicação não precisa conhecer a localização concreta da
     * fonte Amazon.</p>
     *
     * @return resultado bruto da coleta
     */
    public CollectionResult collect() {
        try {
            return collectionCollector.collect(
                    new CollectionRequest(DEALS_SOURCE)
            );
        } catch (CollectionException exception) {
            /*
             * A exceção de coleta já possui significado operacional
             * suficiente e deve atravessar este adaptador sem ser
             * substituída por outra exceção.
             */
            throw exception;
        } catch (Exception exception) {
            /*
             * Uma falha inesperada na fronteira da Amazon deve ser
             * convertida para o contrato de erro da coleta.
             */
            throw new CollectionException(
                    "Amazon deals collection failed",
                    exception
            );
        }
    }
}