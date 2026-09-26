package com.raspingamazon.testsupport.database;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma classe como teste de integração dependente de PostgreSQL.
 *
 * <p>Além da classificação semântica, a anotação garante que
 * as migrations da aplicação sejam aplicadas antes da classe
 * de teste ser executada.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(PostgresSchemaExtension.class)
public @interface PostgresIntegrationTest {
}
