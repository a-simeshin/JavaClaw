package ai.javaclaw.api.chat.error;

import ai.javaclaw.api.chat.controller.ChatRestController;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Order(0)
@ControllerAdvice(basePackageClasses = ChatRestController.class)
public class SseExceptionHandler {

    @ExceptionHandler(IOException.class)
    public void handleClientAbort(IOException ex) {
        log.debug("SSE client aborted: {}", ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("SSE async request timed out");
    }
}
