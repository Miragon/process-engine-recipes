package io.miragon.tasklist.order

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** The orders database in a container, the same PostgreSQL version as in the compose stack. */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainer {

    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:18.6")
}
