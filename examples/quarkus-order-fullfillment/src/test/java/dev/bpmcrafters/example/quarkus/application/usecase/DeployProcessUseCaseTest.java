package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.out.ProcessDeployment;
import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeployProcessUseCaseTest {
  @Test
  void returnsDeploymentResultAndPropagatesFailure() throws Exception {
    final var port = mock(ProcessDeployment.class);
    final var result = new DeploymentResult("key", null, null);
    final var useCase = new DeployProcessUseCase(port);
    when(port.deploy()).thenReturn(result);
    assertThat(useCase.deploy()).isSameAs(result);
    when(port.deploy()).thenThrow(new IllegalStateException("offline"));
    assertThatThrownBy(useCase::deploy).hasMessage("offline");
  }
}
