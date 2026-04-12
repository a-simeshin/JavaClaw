package ai.javaclaw.persistence.dialect;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Minimal {@link ObjectProvider} stub for unit tests — wraps a single pre-built instance so
 * tests can construct JDBC configurations without spinning up a Spring context. All variants
 * of {@code getIfAvailable} / {@code getIfUnique} / {@code getObject} return the same value;
 * the instance is assumed to be non-null for straightforward unit-test scenarios.
 */
final class SingletonObjectProvider<T> implements ObjectProvider<T> {

    private final T instance;

    SingletonObjectProvider(T instance) {
        this.instance = instance;
    }

    @Override
    public T getObject() {
        return instance;
    }

    @Override
    public T getObject(Object... args) {
        return instance;
    }

    @Override
    public T getIfAvailable() {
        return instance;
    }

    @Override
    public T getIfUnique() {
        return instance;
    }
}
