package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import dev.bpmcrafters.processengineapi.deploy.NamedResource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeploymentResource {

  private final DeploymentApi deploymentApi;

  @SneakyThrows
  @PostMapping("/{resourceName}")
  public ResponseEntity<DeploymentInformation> deploy(@PathVariable(name = "resourceName") String resourceName) {
    var info = deploymentApi.deploy(
      new DeployBundleCommand(
        List.of(
          NamedResource.fromClasspath(resourceName)
        ),
        null
      )
    ).get();
    return ok(info);
  }
}
