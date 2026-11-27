package dev.bpmcrafters.processengine.worker.acceptance

import io.restassured.RestAssured
import io.restassured.config.HttpClientConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.specification.RequestSpecification

object AcceptanceHttp {
  fun given(): RequestSpecification = RestAssured.given().config(RestAssuredConfig.config().httpClient(
    HttpClientConfig.httpClientConfig().setParam("http.connection.timeout", 5_000)
      .setParam("http.socket.timeout", 30_000).setParam("http.connection-manager.timeout", 5_000L)))
}
