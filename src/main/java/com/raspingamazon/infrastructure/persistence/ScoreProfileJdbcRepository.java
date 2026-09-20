package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.scoring.ScoreProfileProvider;
import com.raspingamazon.domain.scoring.ScoreProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de ScoreProfile.
 *
 * <p>O repository lê do PostgreSQL o perfil de score atualmente
 * ativo e reconstrói o objeto de domínio correspondente.</p>
 *
 * <p>Não existem pesos, versões ou parâmetros padrão neste
 * adaptador. Toda configuração operacional deve vir da tabela
 * score_profile.</p>
 */
public final class ScoreProfileJdbcRepository
    implements ScoreProfileProvider {

    private final Connection connection;

    public ScoreProfileJdbcRepository(
        Connection connection
    ) {
        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    /**
     * Carrega o único ScoreProfile ativo.
     *
     * <p>A constraint do banco já impede mais de um perfil ativo,
     * mas a camada de infraestrutura também protege explicitamente
     * o contrato caso o schema seja alterado incorretamente no futuro.</p>
     */
    @Override
    public ScoreProfile activeProfile() {

        String sql = """
                SELECT
                    version,
                    sold_percentage_weight,
                    cash_discount_weight,
                    rating_weight,
                    review_count_weight,
                    review_count_full_score_threshold
                FROM score_profile
                WHERE active = true
                """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );

             ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {
                throw new IllegalStateException(
                    "No active score profile was found"
                );
            }

            ScoreProfile profile =
                mapScoreProfile(
                    resultSet
                );

            /*
             * O índice único parcial deve impedir esta situação.
             *
             * Ainda assim, o repository mantém defesa adicional
             * para preservar o contrato da porta.
             */
            if (resultSet.next()) {
                throw new IllegalStateException(
                    "More than one active score profile was found"
                );
            }

            return profile;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load active score profile",
                exception
            );
        }
    }

    /**
     * Reconstrói o ScoreProfile utilizando exclusivamente os
     * valores persistidos.
     *
     * <p>As invariantes finais continuam sendo validadas pelo
     * próprio objeto de domínio.</p>
     */
    private ScoreProfile mapScoreProfile(
        ResultSet resultSet
    ) throws SQLException {

        return new ScoreProfile(
            resultSet.getString(
                "version"
            ),
            resultSet.getBigDecimal(
                "sold_percentage_weight"
            ),
            resultSet.getBigDecimal(
                "cash_discount_weight"
            ),
            resultSet.getBigDecimal(
                "rating_weight"
            ),
            resultSet.getBigDecimal(
                "review_count_weight"
            ),
            resultSet.getLong(
                "review_count_full_score_threshold"
            )
        );
    }
}
