package ai.javaclaw.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React SPA bundle from {@code classpath:/static/} and falls back to
 * {@code index.html} for any non-API path that does not resolve to an existing
 * static resource. API ({@code /api/**}) and actuator ({@code /actuator/**})
 * requests are never remapped — they fall through to their respective
 * controllers.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";
    private static final String INDEX_HTML = "index.html";

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
    }

    private static final class SpaPathResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                return null;
            }
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null && requested.exists() && requested.isReadable()) {
                return requested;
            }
            // SPA fallback — let the React router render the route client-side.
            Resource index = location.createRelative(INDEX_HTML);
            if (index.exists() && index.isReadable()) {
                return index;
            }
            return null;
        }
    }
}
