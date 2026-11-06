package io.miragon.tasklist.payment

import io.camunda.client.annotation.Deployment
import io.miragon.tasklist.contract.provider.EnableTasklistProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTasklistProvider
@Deployment(resources = ["classpath*:/bpmn/*.bpmn"])
class PaymentApplication

fun main(args: Array<String>) {
    runApplication<PaymentApplication>(*args)
}
