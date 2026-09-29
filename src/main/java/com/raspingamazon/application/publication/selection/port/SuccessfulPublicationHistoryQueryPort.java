package com.raspingamazon.application.publication.selection.port;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;

import java.util.Map;
import java.util.Set;

/**
 * Porta de leitura do histórico de publicações externas
 * concluídas com sucesso.
 *
 * <p>A camada de aplicação conhece somente este contrato.
 * JDBC, SQL e a forma concreta de agregação pertencem à
 * infraestrutura.</p>
 *
 * <p>A consulta é deliberadamente feita em lote para evitar
 * uma consulta adicional por candidato durante a seleção
 * operacional.</p>
 *
 * <p>O histórico é sempre consultado dentro de um escopo
 * específico de:</p>
 *
 * <pre>
 * channel
 * +
 * destination
 * </pre>
 *
 * <p>Um ASIN ausente do mapa retornado significa que nenhuma
 * publicação bem-sucedida foi encontrada para aquele ASIN
 * no canal e destino consultados.</p>
 */
public interface SuccessfulPublicationHistoryQueryPort {

    /**
     * Carrega em uma única operação lógica o histórico de sucesso
     * dos ASINs informados para determinado canal e destino.
     *
     * @param asins ASINs candidatos à seleção operacional
     * @param channel identidade lógica do canal
     * @param destination destino dentro do canal
     * @return histórico existente indexado por ASIN;
     *         ASINs nunca publicados com sucesso não aparecem no mapa
     */
    Map<Asin, SuccessfulPublicationHistory> findSuccessfulByAsins(
        Set<Asin> asins,
        String channel,
        String destination
    );
}
