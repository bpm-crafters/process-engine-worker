package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpringJacksonConverterConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ProcessEngineWorkerAutoConfiguration.class));

    @Test
    void prefersTheConfiguredJackson3MapperWhenBothMappersAreAvailable() {
        context.withBean(ObjectMapper.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build())
                .run(application -> {
                    assertThat(application).hasSingleBean(VariableConverter.class);
                    final var converter = application.getBean(VariableConverter.class);
                    assertThat(converter).isInstanceOf(Jackson3VariableConverter.class);
                    assertThat(converter.mapToType(Map.of("first_name", "Ada"), Person.class))
                            .isEqualTo(new Person("Ada"));
                });
    }

    @Test
    void usesJackson3WithoutJackson2OnTheClasspath() {
        context.withClassLoader(new FilteredClassLoader("com.fasterxml.jackson.databind"))
                .withBean(JsonMapper.class, JsonMapper::new)
                .run(application -> assertThat(application.getBean(VariableConverter.class))
                        .isInstanceOf(Jackson3VariableConverter.class));
    }

    @Test
    void usesTheConfiguredJackson2MapperWithoutJackson3OnTheClasspath() {
        context.withClassLoader(new FilteredClassLoader("tools.jackson"))
                .withBean(ObjectMapper.class)
                .run(application -> {
                    assertThat(application).hasSingleBean(ObjectMapper.class);
                    assertThat(application.getBean(VariableConverter.class)).isInstanceOf(Jackson2VariableConverter.class);
                });
    }

    @Test
    void leavesTheDefaultUnconfiguredWhenThereIsNoMapperBean() {
        context.run(application -> assertThat(application).doesNotHaveBean(VariableConverter.class));
    }

    @Test
    void backsOffWhenTheApplicationProvidesItsOwnConverter() {
        final var converter = mock(VariableConverter.class);
        context.withBean(ObjectMapper.class)
                .withBean(JsonMapper.class, JsonMapper::new)
                .withBean(VariableConverter.class, () -> converter)
                .run(application -> {
                    assertThat(application).hasSingleBean(VariableConverter.class);
                    assertThat(application.getBean(VariableConverter.class)).isSameAs(converter);
                });
    }

    public record Person(String firstName) {}
}
