package ai.javaclaw.integration;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Resolves active Spring profiles for integration tests based on {@code test.dialect}.
 *
 * <ul>
 *   <li>{@code -Dtest.dialect=sqlite} → profiles {@code ["contracttest", "sqlite"]}</li>
 *   <li>{@code -Dtest.dialect=postgres} (or unset) → profile {@code ["contracttest"]}</li>
 * </ul>
 */
public class IntegrationTestProfileResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        if ("sqlite".equals(System.getProperty("test.dialect"))) {
            return new String[] {"contracttest", "sqlite"};
        }
        return new String[] {"contracttest"};
    }
}
