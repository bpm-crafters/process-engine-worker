package dev.bpmcrafters.example.camunda7remote

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.ConnectException

private val logger = KotlinLogging.logger {}

@Component
class SomeProcessService {

  @ProcessEngineWorker(topic = "example.retry-backoff.do-stuff")
  fun doStuff() {
    logger.info{ "I'm really trying here but nope, couldn't connect to the remote system (tehehe, if only they knew)" }
    throw ConnectException("Oh no, could not connect to the remote system (tehehe)")
  }

}
