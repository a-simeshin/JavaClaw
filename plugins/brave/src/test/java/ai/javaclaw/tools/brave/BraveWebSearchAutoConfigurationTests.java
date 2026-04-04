package ai.javaclaw.tools.brave;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.tools.AutoDiscoveredTool;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class BraveWebSearchAutoConfigurationTests {

    @Test
    void braveBeanIsCreatedWhenApiKeyPropertyIsSet() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BraveWebSearchAutoConfiguration.class))
                .withPropertyValues("agent.browser.brave.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AutoDiscoveredTool.class);
                    Map<String, AutoDiscoveredTool> autoDiscoveredTools =
                            context.getBeansOfType(AutoDiscoveredTool.class);
                    Assertions.assertThat(autoDiscoveredTools)
                            .hasSize(1)
                            .containsKey("autoDiscoveredBraveWebSearchTool");
                });
    }

    @Test
    void braveBeanIsAbsentWhenApiKeyPropertyIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BraveWebSearchAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(AutoDiscoveredTool.class));
    }
}
