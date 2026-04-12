package ai.javaclaw.e2e.support;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Resolves active Spring profiles for E2E tests based on {@code e2e.dialect} system property.
 *
 * <ul>
 *   <li>{@code -De2e.dialect=sqlite} → profiles {@code ["e2e", "sqlite"]}</li>
 *   <li>{@code -De2e.dialect=postgres} (or unset) → profile {@code ["e2e"]}</li>
 * </ul>
 */
public class E2EProfileResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        if ("sqlite".equals(System.getProperty("test.dialect"))) {
            return new String[] {"e2e", "sqlite"};
        }
        return new String[] {"e2e"};
    }
}
