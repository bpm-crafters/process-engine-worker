package dev.bpmcrafters.processengine.worker.quarkus.deployment.support;

import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Helpers for the extension tests.
 */
public final class TestSupport {

  private TestSupport() {
  }

  /**
   * Creates an archive containing the test support classes (in-memory engine APIs and their producer) and the given classes.
   */
  public static JavaArchive archive(Class<?>... classes) {
    return ShrinkWrap.create(JavaArchive.class)
      .addClasses(TestSupport.class, TestApiProducer.class, InMemoryTaskSubscriptionApi.class, RecordingServiceTaskCompletionApi.class)
      .addClasses(classes);
  }

  /**
   * Hibernate ORM, H2 and the JPA idempotency module are test dependencies of the extension. Tests not covering JPA
   * disable Hibernate ORM and the datasource dev services, so that no database is required.
   */
  public static QuarkusExtensionTest withoutJpa(QuarkusExtensionTest test) {
    return test
      .overrideConfigKey("quarkus.hibernate-orm.enabled", "false")
      .overrideConfigKey("quarkus.datasource.devservices.enabled", "false");
  }

  /**
   * Configures an in-memory H2 database for the JPA idempotency registry.
   */
  public static QuarkusExtensionTest withJpa(QuarkusExtensionTest test, String databaseName) {
    return test
      .overrideConfigKey("quarkus.datasource.db-kind", "h2")
      .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1")
      .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
  }

  /**
   * Excludes JTA (narayana) and everything depending on it (Hibernate ORM, JDBC) from the application.
   */
  public static Set<ArtifactKey> withoutTransactions() {
    // the deployment artifacts are direct test dependencies of this module and must be excluded explicitly
    return Set.of(
      ArtifactKey.ga("io.quarkus", "quarkus-hibernate-orm"),
      ArtifactKey.ga("io.quarkus", "quarkus-hibernate-orm-deployment"),
      ArtifactKey.ga("io.quarkus", "quarkus-jdbc-h2"),
      ArtifactKey.ga("io.quarkus", "quarkus-jdbc-h2-deployment"),
      ArtifactKey.ga("io.quarkus", "quarkus-agroal"),
      ArtifactKey.ga("io.quarkus", "quarkus-agroal-deployment"),
      ArtifactKey.ga("io.quarkus", "quarkus-datasource"),
      ArtifactKey.ga("io.quarkus", "quarkus-datasource-deployment"),
      ArtifactKey.ga("io.quarkus", "quarkus-narayana-jta"),
      ArtifactKey.ga("io.quarkus", "quarkus-narayana-jta-deployment"),
      ArtifactKey.ga("dev.bpm-crafters.process-engine-worker", "process-engine-worker-idempotency-registry-jpa")
    );
  }

  /**
   * Checks the cause chain and suppressed exceptions of the throwable for a match.
   */
  public static boolean anyInChain(Throwable throwable, Predicate<Throwable> predicate) {
    if (throwable == null) {
      return false;
    }
    if (predicate.test(throwable)) {
      return true;
    }
    for (Throwable suppressed : throwable.getSuppressed()) {
      if (anyInChain(suppressed, predicate)) {
        return true;
      }
    }
    return throwable.getCause() != throwable && anyInChain(throwable.getCause(), predicate);
  }
}
