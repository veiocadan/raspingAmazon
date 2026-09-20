package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.shared.Percentage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de FilterProfile.
 *
 * O repository lê o perfil comercial atualmente ativo no PostgreSQL
 * e reconstrói o objeto de domínio.
 *
 * A tabela possui proteção estrutural para permitir no máximo um
 * perfil ativo. Mesmo assim, o repository valida também a ausência
 * de perfil ativo e converte falhas de acesso em erro explícito.
 */
public final class FilterProfileJdbcRepository
    implements FilterProfileProvider {

    private final Connection connection;

    public FilterProfileJdbcRepository(
        Connection connection
    ) {
        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    /**
     * Carrega o perfil comercial ativo.
     *
     * Não existem valores padrão no código.
     * Todos os limites são reconstruídos a partir da configuração
     * persistida.
     */
    @Override
    public FilterProfile activeProfile() {

        String sql = """
                SELECT
                    version,
                    min_cash_discount_percentage,
                    min_rating,
                    min_review_count
                FROM filter_profile
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
                    "No active commercial filter profile was found"
                );
            }

            FilterProfile profile =
                mapFilterProfile(
                    resultSet
                );

            /*
             * A restrição do banco já deve impedir duas linhas ativas.
             *
             * Esta verificação adicional protege o contrato caso o
             * schema seja alterado indevidamente no futuro.
             */
            if (resultSet.next()) {
                throw new IllegalStateException(
                    "More than one active commercial filter profile was found"
                );
            }

            return profile;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load active commercial filter profile",
                exception
            );
        }
    }

    /**
     * Converte os dados persistidos para o contrato de domínio.
     *
     * As invariantes finais ainda são validadas pelo próprio
     * FilterProfile e pelo value object Percentage.
     */
    private FilterProfile mapFilterProfile(
        ResultSet resultSet
    ) throws SQLException {

        return new FilterProfile(
            resultSet.getString(
                "version"
            ),

            new Percentage(
                resultSet.getBigDecimal(
                    "min_cash_discount_percentage"
                )
            ),

            resultSet.getBigDecimal(
                "min_rating"
            ),

            resultSet.getLong(
                "min_review_count"
            )
        );
    }
}
