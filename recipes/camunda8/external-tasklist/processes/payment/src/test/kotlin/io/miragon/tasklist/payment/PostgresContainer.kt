package io.miragon.tasklist.payment

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** The PostgreSQL of the compose stack, started by Testcontainers. */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainer {

    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:18.6")
}
