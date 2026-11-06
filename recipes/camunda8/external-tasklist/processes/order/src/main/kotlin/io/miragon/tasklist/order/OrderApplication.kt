package io.miragon.tasklist.order

import io.camunda.client.annotation.Deployment
import io.miragon.tasklist.contract.provider.EnableTasklistProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/** The order application of the MiraVelo bike shop: orders, the order process and its forms. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTasklistProvider
@Deployment(resources = ["classpath*:/bpmn/*.bpmn"])
class OrderApplication

fun main(args: Array<String>) {
    runApplication<OrderApplication>(*args)
}

/**
 * @property payments where the payment application runs.
 * @property callbackBaseUrl how the payment application reaches this application to report a payment result.
 */
@ConfigurationProperties("order")
data class OrderProperties(
    val payments: Payments,
    val callbackBaseUrl: String,
) {
    data class Payments(val baseUrl: String)
}
