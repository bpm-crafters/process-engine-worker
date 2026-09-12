package dev.bpmcrafters.example.camunda7remote

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

fun main() = runApplication<ExampleApplication>().let { }

@EnableAsync
@SpringBootApplication
class ExampleApplication
