/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring configuration class for CORS (Cross-Origin Resource Sharing). ║
 * ║                                                                              ║
 * ║  WHY:  During development, the React frontend runs on http://localhost:5173  ║
 * ║        and the Spring Boot backend runs on http://localhost:8080.  These     ║
 * ║        are different ORIGINS (different ports = different origins).           ║
 * ║                                                                              ║
 * ║        Browsers block cross-origin requests by default for security.         ║
 * ║        Without this config, every fetch() call from React to Spring Boot    ║
 * ║        would fail with a CORS error in the browser console.                 ║
 * ║                                                                              ║
 * ║        This class tells Spring: "Allow requests from localhost:5173."        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add more allowed origins for staging/production URLs.              ║
 * ║        - Restrict allowed methods (e.g., remove DELETE if not needed).      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * @Configuration — Tells Spring this class contains configuration beans.
 *   Spring reads it on startup and applies the settings.
 *
 * WebMvcConfigurer — An interface that lets us customize Spring MVC behavior
 *   by overriding specific methods.  We only override addCorsMappings here.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")             // Apply CORS to all /api/* endpoints.
            .allowedOrigins("http://localhost:5173")  // The Vite dev server origin.
            .allowedMethods("GET", "POST", "PUT", "DELETE")  // HTTP methods to allow.
            .allowedHeaders("*");              // Allow all request headers.
    }
}
