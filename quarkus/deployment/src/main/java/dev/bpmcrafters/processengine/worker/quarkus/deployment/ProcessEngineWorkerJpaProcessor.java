package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.JpaIdempotencyRegistryProducer;
import dev.bpmcrafters.processengine.worker.quarkus.NoOpIdempotencyRegistryProducer;
import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerValidationException;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import org.jboss.jandex.DotName;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

/**
 * Build steps wiring the JPA-based idempotency registry ({@code process-engine-worker-idempotency-registry-jpa}) if
 * Hibernate ORM is present and the JPA module is on the classpath.
 * <p>
 * The steps depend on {@code quarkus-hibernate-orm-deployment-spi} only (a tiny artifact depending on {@code quarkus-core-deployment}),
 * so they are always loadable, even if Hibernate ORM is not part of the application. The full
 * {@code quarkus-hibernate-orm-deployment} is only a test dependency of the extension.
 * @since 0.8.6
 */
public class ProcessEngineWorkerJpaProcessor {

  private static final Logger LOG = Logger.getLogger(ProcessEngineWorkerJpaProcessor.class);

  static final String JPA_ARTIFACT_ID = "process-engine-worker-idempotency-registry-jpa";
  static final String IDEMPOTENCY_PACKAGE = "dev.bpmcrafters.processengine.worker.idempotency.";
  static final DotName TASK_LOG_ENTRY = DotName.createSimple(IDEMPOTENCY_PACKAGE + "TaskLogEntry");
  static final String TASK_RESULT_MAP_CONVERTER = IDEMPOTENCY_PACKAGE + "TaskResultMapConverter";
  static final String JAVA_SERIALIZER = IDEMPOTENCY_PACKAGE + "JavaTaskResultMapSerializer";
  static final String JACKSON_SERIALIZER = IDEMPOTENCY_PACKAGE + "JacksonTaskResultMapSerializer";
  static final String ENTITY_MANAGER_REGISTRY = IDEMPOTENCY_PACKAGE + "EntityManagerJpaIdempotencyRegistry";
  /**
   * Name of the default persistence unit (io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME).
   */
  static final String DEFAULT_PERSISTENCE_UNIT = "<default>";

  @BuildStep
  IndexDependencyBuildItem indexJpaModule() {
    // ignored by Quarkus if the artifact is not part of the application
    return new IndexDependencyBuildItem(ProcessEngineWorkerProcessor.GROUP_ID, JPA_ARTIFACT_ID);
  }

  @BuildStep
  void jpaIdempotencyRegistry(
    Capabilities capabilities,
    CombinedIndexBuildItem combinedIndex,
    BuildProducer<AdditionalBeanBuildItem> additionalBeans,
    BuildProducer<ExcludedTypeBuildItem> excludedTypes,
    BuildProducer<AdditionalJpaModelBuildItem> jpaModel,
    BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
    BuildProducer<ValidationErrorBuildItem> validationErrors
  ) {
    boolean hibernatePresent = capabilities.isPresent(Capability.HIBERNATE_ORM) && isHibernateOrmEnabled();
    boolean jpaModulePresent = combinedIndex.getIndex().getClassByName(TASK_LOG_ENTRY) != null;
    if (!hibernatePresent || !jpaModulePresent) {
      LOG.debugf("PROCESS-ENGINE-WORKER-034: JPA idempotency registry not active (hibernate-orm enabled: %s, jpa module present: %s), using the no-op registry.", hibernatePresent, jpaModulePresent);
      // both producers declare @Produces and would be discovered from the index of the runtime artifact otherwise
      excludedTypes.produce(new ExcludedTypeBuildItem(JpaIdempotencyRegistryProducer.class.getName()));
      additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(NoOpIdempotencyRegistryProducer.class));
      return;
    }
    excludedTypes.produce(new ExcludedTypeBuildItem(NoOpIdempotencyRegistryProducer.class.getName()));
    if (capabilities.isMissing(Capability.TRANSACTIONS)) {
      validationErrors.produce(new ValidationErrorBuildItem(new ProcessEngineWorkerValidationException(
        "PROCESS-ENGINE-WORKER-035: The JPA idempotency registry requires JTA transactions. Add the 'io.quarkus:quarkus-narayana-jta' extension."
      )));
      return;
    }
    LOG.debug("PROCESS-ENGINE-WORKER-034: Hibernate ORM and the JPA idempotency module detected, registering the JPA idempotency registry.");
    jpaModel.produce(new AdditionalJpaModelBuildItem(TASK_LOG_ENTRY.toString(), Set.of(DEFAULT_PERSISTENCE_UNIT)));
    additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(JpaIdempotencyRegistryProducer.class));
    reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
      List.of(TASK_LOG_ENTRY.toString(), TASK_RESULT_MAP_CONVERTER, JAVA_SERIALIZER, JACKSON_SERIALIZER, ENTITY_MANAGER_REGISTRY)
    ).constructors().methods().fields().reason(ProcessEngineWorkerProcessor.FEATURE).build());
  }

  /*
   * Hibernate ORM provides its capability even if disabled via the build-time switch 'quarkus.hibernate-orm.enabled'.
   * The switch is read from the build config to avoid a dependency on quarkus-hibernate-orm-deployment.
   */
  private static boolean isHibernateOrmEnabled() {
    return ConfigProvider.getConfig().getOptionalValue("quarkus.hibernate-orm.enabled", Boolean.class).orElse(true);
  }
}
