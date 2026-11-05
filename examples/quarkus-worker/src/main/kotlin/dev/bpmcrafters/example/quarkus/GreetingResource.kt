package dev.bpmcrafters.example.quarkus

import io.smallrye.common.annotation.Blocking
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/demo/greetings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class GreetingResource @Inject constructor(private val adapter: DemoEngineAdapter) {
  @POST
  @Blocking
  fun greet(request: GreetingRequest?): GreetingResponse {
    val name = request?.name?.takeUnless { it.isBlank() }
      ?: throw BadRequestException("Provide a non-blank name")
    val result = adapter.deliver("greet", mapOf("request" to mapOf("name" to name)))
    return GreetingResponse(result["greeting"] as String)
  }
}
