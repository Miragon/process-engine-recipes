package io.miragon.tasklist.taskmanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class TaskmanagerApplication

fun main(args: Array<String>) {
    runApplication<TaskmanagerApplication>(*args)
}
