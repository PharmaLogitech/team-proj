/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring MVC configuration — general web settings.                     ║
 * ║                                                                              ║
 * ║  NOTE ON CORS:                                                               ║
 * ║        CORS is now configured INSIDE SecurityConfig.java via Spring          ║
 * ║        Security's CorsConfigurationSource bean.  When Spring Security is    ║
 * ║        active, its CORS filter runs BEFORE MVC's CORS filter.  If we        ║
 * ║        configured CORS in BOTH places, one would silently override the      ║
 * ║        other, causing hard-to-debug failures.                               ║
 * ║                                                                              ║
 * ║        The old addCorsMappings() method has been removed to avoid this      ║
 * ║        conflict.  All CORS settings now live in:                            ║
 * ║          SecurityConfig.corsConfigurationSource()                           ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Use this class for non-security MVC settings like:                   ║
 * ║        - Custom message converters                                          ║
 * ║        - View resolvers                                                     ║
 * ║        - Static resource handlers                                           ║
 * ║        - Interceptors (logging, timing)                                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * @Configuration — Tells Spring this class contains configuration beans.
 *
 * WebMvcConfigurer — An interface that lets us customize Spring MVC behavior
 *   by overriding specific methods.  Currently empty because CORS has moved
 *   to SecurityConfig, but kept as a placeholder for future MVC customisation.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    /*
     * CORS has been moved to SecurityConfig.corsConfigurationSource().
     * See SecurityConfig.java for the full CORS configuration and comments
     * explaining why it must live inside the security filter chain.
     */
}
