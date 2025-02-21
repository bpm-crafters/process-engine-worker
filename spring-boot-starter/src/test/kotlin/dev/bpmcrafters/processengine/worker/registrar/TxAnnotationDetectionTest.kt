package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

internal class TxAnnotationDetectionTest {

  @Test
  fun `detect spring tx annotations`() {
    // class level
    SpringTxAnnotatedClazz::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzRequiresNew::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzSupports::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzMandatory::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzNever::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedClazzNotSupported::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedClazzNested::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // method level
    SpringTxAnnotatedMethod::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodRequiresNew::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodSupports::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodMandatory::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodNever::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedMethodNotSupported::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedMethodNested::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // none
    PlainComponent::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
  }

  @Test
  fun `detects jakarta tx annotations`() {
    // class level
    JakartaTxAnnotatedClazz::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzRequiresNew::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzSupports::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzMandatory::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzNever::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    JakartaTxAnnotatedClazzNotSupported::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // method level
    JakartaTxAnnotatedMethod::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodRequiresNew::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodSupports::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodMandatory::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodNever::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    JakartaTxAnnotatedMethodNotSupported::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // none
    PlainComponent::class.java.declaredMethods.first().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

  }

  @Component
  @Transactional
  class SpringTxAnnotatedClazz {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  class SpringTxAnnotatedClazzRequiresNew {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.SUPPORTS)
  class SpringTxAnnotatedClazzSupports {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.MANDATORY)
  class SpringTxAnnotatedClazzMandatory {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.NEVER)
  class SpringTxAnnotatedClazzNever {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  class SpringTxAnnotatedClazzNotSupported {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @Transactional(propagation = Propagation.NESTED)
  class SpringTxAnnotatedClazzNested {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethod {
    @Transactional
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodRequiresNew {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodSupports {
    @Transactional(propagation = Propagation.SUPPORTS)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodMandatory {
    @Transactional(propagation = Propagation.MANDATORY)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodNever {
    @Transactional(propagation = Propagation.NEVER)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodNotSupported {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class SpringTxAnnotatedMethodNested {
    @Transactional(propagation = Propagation.NESTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @jakarta.transaction.Transactional
  class JakartaTxAnnotatedClazz {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
  @Component
  class JakartaTxAnnotatedClazzRequiresNew {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.SUPPORTS)
  class JakartaTxAnnotatedClazzSupports {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
  class JakartaTxAnnotatedClazzMandatory {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
  class JakartaTxAnnotatedClazzNever {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
  class JakartaTxAnnotatedClazzNotSupported {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class JakartaTxAnnotatedMethod {
    @jakarta.transaction.Transactional
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class JakartaTxAnnotatedMethodRequiresNew {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class JakartaTxAnnotatedMethodSupports {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.SUPPORTS)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class JakartaTxAnnotatedMethodMandatory {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
    @ProcessEngineWorker("")
    fun execute() {

    }
  }

  @Component
  class JakartaTxAnnotatedMethodNever {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class JakartaTxAnnotatedMethodNotSupported {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @Component
  class PlainComponent {
    @ProcessEngineWorker("")
    fun execute() {}
  }
}
