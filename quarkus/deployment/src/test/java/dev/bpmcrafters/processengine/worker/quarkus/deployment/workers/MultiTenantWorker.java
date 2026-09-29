package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

/**
 * The same topic served for two different tenants is a valid setup.
 */
public class MultiTenantWorker {

  @ProcessEngineWorker(topic = "shared-topic", tenantId = "tenant-a")
  public void forTenantA() {
  }

  @ProcessEngineWorker(topic = "shared-topic", tenantId = "tenant-b")
  public void forTenantB() {
  }
}
