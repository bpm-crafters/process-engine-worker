package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ClasspathResourcePatternTest {
  @Test
  fun doubleStarIncludesRootAndEveryDepth() {
    val pattern = ClasspathResourcePattern("classpath*:/**/*.bpmn")
    assertThat(pattern.all()).isTrue()
    assertThat(pattern.matches("root.bpmn")).isTrue()
    assertThat(pattern.matches("nested/process.bpmn")).isTrue()
    assertThat(pattern.matches("a/b/process.bpmn")).isTrue()
    assertThat(pattern.matches("process.dmn")).isFalse()
  }

  @Test
  fun singleStarAndQuestionMarkStayWithinOneDirectory() {
    val pattern = ClasspathResourcePattern("classpath:/processes/order-?.bpmn")
    assertThat(pattern.all()).isFalse()
    assertThat(pattern.matches("processes/order-1.bpmn")).isTrue()
    assertThat(pattern.matches("processes/order-11.bpmn")).isFalse()
    assertThat(ClasspathResourcePattern("classpath:processes/*.bpmn").matches("processes/nested/order.bpmn")).isFalse()
  }

  @Test
  fun regexCharactersAreLiteralAndDoubleStarCanMatchZeroDirectories() {
    val pattern = ClasspathResourcePattern("classpath*:a/**/order(1).bpmn")
    assertThat(pattern.matches("a/order(1).bpmn")).isTrue()
    assertThat(pattern.matches("a/b/order(1).bpmn")).isTrue()
    assertThat(pattern.matches("a/order1.bpmn")).isFalse()
  }

  @Test
  fun rejectsUnsupportedSchemesAndUnsafePaths() {
    for (value in listOf(
      "file:/*.bpmn",
      "https://example.org/p.bpmn",
      "*.bpmn",
      "classpath:",
      "classpath:../secret",
      "classpath:a\\b.bpmn",
      "classpath:http:foo"
    )) {
      assertThatThrownBy { ClasspathResourcePattern(value) }.isInstanceOf(IllegalArgumentException::class.java)
    }
  }
}
