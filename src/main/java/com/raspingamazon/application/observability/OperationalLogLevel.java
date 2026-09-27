package com.raspingamazon.application.observability;

/**
 * Nível de severidade de um evento operacional estruturado.
 *
 * <p>O enum pertence à aplicação para que produtores de eventos
 * não dependam de uma biblioteca concreta de logging.</p>
 */
public enum OperationalLogLevel {

    DEBUG,

    INFO,

    WARN,

    ERROR
}
