package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Implementação do coletor baseado em HTTP.
 *
 * <p>Esta classe adapta o transporte HTTP ao contrato de coleta
 * definido pela aplicação.</p>
 *
 * <p>A classe permanece deliberadamente genérica: não conhece Amazon,
 * HTML, JSON, ASIN, ofertas ou regras de negócio. Sua responsabilidade
 * termina ao transformar uma resposta HTTP válida em
 * {@link CollectionResult}.</p>
 *
 * <p>Respostas HTTP que representam falha da fonte não são consideradas
 * coletas válidas. Nesses casos, a classe converte o status HTTP em
 * {@link CollectionException}, permitindo que a falha seja tratada
 * pela camada superior sem entregar conteúdo potencialmente inválido
 * para as etapas seguintes.</p>
 */
public final class HttpCollectionCollector implements CollectionCollector {

    private final HttpTransport httpTransport;
    private final Clock clock;

    /**
     * Cria um collector HTTP.
     *
     * @param httpTransport transporte responsável pela comunicação HTTP
     * @param clock relógio utilizado para registrar o instante da coleta
     */
    public HttpCollectionCollector(
            HttpTransport httpTransport,
            Clock clock
    ) {
        if (httpTransport == null) {
            throw new NullPointerException(
                    "HTTP transport must not be null"
            );
        }

        if (clock == null) {
            throw new NullPointerException(
                    "Clock must not be null"
            );
        }

        this.httpTransport = httpTransport;
        this.clock = clock;
    }

    /**
     * Executa uma coleta HTTP.
     *
     * <p>A requisição é encaminhada ao transporte e a resposta é
     * transformada em {@link CollectionResult} somente quando seu
     * status HTTP representa sucesso.</p>
     *
     * @param request requisição contendo a fonte da coleta
     * @return resultado bruto da coleta
     * @throws CollectionException quando o transporte falha ou quando
     *                             a resposta HTTP representa erro
     */
    @Override
    public CollectionResult collect(
            CollectionRequest request
    ) {
        if (request == null) {
            throw new NullPointerException(
                    "Collection request must not be null"
            );
        }

        try {
            HttpTransportResponse response =
                    httpTransport.get(request.source());

            /*
             * Uma resposta fora da faixa 2xx não é considerada uma
             * coleta válida.
             *
             * O conteúdo da resposta de erro não deve chegar às
             * próximas etapas como se fosse conteúdo coletado com
             * sucesso.
             */
            if (!isSuccessful(response.statusCode())) {
                throw new CollectionException(
                        "HTTP response status indicates collection failure: "
                                + response.statusCode()
                );
            }

            /*
             * Somente depois da validação do status HTTP o conteúdo
             * é transformado no contrato CollectionResult.
             */
            return new CollectionResult(
                    response.body(),
                    OffsetDateTime.now(clock),
                    request.source().toString()
            );

        } catch (CollectionException exception) {
            /*
             * Exceções que já pertencem ao contrato de coleta devem
             * atravessar esta camada sem serem encapsuladas novamente.
             */
            throw exception;

        } catch (Exception exception) {
            /*
             * Qualquer falha inesperada na adaptação HTTP é convertida
             * para a exceção operacional padronizada da coleta.
             */
            throw new CollectionException(
                    "Collection failed",
                    exception
            );
        }
    }

    /**
     * Verifica se um status HTTP representa uma resposta de sucesso.
     *
     * <p>Todo status da faixa 200–299 é considerado sucesso. Outros
     * códigos, incluindo redirecionamentos, erros de cliente e erros
     * de servidor, não são aceitos por esta camada como resposta final
     * válida.</p>
     *
     * @param statusCode código HTTP retornado pelo transporte
     * @return {@code true} quando o código pertence à faixa 2xx
     */
    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}