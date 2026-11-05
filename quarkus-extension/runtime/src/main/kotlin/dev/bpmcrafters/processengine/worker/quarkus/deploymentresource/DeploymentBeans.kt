package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

@Singleton
class DeploymentBeans {
  @Produces @Singleton @DefaultBean
  fun processDeployment(api: Instance<DeploymentApi>, resources: DeploymentResources, config: DeploymentConfig): ProcessDeployment =
    ProcessDeployment {
      // Like Spring's ConditionalOnBean, explicit startup enablement still skips a missing client.
      if (api.isUnsatisfied) null else {
        val handle = api.handle
        try {
          ResourceProcessDeployment(handle.get(), resources, config.deploymentTimeoutInSeconds()).deployResources()
        } finally {
          if (handle.bean.scope == Dependent::class.java) handle.destroy()
        }
      }
    }
}
