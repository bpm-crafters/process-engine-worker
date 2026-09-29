package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.JtaTransactionalExecutor;
import dev.bpmcrafters.processengine.worker.quarkus.MicrometerProcessEngineWorkerMetrics;
import dev.bpmcrafters.processengine.worker.quarkus.NoOpProcessEngineWorkerMetricsProducer;
import dev.bpmcrafters.processengine.worker.quarkus.NoneTransactionalExecutorProducer;
import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerProducers;
import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerRecorder;
import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerRegistration;
import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerValidationException;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.IgnoreSplitPackageBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Build steps of the process engine worker extension: bean registration, build-time discovery and validation of
 * {@code @ProcessEngineWorker} methods, runtime-init registration and native image support.
 * @since 0.8.6
 */
public class ProcessEngineWorkerProcessor {

  private static final Logger LOG = Logger.getLogger(ProcessEngineWorkerProcessor.class);

  static final String FEATURE = "process-engine-worker";
  static final String GROUP_ID = "dev.bpm-crafters.process-engine-worker";
  static final String CORE_ARTIFACT_ID = "process-engine-worker-core";
  static final String RUNTIME_ARTIFACT_ID = "process-engine-worker-quarkus";

  static final DotName PROCESS_ENGINE_WORKER = DotName.createSimple("dev.bpmcrafters.processengine.worker.ProcessEngineWorker");
  static final DotName VARIABLE = DotName.createSimple("dev.bpmcrafters.processengine.worker.Variable");
  static final DotName DEFAULT_VARIABLE_CONVERTER = DotName.createSimple("dev.bpmcrafters.processengine.worker.Variable$DefaultVariableConverter");
  static final DotName MAP = DotName.createSimple(Map.class.getName());
  static final String UNSET_TOPIC = "__unset";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  void indexDependencies(BuildProducer<IndexDependencyBuildItem> indexDependencies) {
    indexDependencies.produce(new IndexDependencyBuildItem(GROUP_ID, CORE_ARTIFACT_ID));
    indexDependencies.produce(new IndexDependencyBuildItem(GROUP_ID, RUNTIME_ARTIFACT_ID));
  }

  /*
   * The idempotency package is intentionally shared by the core and the JPA idempotency module (same FQCNs as the Spring modules).
   */
  @BuildStep
  IgnoreSplitPackageBuildItem ignoreSplitPackages() {
    return new IgnoreSplitPackageBuildItem(List.of("dev.bpmcrafters.processengine.worker.idempotency"));
  }

  /*
   * The fallback producers (NONE executor, no-op metrics) declare @Produces and would be discovered from the index of the
   * runtime artifact in any case, so the inactive variant is excluded explicitly from bean discovery.
   */
  @BuildStep
  void additionalBeans(
    Capabilities capabilities,
    Optional<MetricsCapabilityBuildItem> metricsCapability,
    BuildProducer<AdditionalBeanBuildItem> additionalBeans,
    BuildProducer<ExcludedTypeBuildItem> excludedTypes
  ) {
    additionalBeans.produce(AdditionalBeanBuildItem.builder()
      .addBeanClasses(ProcessEngineWorkerProducers.class, ProcessEngineWorkerRegistration.class)
      .setUnremovable()
      .build());
    if (capabilities.isPresent(Capability.TRANSACTIONS)) {
      LOG.debug("PROCESS-ENGINE-WORKER-030: Transactions capability detected, registering JTA transactional executor.");
      additionalBeans.produce(AdditionalBeanBuildItem.builder()
        .addBeanClass(JtaTransactionalExecutor.class)
        .setDefaultScope(DotNames.SINGLETON)
        .setUnremovable()
        .build());
      excludedTypes.produce(new ExcludedTypeBuildItem(NoneTransactionalExecutorProducer.class.getName()));
    } else {
      LOG.debug("PROCESS-ENGINE-WORKER-030: No transactions capability detected, transactional workers run without transaction management.");
      additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(NoneTransactionalExecutorProducer.class));
    }
    if (metricsCapability.isPresent() && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)) {
      LOG.debug("PROCESS-ENGINE-WORKER-031: Micrometer metrics capability detected, registering Micrometer worker metrics.");
      additionalBeans.produce(AdditionalBeanBuildItem.builder()
        .addBeanClass(MicrometerProcessEngineWorkerMetrics.class)
        .setDefaultScope(DotNames.SINGLETON)
        .setUnremovable()
        .build());
      excludedTypes.produce(new ExcludedTypeBuildItem(NoOpProcessEngineWorkerMetricsProducer.class.getName()));
    } else {
      additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(NoOpProcessEngineWorkerMetricsProducer.class));
    }
  }

  /*
   * Concrete classes declaring (or inheriting) non-static @ProcessEngineWorker methods become @Singleton beans
   * if they do not declare a scope (parity with Spring's @Component requirement being the only prerequisite).
   */
  @BuildStep
  AutoAddScopeBuildItem autoAddScope() {
    return AutoAddScopeBuildItem.builder()
      .match((clazz, annotations, index) -> !isAbstractOrInterface(clazz)
        && declaresOrInheritsWorkerMethod(clazz, index)
        // classes exposed via producers must not become class beans as well (reported by the validation)
        && !producedClassNames(index).contains(clazz.name()))
      .defaultScope(BuiltinScope.SINGLETON)
      .unremovable()
      .reason("Found @ProcessEngineWorker methods")
      .build();
  }

  private static boolean declaresOrInheritsWorkerMethod(ClassInfo clazz, IndexView index) {
    Set<DotName> visited = new HashSet<>();
    ClassInfo current = clazz;
    while (current != null && visited.add(current.name())) {
      if (hasWorkerMethod(current)) {
        return true;
      }
      for (DotName interfaceName : current.interfaceNames()) {
        ClassInfo interfaceInfo = index.getClassByName(interfaceName);
        if (interfaceInfo != null && hasWorkerMethod(interfaceInfo)) {
          return true;
        }
      }
      DotName superName = current.superName();
      current = superName == null || DotNames.OBJECT.equals(superName) ? null : index.getClassByName(superName);
    }
    return false;
  }

  /*
   * Names of the classes exposed via @Produces methods or fields.
   */
  private static Set<DotName> producedClassNames(IndexView index) {
    Set<DotName> names = new HashSet<>();
    for (AnnotationInstance produces : index.getAnnotations(DotNames.PRODUCES)) {
      AnnotationTarget target = produces.target();
      Type type = switch (target.kind()) {
        case METHOD -> target.asMethod().returnType();
        case FIELD -> target.asField().type();
        default -> null;
      };
      if (type != null && type.kind() == Type.Kind.CLASS) {
        names.add(type.name());
      }
    }
    return names;
  }

  private static boolean hasWorkerMethod(ClassInfo clazz) {
    return clazz.methods().stream().anyMatch(m -> !Modifier.isStatic(m.flags()) && m.hasAnnotation(PROCESS_ENGINE_WORKER));
  }

  @BuildStep
  void unremovableWorkerBeans(CombinedIndexBuildItem combinedIndex, BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
    IndexView index = combinedIndex.getIndex();
    Set<String> classNames = index.getAnnotations(PROCESS_ENGINE_WORKER).stream()
      .filter(a -> a.target().kind() == AnnotationTarget.Kind.METHOD)
      .map(a -> a.target().asMethod().declaringClass())
      .flatMap(declaringClass -> resolveBeanClasses(index, declaringClass).stream())
      .map(c -> c.name().toString())
      .collect(Collectors.toCollection(TreeSet::new));
    if (!classNames.isEmpty()) {
      unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(classNames.toArray(new String[0])));
    }
  }

  @BuildStep
  void collectWorkerMethods(
    CombinedIndexBuildItem combinedIndex,
    BeanDiscoveryFinishedBuildItem beanDiscovery,
    TransformedAnnotationsBuildItem transformedAnnotations,
    BuildProducer<ProcessEngineWorkerMethodBuildItem> workerMethods,
    BuildProducer<ValidationErrorBuildItem> validationErrors
  ) {
    IndexView index = combinedIndex.getIndex();
    Set<DotName> beanClasses = beanDiscovery.beanStream().classBeans().stream()
      .map(BeanInfo::getBeanClass)
      .collect(Collectors.toSet());

    List<String> errors = new ArrayList<>();
    Set<DotName> producedClasses = producedClassNames(index);
    // key: bean class + method signature (deduplicates overridden workers, e.g. an abstract worker overridden in a subclass)
    Map<String, ProcessEngineWorkerMethodBuildItem> workers = new LinkedHashMap<>();

    for (AnnotationInstance annotation : index.getAnnotations(PROCESS_ENGINE_WORKER)) {
      if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
        continue;
      }
      MethodInfo method = annotation.target().asMethod();
      ClassInfo declaringClass = method.declaringClass();
      String location = declaringClass.name() + "#" + method.name();

      if (!Modifier.isPublic(method.flags())) {
        // the registrar discovers public methods only (Class.getMethods())
        errors.add("Worker method " + location + " must be public.");
        continue;
      }
      if (Modifier.isStatic(method.flags())) {
        errors.add("Worker method " + location + " must not be static.");
        continue;
      }
      if (Modifier.isAbstract(method.flags())) {
        // the registrar detects the annotation on the method itself only, an un-annotated implementation would be ignored silently
        errors.add("Worker method " + location + " must not be abstract. Annotate the implementing method instead.");
        continue;
      }
      String topic;
      try {
        topic = effectiveTopic(annotation, method);
      } catch (IllegalStateException e) {
        errors.add(e.getMessage());
        continue;
      }

      Collection<ClassInfo> targetClasses = resolveBeanClasses(index, declaringClass);
      if (targetClasses.isEmpty()) {
        if (isAbstractOrInterface(declaringClass)) {
          errors.add("Worker method " + location + " is declared on an abstract class or interface without any concrete subclass.");
        }
        continue;
      }
      for (ClassInfo beanClass : targetClasses) {
        if (isShadowedByAnnotatedOverride(index, beanClass, declaringClass, method)) {
          // the override carries its own annotation and is what Class.getMethods() exposes at runtime
          continue;
        }
        if (producedClasses.contains(beanClass.name())) {
          continue; // reported below
        }
        if (!beanClasses.contains(beanClass.name())) {
          errors.add("Class " + beanClass.name() + " declares the worker method " + location
            + " but is not a bean (e.g. vetoed or excluded). Remove the exclusion or the annotation.");
          continue;
        }
        String key = beanClass.name() + "#" + method.name() + method.parameterTypes().stream().map(Type::toString).collect(Collectors.joining(",", "(", ")"));
        workers.putIfAbsent(key, new ProcessEngineWorkerMethodBuildItem(beanClass, method, topic));
      }
    }

    // workers exposed via producers are not supported: the worker class would have to become a class bean as well
    for (DotName producedClassName : producedClasses) {
      ClassInfo producedClass = index.getClassByName(producedClassName);
      if (producedClass != null && !isAbstractOrInterface(producedClass) && declaresOrInheritsWorkerMethod(producedClass, index)) {
        errors.add("Worker class " + producedClass.name() + " is exposed via the producer of type " + producedClass.simpleName()
          + ". Workers must be class beans: the class is discovered automatically and becomes a @Singleton, "
          + "remove the producer and inject its dependencies into the worker instead.");
      }
    }

    // duplicate topics (the same topic may be used by workers of different tenants)
    Map<String, List<String>> byTopicAndTenant = new LinkedHashMap<>();
    for (ProcessEngineWorkerMethodBuildItem worker : workers.values()) {
      String tenantId = tenantId(worker.getMethod().annotation(PROCESS_ENGINE_WORKER));
      String key = tenantId.isEmpty() ? worker.getTopic() : worker.getTopic() + " (tenant '" + tenantId + "')";
      byTopicAndTenant.computeIfAbsent(key, t -> new ArrayList<>()).add(worker.getBeanClass().name() + "#" + worker.getMethod().name());
    }
    byTopicAndTenant.forEach((topic, locations) -> {
      if (locations.size() > 1) {
        errors.add("Topic '" + topic + "' is used by multiple worker methods: " + String.join(", ", locations) + ". Every topic must be used by exactly one worker per tenant.");
      }
    });

    // Kotlin dual constructor case: a no-arg constructor and a parameterised one without @Inject -> Arc would silently use the no-arg one
    Set<DotName> checkedClasses = new HashSet<>();
    for (ProcessEngineWorkerMethodBuildItem worker : workers.values()) {
      ClassInfo beanClass = worker.getBeanClass();
      if (!checkedClasses.add(beanClass.name())) {
        continue;
      }
      List<MethodInfo> constructors = beanClass.constructors().stream().filter(c -> !c.isSynthetic()).collect(Collectors.toList());
      boolean hasInject = constructors.stream().anyMatch(c -> transformedAnnotations.hasAnnotation(c, DotNames.INJECT));
      boolean hasNoArg = constructors.stream().anyMatch(c -> c.parametersCount() == 0);
      boolean hasParameterised = constructors.stream().anyMatch(c -> c.parametersCount() > 0);
      if (!hasInject && hasNoArg && hasParameterised) {
        errors.add("Worker class " + beanClass.name() + " declares a no-arg constructor and a parameterised constructor without @Inject; "
          + "the container would use the no-arg constructor and the dependencies would not be injected. "
          + "Annotate the constructor to use with @Inject or remove the default arguments.");
      }
    }

    if (!errors.isEmpty()) {
      validationErrors.produce(new ValidationErrorBuildItem(
        errors.stream().map(e -> new ProcessEngineWorkerValidationException("PROCESS-ENGINE-WORKER-032: " + e)).collect(Collectors.toList())
      ));
      return;
    }
    for (ProcessEngineWorkerMethodBuildItem worker : workers.values()) {
      LOG.debugf("PROCESS-ENGINE-WORKER-033: Detected process engine worker %s", worker);
      workerMethods.produce(worker);
    }
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  @Consume(SyntheticBeansRuntimeInitBuildItem.class)
  ServiceStartBuildItem registerWorkers(ProcessEngineWorkerRecorder recorder, BeanContainerBuildItem beanContainer, List<ProcessEngineWorkerMethodBuildItem> workerMethods) {
    List<String> classNames = new ArrayList<>(workerMethods.stream()
      .map(w -> w.getBeanClass().name().toString())
      .collect(Collectors.toCollection(LinkedHashSet::new)));
    recorder.registerWorkers(beanContainer.getValue(), classNames);
    return new ServiceStartBuildItem(FEATURE);
  }

  @BuildStep
  void nativeImage(
    CombinedIndexBuildItem combinedIndex,
    List<ProcessEngineWorkerMethodBuildItem> workerMethods,
    BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
    BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchies
  ) {
    IndexView index = combinedIndex.getIndex();
    Set<String> workerClasses = workerMethods.stream()
      .flatMap(w -> java.util.stream.Stream.of(w.getBeanClass().name().toString(), w.getMethod().declaringClass().name().toString()))
      .collect(Collectors.toCollection(TreeSet::new));
    if (!workerClasses.isEmpty()) {
      reflectiveClasses.produce(ReflectiveClassBuildItem.builder(workerClasses).methods().reason(FEATURE).build());
    }
    Set<String> converterClasses = new TreeSet<>();
    Set<Type> hierarchyTypes = new LinkedHashSet<>();
    for (ProcessEngineWorkerMethodBuildItem worker : workerMethods) {
      MethodInfo method = worker.getMethod();
      for (MethodParameterInfo parameter : method.parameters()) {
        if (parameter.hasAnnotation(VARIABLE)) {
          hierarchyTypes.add(parameter.type());
          AnnotationValue converter = parameter.annotation(VARIABLE).value("converter");
          if (converter != null && !DEFAULT_VARIABLE_CONVERTER.equals(converter.asClass().name())) {
            converterClasses.add(converter.asClass().name().toString());
          }
        }
      }
      Type returnType = method.returnType();
      if (returnType.kind() != Type.Kind.VOID && returnType.kind() != Type.Kind.PRIMITIVE) {
        hierarchyTypes.add(returnType);
      }
    }
    if (!converterClasses.isEmpty()) {
      reflectiveClasses.produce(ReflectiveClassBuildItem.builder(converterClasses).constructors().fields().reason(FEATURE).build());
    }
    for (Type type : hierarchyTypes) {
      reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(type).index(index).source(FEATURE).build());
    }
  }

  /*
   * Resolves the concrete bean classes a worker method declared on the given class applies to.
   */
  private static Collection<ClassInfo> resolveBeanClasses(IndexView index, ClassInfo declaringClass) {
    if (!isAbstractOrInterface(declaringClass)) {
      return List.of(declaringClass);
    }
    Collection<ClassInfo> candidates = declaringClass.isInterface()
      ? index.getAllKnownImplementors(declaringClass.name())
      : index.getAllKnownSubclasses(declaringClass.name());
    return candidates.stream().filter(c -> !isAbstractOrInterface(c)).collect(Collectors.toList());
  }

  /*
   * Checks if the worker method declared on a super type is overridden by an annotated method in the bean class or in a class between.
   */
  private static boolean isShadowedByAnnotatedOverride(IndexView index, ClassInfo beanClass, ClassInfo declaringClass, MethodInfo method) {
    if (beanClass.name().equals(declaringClass.name())) {
      return false;
    }
    Type[] parameterTypes = method.parameterTypes().toArray(new Type[0]);
    ClassInfo current = beanClass;
    Set<DotName> visited = new HashSet<>();
    while (current != null && !current.name().equals(declaringClass.name()) && visited.add(current.name())) {
      MethodInfo override = current.method(method.name(), parameterTypes);
      if (override != null && override.hasAnnotation(PROCESS_ENGINE_WORKER)) {
        return true;
      }
      DotName superName = current.superName();
      current = superName == null || DotNames.OBJECT.equals(superName) ? null : index.getClassByName(superName);
    }
    return false;
  }

  private static boolean isAbstractOrInterface(ClassInfo classInfo) {
    return classInfo.isInterface() || Modifier.isAbstract(classInfo.flags());
  }

  /*
   * Mirrors the alias resolution of the core: topic, else value, else method name; conflict if both set and different.
   */
  static String effectiveTopic(AnnotationInstance annotation, MethodInfo method) {
    String topic = stringValue(annotation, "topic");
    String value = stringValue(annotation, "value");
    boolean topicSet = topic != null && !UNSET_TOPIC.equals(topic);
    boolean valueSet = value != null && !UNSET_TOPIC.equals(value);
    if (topicSet && valueSet && !topic.equals(value)) {
      throw new IllegalStateException("Attributes 'topic' and 'value' of @ProcessEngineWorker on " + method.declaringClass().name() + "#" + method.name()
        + " are aliases and must not be set to different values, but were '" + topic + "' and '" + value + "'.");
    }
    if (topicSet) {
      return topic;
    }
    if (valueSet) {
      return value;
    }
    return method.name();
  }

  /*
   * Tenant id attribute of the annotation, blank if not set.
   */
  static String tenantId(AnnotationInstance annotation) {
    String tenantId = annotation == null ? null : stringValue(annotation, "tenantId");
    return tenantId == null ? "" : tenantId.trim();
  }

  private static String stringValue(AnnotationInstance annotation, String name) {
    AnnotationValue value = annotation.value(name);
    return value == null ? null : value.asString();
  }
}
