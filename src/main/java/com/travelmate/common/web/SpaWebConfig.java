package com.travelmate.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled Flutter web client (baked into {@code classpath:/static/} by the Docker build)
 * and falls back to {@code index.html} for client-side (go_router) deep links, so refreshing on a
 * route like {@code /trips/abc/timeline} still loads the app. API paths ({@code /api/**}) and real
 * asset files are never rewritten — only unknown, file-less paths fall through to the SPA shell.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        final Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Let API calls 404 normally; only HTML routes fall back to the SPA shell.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        final Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
