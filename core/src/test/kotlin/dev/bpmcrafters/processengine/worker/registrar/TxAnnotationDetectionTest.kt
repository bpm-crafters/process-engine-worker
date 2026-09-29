package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Method
import kotlin.test.Test

internal class TxAnnotationDetectionTest {

  @Test
  fun `detect spring tx annotations`() {
    // class level
    SpringTxAnnotatedClazz::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzRequiresNew::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzSupports::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzMandatory::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedClazzNever::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedClazzNotSupported::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedClazzNested::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // method level
    SpringTxAnnotatedMethod::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodRequiresNew::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodSupports::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodMandatory::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    SpringTxAnnotatedMethodNever::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedMethodNotSupported::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    SpringTxAnnotatedMethodNested::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // none
    PlainComponent::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
  }

  @Test
  fun `detects jakarta tx annotations`() {
    // class level
    JakartaTxAnnotatedClazz::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzRequiresNew::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzSupports::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzMandatory::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedClazzNever::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    JakartaTxAnnotatedClazzNotSupported::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // method level
    JakartaTxAnnotatedMethod::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodRequiresNew::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodSupports::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodMandatory::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isTrue()
    }
    JakartaTxAnnotatedMethodNever::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }
    JakartaTxAnnotatedMethodNotSupported::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

    // none
    PlainComponent::class.java.executeMethod().let { method ->
      assertThat(method.isTransactional()).isFalse()
    }

  }

    @Transactional
  class SpringTxAnnotatedClazz {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
  class SpringTxAnnotatedClazzRequiresNew {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.SUPPORTS)
  class SpringTxAnnotatedClazzSupports {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.MANDATORY)
  class SpringTxAnnotatedClazzMandatory {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.NEVER)
  class SpringTxAnnotatedClazzNever {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
  class SpringTxAnnotatedClazzNotSupported {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @Transactional(propagation = Propagation.NESTED)
  class SpringTxAnnotatedClazzNested {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethod {
    @Transactional
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodRequiresNew {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodSupports {
    @Transactional(propagation = Propagation.SUPPORTS)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodMandatory {
    @Transactional(propagation = Propagation.MANDATORY)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodNever {
    @Transactional(propagation = Propagation.NEVER)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodNotSupported {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class SpringTxAnnotatedMethodNested {
    @Transactional(propagation = Propagation.NESTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @jakarta.transaction.Transactional
  class JakartaTxAnnotatedClazz {
    @ProcessEngineWorker("")
    fun execute() {}
  }

  @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    class JakartaTxAnnotatedClazzRequiresNew {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.SUPPORTS)
  class JakartaTxAnnotatedClazzSupports {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
  class JakartaTxAnnotatedClazzMandatory {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
  class JakartaTxAnnotatedClazzNever {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
  class JakartaTxAnnotatedClazzNotSupported {
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class JakartaTxAnnotatedMethod {
    @jakarta.transaction.Transactional
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class JakartaTxAnnotatedMethodRequiresNew {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class JakartaTxAnnotatedMethodSupports {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.SUPPORTS)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class JakartaTxAnnotatedMethodMandatory {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
    @ProcessEngineWorker("")
    fun execute() {

    }
  }

    class JakartaTxAnnotatedMethodNever {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class JakartaTxAnnotatedMethodNotSupported {
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    @ProcessEngineWorker("")
    fun execute() {}
  }

    class PlainComponent {
    @ProcessEngineWorker("")
    fun execute() {}
  }
}

private fun Class<*>.executeMethod(): Method = getDeclaredMethod("execute")
