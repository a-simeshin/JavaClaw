package ai.javaclaw.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated Superseded by the React SPA served via {@code SpaWebConfig}.
 *     The root path ({@code /}) is now served by the SPA fallback (index.html).
 *     The legacy Pebble redirect remains on {@code /index} for a transition
 *     period and will be removed once the SPA is the sole web UI.
 */
@Deprecated
@Controller
public class IndexController {

    @GetMapping("/index")
    public String index() {
        return "redirect:/chat";
    }
}
