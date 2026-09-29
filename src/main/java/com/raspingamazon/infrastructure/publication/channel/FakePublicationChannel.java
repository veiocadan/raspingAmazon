package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adapter determinístico de PublicationChannel utilizado para
 * testes de integração e validação do fluxo de publicação.
 *
 * <p>Este adapter não acessa rede, Telegram, WhatsApp ou qualquer
 * provider externo.</p>
 *
 * <p>O resultado devolvido é definido no construtor. Isso permite
 * exercitar deterministicamente:</p>
 *
 * <ul>
 *     <li>SUCCESS;</li>
 *     <li>FAILED_TRANSIENT;</li>
 *     <li>FAILED_PERMANENT.</li>
 * </ul>
 *
 * <p>Cada comando recebido é registrado em memória na ordem em que
 * foi publicado. O registro existe exclusivamente para permitir
 * verificar o comportamento do fluxo durante testes.</p>
 *
 * <p>CopyOnWriteArrayList é utilizada para que o fake continue
 * seguro caso testes posteriores executem workers concorrentes.</p>
 */
public final class FakePublicationChannel
    implements PublicationChannel {

    private final PublicationResult configuredResult;

    private final CopyOnWriteArrayList<PublicationCommand>
        publishedCommands =
        new CopyOnWriteArrayList<>();

    public FakePublicationChannel(
        PublicationResult configuredResult
    ) {

        this.configuredResult =
            Objects.requireNonNull(
                configuredResult,
                "configuredResult must not be null"
            );
    }

    @Override
    public PublicationResult publish(
        PublicationCommand command
    ) {

        PublicationCommand validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        publishedCommands.add(
            validatedCommand
        );

        return configuredResult;
    }

    /**
     * Retorna todos os comandos recebidos pelo fake,
     * preservando a ordem de publicação.
     */
    public List<PublicationCommand> publishedCommands() {

        return List.copyOf(
            publishedCommands
        );
    }

    /**
     * Retorna quantas chamadas de publish foram concluídas
     * pelo adapter.
     */
    public int callCount() {

        return publishedCommands.size();
    }

    /**
     * Retorna o último comando recebido, quando existir.
     */
    public Optional<PublicationCommand> lastCommand() {

        if (publishedCommands.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
            publishedCommands.getLast()
        );
    }

    /**
     * Resultado fixo configurado para este fake.
     */
    public PublicationResult configuredResult() {

        return configuredResult;
    }
}
