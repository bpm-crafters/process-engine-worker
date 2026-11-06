package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.*
import dev.bpmcrafters.processengine.worker.registrar.*
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.quarkus.arc.deployment.*
import io.quarkus.arc.processor.AnnotationsTransformer
import io.quarkus.arc.processor.BeanInfo
import io.quarkus.deployment.Capabilities
import io.quarkus.deployment.Capability
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.annotations.ExecutionTime
import io.quarkus.deployment.annotations.Record
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Predicate
import org.jboss.jandex.AnnotationInstance
import org.jboss.jandex.AnnotationTarget
import org.jboss.jandex.DotName
import org.jboss.jandex.IndexView

class WorkerProcessor {
  @BuildStep
  fun feature() = FeatureBuildItem("process-engine-worker")

  @BuildStep
  fun defaults(capabilities: Capabilities): AdditionalBeanBuildItem {
    val beans = AdditionalBeanBuildItem.builder().setUnremovable()
      .addBeanClasses(WorkerDefaults::class.java, WorkerRuntime::class.java,
        WorkerDeliveryInterceptor::class.java, WorkerDeliveryEntryInterceptor::class.java)
    if (capabilities.isPresent(Capability.METRICS)) beans.addBeanClass(MicrometerWorkerDefaults::class.java)
    return beans.build()
  }

  @BuildStep
  fun collaborators(): UnremovableBeanBuildItem = UnremovableBeanBuildItem.beanTypes(
    TaskSubscriptionApi::class.java, ServiceTaskCompletionApi::class.java, VariableConverter::class.java,
    ParameterResolver::class.java, ResultResolver::class.java, ProcessEngineWorkerMetrics::class.java, IdempotencyRegistry::class.java)

  @BuildStep
  fun retainWorkers(combined: CombinedIndexBuildItem): UnremovableBeanBuildItem {
    val candidates = candidates(combined.index)
    // Keep unsupported worker producers until validation can report them, even when unused.
    return UnremovableBeanBuildItem(Predicate { bean -> isWorker(bean, candidates) })
  }

  @BuildStep
  fun deliveryBindings(combined: CombinedIndexBuildItem, exclusions: BuildExclusionsBuildItem): AnnotationsTransformerBuildItem {
    val methods = HashSet<String>()
    for (candidate in candidates(combined.index) - exclusions.excludedDeclaringClasses) {
      val beanClass = load(candidate)
      if (beanClass.isInterface) continue
      beanClass.methods.filter { !it.isBridge && !it.isSynthetic }
        .filter { it.workerMetadata(beanClass) != null }
        .forEach { method -> methods.add("${method.declaringClass.name}|${method.name}|" +
          MethodType.methodType(method.returnType, method.parameterTypes.toList()).descriptorString()) }
    }
    return AnnotationsTransformerBuildItem(object : AnnotationsTransformer {
      override fun appliesTo(kind: AnnotationTarget.Kind) = kind == AnnotationTarget.Kind.METHOD
      override fun transform(context: AnnotationsTransformer.TransformationContext) {
        val method = context.target.asMethod()
        if ("${method.declaringClass().name()}|${method.name()}|${method.descriptor()}" in methods) {
          context.transform().add(WorkerDeliveryBinding::class.java).done()
        }
      }
    })
  }

  private fun candidates(index: IndexView): Set<String> {
    val candidates = HashSet<String>()
    for (annotation in index.getAnnotations(WORKER)) {
      if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) continue
      val owner = annotation.target().asMethod().declaringClass()
      candidates.add(owner.name().toString())
      index.getAllKnownSubclasses(owner.name()).forEach { candidates.add(it.name().toString()) }
      index.getAllKnownImplementors(owner.name()).forEach { candidates.add(it.name().toString()) }
    }
    return candidates
  }

  private fun isWorker(bean: BeanInfo, candidates: Set<String>): Boolean {
    if (bean.isClassBean) return bean.beanClass.toString() in candidates
    // A producer's beanClass is its declaring class, not the type it creates.
    return bean.providerType.name().toString() in candidates ||
      bean.types.any { it.name().toString() in candidates } ||
      bean.unrestrictedTypes.any { it.name().toString() in candidates }
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  fun discover(
    combined: CombinedIndexBuildItem, validation: ValidationPhaseBuildItem, exclusions: BuildExclusionsBuildItem,
    container: BeanContainerBuildItem, recorder: WorkerRecorder,
    reflection: BuildProducer<ReflectiveClassBuildItem>, hierarchies: BuildProducer<ReflectiveHierarchyBuildItem>,
  ) {
    val candidates = candidates(combined.index)
    val descriptors = ArrayList<WorkerDefinition>()
    val workerClasses = HashSet<Class<*>>()
    for (bean in validation.context.beans()) {
      if (!isWorker(bean, candidates)) continue
      require(bean.isClassBean) { "Worker producer beans are unsupported: $bean" }
      val scope = bean.scope.dotName.toString()
      require(scope == ApplicationScoped::class.java.name || scope == Singleton::class.java.name) {
        "Workers require @ApplicationScoped or @Singleton: $bean"
      }
      val beanClass = load(bean.beanClass.toString())
      workerClasses.add(beanClass)
      for (method in WorkerMethods.discover(beanClass)) {
        val transaction = transaction(bean, method)
        val rollback = exceptionTypes(transaction, "rollbackOn")
        val dontRollback = exceptionTypes(transaction, "dontRollbackOn")
        descriptors.add(WorkerDefinition(bean.identifier, beanClass.name, WorkerMethods.descriptor(beanClass, method),
          transactionType(transaction), rollback, dontRollback))
        val exceptions = rollback + dontRollback
        if (exceptions.isNotEmpty()) reflection.produce(ReflectiveClassBuildItem.builder(*exceptions.toTypedArray()).build())
      }
      registerReflection(beanClass, combined.index, reflection, hierarchies)
    }
    validateManagedWorkers(combined.index, workerClasses, exclusions)
    registerConverters(combined.index, reflection)
    recorder.configure(descriptors)
  }

  private fun transaction(bean: BeanInfo, method: Method): AnnotationInstance? {
    val descriptor = MethodType.methodType(method.returnType, method.parameterTypes.toList()).descriptorString()
    val transactional = DotName.createSimple(Transactional::class.java.name)
    return bean.interceptedMethodsBindings.entries.asSequence()
      .filter { it.key.name() == method.name && it.key.descriptor() == descriptor }
      .flatMap { it.value.asSequence() }.firstOrNull { it.name() == transactional }
  }

  private fun transactionType(transaction: AnnotationInstance?): String? =
    if (transaction == null) null else transaction.value("value")?.asEnum() ?: Transactional.TxType.REQUIRED.name

  private fun exceptionTypes(transaction: AnnotationInstance?, attribute: String): List<String> =
    transaction?.value(attribute)?.asClassArray()?.map { it.name().toString() } ?: emptyList()

  private fun validateManagedWorkers(index: IndexView, workerClasses: Set<Class<*>>, exclusions: BuildExclusionsBuildItem) {
    for (annotation in index.getAnnotations(WORKER)) {
      val owner = annotation.target().asMethod().declaringClass()
      if (exclusions.isExcluded(owner) || owner.isInterface || Modifier.isAbstract(owner.flags().toInt())) continue
      val ownerClass = load(owner.name().toString())
      require(workerClasses.any { ownerClass.isAssignableFrom(it) }) {
        "Annotated worker has no supported CDI-managed bean: ${owner.name()}"
      }
    }
  }

  private fun registerReflection(
    type: Class<*>?, index: IndexView, reflection: BuildProducer<ReflectiveClassBuildItem>,
    hierarchies: BuildProducer<ReflectiveHierarchyBuildItem>,
  ) {
    if (type == null || type == Any::class.java) return
    reflection.produce(ReflectiveClassBuildItem.builder(type.name).methods().fields().constructors().build())
    val indexed = index.getClassByName(DotName.createSimple(type.name))
    if (indexed != null) {
      for (method in indexed.methods()) {
        for (parameter in method.parameterTypes()) {
          hierarchies.produce(ReflectiveHierarchyBuildItem.builder(parameter).index(index)
            .source("Process engine worker ${type.name}").build())
        }
        if (!method.isConstructor) hierarchies.produce(ReflectiveHierarchyBuildItem.builder(method.returnType())
          .index(index).source("Process engine worker ${type.name}").build())
      }
    }
    registerReflection(type.superclass, index, reflection, hierarchies)
    for (parent in type.interfaces) registerReflection(parent, index, reflection, hierarchies)
  }

  private fun registerConverters(index: IndexView, reflection: BuildProducer<ReflectiveClassBuildItem>) {
    reflection.produce(ReflectiveClassBuildItem.builder(ProcessEngineWorker::class.java.name, Variable::class.java.name)
      .methods().build())
    for (variable in index.getAnnotations(DotName.createSimple(Variable::class.java.name))) {
      val converter = variable.value("converter")
      if (converter != null) reflection.produce(ReflectiveClassBuildItem.builder(converter.asClass().name().toString())
        .constructors().methods().build())
    }
  }

  private fun load(name: String): Class<*> = try {
    Class.forName(name, false, Thread.currentThread().contextClassLoader)
  } catch (failure: ClassNotFoundException) {
    throw IllegalArgumentException("Cannot inspect worker bean $name", failure)
  }

  companion object {
    private val WORKER = DotName.createSimple(ProcessEngineWorker::class.java.name)
  }
}
