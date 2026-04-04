package ai.javaclaw.api.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated Superseded by {@code ChatRestController} + React SPA. The Pebble
 *     {@code /chat} view is retained only for the legacy htmx fallback.
 */
@Deprecated
@Controller
public class ChatController {

    @Value("${jobrunr.dashboard.port:8081}")
    private int jobrunrDashboardPort;

    @GetMapping("/legacy/chat")
    public String chat(Model model) {
        model.addAttribute("jobrunrDashboardPort", jobrunrDashboardPort);
        return "chat";
    }
}
