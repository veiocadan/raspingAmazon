package com.raspingamazon.domain.publication;

import com.raspingamazon.domain.evaluation.DealEvaluation;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa uma publicação gerada a partir de uma avaliação de oferta.
 *
 * <p>A Publication pertence ao domínio e, portanto, não conhece:
 * banco de dados, SQL, filas, HTTP, WhatsApp, Telegram ou qualquer
 * provedor externo.</p>
 *
 * <p>O ciclo de vida da publicação é controlado aqui para evitar
 * transições inválidas.</p>
 */
public final class Publication {

    private final Long id;
    private final DealEvaluation dealEvaluation;
    private final String templateVersion;
    private final String generatedText;
    private final String affiliateUrl;
    private PublicationStatus status;
    private final OffsetDateTime createdAt;

    public Publication(
            Long id,
            DealEvaluation dealEvaluation,
            String templateVersion,
            String generatedText,
            String affiliateUrl,
            PublicationStatus status,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.dealEvaluation = Objects.requireNonNull(
                dealEvaluation,
                "Deal evaluation must not be null"
        );
        this.templateVersion = requireText(
                templateVersion,
                "Template version"
        );
        this.generatedText = requireText(
                generatedText,
                "Generated text"
        );
        this.affiliateUrl = affiliateUrl;
        this.status = Objects.requireNonNull(
                status,
                "Publication status must not be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Created at must not be null"
        );
    }

    public Long id() {
        return id;
    }

    public DealEvaluation dealEvaluation() {
        return dealEvaluation;
    }

    public String templateVersion() {
        return templateVersion;
    }

    public String generatedText() {
        return generatedText;
    }

    public String affiliateUrl() {
        return affiliateUrl;
    }

    public PublicationStatus status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    /**
     * Avança uma publicação CREATED para READY.
     *
     * <p>READY representa o ponto em que a publicação está liberada
     * para o fluxo posterior de publicação.</p>
     */
    public void markReady() {
        requireCurrentStatus(PublicationStatus.CREATED);

        this.status = PublicationStatus.READY;
    }

    /**
     * Marca uma publicação READY como publicada com sucesso.
     */
    public void markPublished() {
        requireCurrentStatus(PublicationStatus.READY);

        this.status = PublicationStatus.PUBLISHED;
    }

    /**
     * Marca uma publicação READY como falha.
     *
     * <p>A causa detalhada da falha não pertence ao estado da Publication.
     * Posteriormente ela será registrada no mecanismo de tentativas.</p>
     */
    public void markFailed() {
        requireCurrentStatus(PublicationStatus.READY);

        this.status = PublicationStatus.FAILED;
    }

    /**
     * Permite que uma publicação FAILED retorne ao fluxo de publicação.
     *
     * <p>A decisão sobre quando realizar retry pertence a uma camada
     * posterior. O domínio apenas controla que a transição seja válida.</p>
     */
    public void retry() {
        requireCurrentStatus(PublicationStatus.FAILED);

        this.status = PublicationStatus.READY;
    }

    /**
     * Garante que uma transição só seja executada a partir do estado
     * esperado.
     */
    private void requireCurrentStatus(PublicationStatus expectedStatus) {
        if (this.status != expectedStatus) {
            throw new IllegalStateException(
                    "Invalid publication transition from "
                            + this.status
                            + ", expected "
                            + expectedStatus
            );
        }
    }

    /**
     * Validação estrutural compartilhada para textos obrigatórios.
     */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value;
    }
}