package ai.javaclaw.agent.pipeline;

/**
 * Выбрасывается когда все модели в fallback-цепочке исчерпаны и ни одна не дала успешный ответ.
 */
public class ModelFallbackExhaustedException extends RuntimeException {

    public ModelFallbackExhaustedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
