package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Fábrica responsável pela composição da coleta de promoções da Amazon.
 *
 * <p>Esta classe concentra a montagem das implementações concretas
 * necessárias para executar uma coleta real:</p>
 *
 * <ul>
 *     <li>{@link JavaHttpTransport}: transporte HTTP;</li>
 *     <li>{@link HttpCollectionCollector}: adaptação do transporte
 *     ao contrato de coleta;</li>
 *     <li>{@link AmazonDealsCollector}: adaptação específica da
 *     fonte de promoções da Amazon Brasil.</li>
 * </ul>
 *
 * <p>A fábrica não interpreta o conteúdo coletado. HTML, JSON, ASIN,
 * preços, percentual vendido e demais informações pertencem às etapas
 * posteriores do projeto.</p>
 *
 * <p>A composição também mantém as dependências concretas fora do
 * domínio e dos contratos da aplicação.</p>
 */
public final class AmazonDealsCollectorFactory {

    /**
     * Timeout padrão utilizado pela composição HTTP.
     *
     * <p>O timeout permanece encapsulado na infraestrutura e não
     * interfere no contrato da coleta. A configuração operacional
     * detalhada será tratada conforme a evolução da configuração
     * do projeto.</p>
     */
    private static final Duration DEFAULT_REQUEST_TIMEOUT =
            Duration.ofSeconds(30);

    /**
     * Construtor privado porque esta classe fornece apenas uma
     * operação estática de composição.
     */
    private AmazonDealsCollectorFactory() {
        // Impede instanciação acidental da fábrica.
    }

    /**
     * Cria um coletor funcional da página de promoções da Amazon Brasil.
     *
     * <p>A cadeia de dependências é construída nesta ordem:</p>
     *
     * <pre>
     * JavaHttpTransport
     *        ↓
     * HttpCollectionCollector
     *        ↓
     * AmazonDealsCollector
     * </pre>
     *
     * <p>O {@link HttpClient} utilizado pelo transporte pertence à
     * infraestrutura Java e não é exposto para as demais camadas.</p>
     *
     * @return coletor configurado para a fonte de promoções da Amazon
     */
    public static AmazonDealsCollector create() {

        /*
         * O cliente HTTP é criado aqui, na composição da infraestrutura.
         * As camadas superiores não precisam conhecer sua implementação.
         */
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(
                        HttpClient.Redirect.NORMAL
                )
                .build();

        /*
         * O transporte conhece somente HTTP.
         * Ele não conhece Amazon nem o significado do conteúdo.
         */
        var httpTransport = new JavaHttpTransport(
                httpClient,
                DEFAULT_REQUEST_TIMEOUT
        );

        /*
         * O collector HTTP transforma a resposta de transporte
         * no contrato CollectionResult.
         */
        CollectionCollector collectionCollector =
                new HttpCollectionCollector(
                        httpTransport,
                        Clock.systemUTC()
                );

        /*
         * Finalmente, o adaptador Amazon define a fonte funcional
         * de promoções e delega a coleta para o collector genérico.
         */
        return new AmazonDealsCollector(
                collectionCollector
        );
    }
}