package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengine.worker.acceptance.AcceptanceHttp.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.equalTo

/** use keeps the original test failure primary and suppresses cleanup errors. */
class ControlledDelivery(topic: String, private val id: String) : AutoCloseable {
  private val response: CompletableFuture<Response>

  init {
    given().post("/harness/completion/$id/pending").then().statusCode(204)
    response = CompletableFuture.supplyAsync {
      given().contentType(ContentType.JSON).body(emptyMap<String, Any?>()).post("/harness/deliver/$topic/$id")
    }
  }

  fun awaitPending() {
    await().atMost(Duration.ofSeconds(20)).untilAsserted {
      given().get("/harness/tasks/$id").then().statusCode(200).body("pending", equalTo(true))
    }
  }

  fun assertWaiting() { assertThat(response).isNotDone() }

  fun finish(success: Boolean): Response {
    release(success)
    return response.get(40, TimeUnit.SECONDS)
  }

  private fun release(success: Boolean) {
    given().queryParam("success", success).post("/harness/release/$id").then().statusCode(204)
  }

  override fun close() { cleanup({ release(false) }, { response.get(40, TimeUnit.SECONDS) }) }

  companion object {
    fun cleanup(release: Runnable, join: Callable<*>) {
      var failure: Throwable? = null
      try {
        release.run() // Durable cancellation also covers release-before-registration.
      } catch (exception: Throwable) {
        failure = exception
      }
      try {
        join.call() // Join even when releasing failed.
      } catch (exception: Throwable) {
        if (exception is InterruptedException) Thread.currentThread().interrupt()
        if (failure == null) failure = exception else failure.addSuppressed(exception)
      }
      if (failure != null) throw AssertionError("Controlled delivery cleanup failed", failure)
    }
  }
}
