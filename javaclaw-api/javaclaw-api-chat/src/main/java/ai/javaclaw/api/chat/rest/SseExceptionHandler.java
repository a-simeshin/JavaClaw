package ai.javaclaw.api.chat.rest;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * Suppresses the noisy SSE-related exceptions that Spring MVC logs by default:
 * client aborts (HTTP/2 close → IOException), async request timeouts, etc.
 *
 * <p>See: Spring #33340, #33421, #33832, Boot #14237.
 */
@Order(0)
@ControllerAdvice(basePackageClasses = ChatRestController.class)
public class SseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SseExceptionHandler.class);

    @ExceptionHandler(IOException.class)
    public void handleClientAbort(IOException ex) {
        log.debug("SSE client aborted: {}", ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("SSE async request timed out");
    }
}
