package dev.bpmcrafters.example.camunda7embedded

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main() = runApplication<ExampleEmbeddedApplication>().let { }

@SpringBootApplication
class ExampleEmbeddedApplication
