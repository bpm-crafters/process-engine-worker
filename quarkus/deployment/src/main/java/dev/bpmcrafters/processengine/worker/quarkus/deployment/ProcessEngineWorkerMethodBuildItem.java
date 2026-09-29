package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

/**
 * A process engine worker method detected at build time.
 * @since 0.8.6
 */
public final class ProcessEngineWorkerMethodBuildItem extends MultiBuildItem {

  private final ClassInfo beanClass;
  private final MethodInfo method;
  private final String topic;

  /**
   * Creates a new build item.
   * @param beanClass concrete bean class the worker is registered on (may differ from the declaring class of the method for inherited workers).
   * @param method worker method.
   * @param topic effective topic of the worker.
   */
  public ProcessEngineWorkerMethodBuildItem(ClassInfo beanClass, MethodInfo method, String topic) {
    this.beanClass = beanClass;
    this.method = method;
    this.topic = topic;
  }

  /**
   * @return concrete bean class the worker is registered on.
   */
  public ClassInfo getBeanClass() {
    return beanClass;
  }

  /**
   * @return worker method.
   */
  public MethodInfo getMethod() {
    return method;
  }

  /**
   * @return effective topic of the worker.
   */
  public String getTopic() {
    return topic;
  }

  @Override
  public String toString() {
    return beanClass.name() + "#" + method.name() + " -> " + topic;
  }
}
