package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import dev.bpmcrafters.processengineapi.deploy.NamedResource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeploymentResource {

  private final DeploymentApi deploymentApi;

  @SneakyThrows
  @PostMapping("/")
  public ResponseEntity<DeploymentInformation> deploy(@RequestBody DeploymentResourceDto resourceName) {
    var info = deploymentApi.deploy(
      new DeployBundleCommand(
        List.of(
          NamedResource.fromClasspath(resourceName.path())
        ),
        null
      )
    ).get();
    return ok(info);
  }
}

