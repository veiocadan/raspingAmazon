package com.raspingamazon.application.collection.contract;

import java.net.URI;

/**
 * Abstrai o transporte HTTP utilizado durante uma coleta.
 *
 * <p>O restante da aplicação não precisa conhecer a implementação concreta
 * utilizada para realizar a requisição. Isso permite testar o Collector
 * sem depender de uma conexão real com a Amazon.</p>
 */
public interface HttpTransport {

    /**
     * Executa uma requisição HTTP GET para a URI informada.
     *
     * @param uri endereço absoluto do recurso que será coletado
     * @return resposta HTTP contendo status e corpo
     * @throws CollectionException quando ocorre uma falha técnica de transporte
     */
    HttpTransportResponse get(URI uri);
}