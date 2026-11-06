package cd.gouv.finances.signalements.config;

import cd.gouv.finances.signalements.access.security.AccountSessionFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PlatformProperties platformProperties;
    private final ObjectMapper objectMapper;

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return http
                .addFilterBefore(new AccountSessionFilter(userDetailsService), AuthorizationFilter.class)
                .csrf(csrfSpec -> csrfSpec
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                write(response, 401, "rejected-credentials", "Identifiants refusés."))
                        .accessDeniedHandler((request, response, exception) ->
                                write(response, 403, "forbidden", "Cette action n'est pas autorisée.")))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers("/api/availability", "/actuator/health").permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll();
                    requests.requestMatchers(HttpMethod.POST, "/api/auth").permitAll();
                    if (platformProperties.documentationOpen()) {
                        requests.requestMatchers("/scalar", "/scalar/**", "/v3/api-docs", "/v3/api-docs/**")
                                .permitAll();
                    }
                    requests.requestMatchers(HttpMethod.DELETE, "/api/auth").authenticated();
                    requests.requestMatchers(HttpMethod.PUT, "/api/accounts/*/password").authenticated();
                    requests.requestMatchers("/api/accounts/**").hasRole("ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.GET, "/api/**")
                            .hasAnyRole("AGENT", "SUPERVISOR", "AUDITOR", "ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.POST, "/api/**")
                            .hasAnyRole("AGENT", "SUPERVISOR", "ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.PUT, "/api/**")
                            .hasAnyRole("AGENT", "SUPERVISOR", "ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.PATCH, "/api/**")
                            .hasAnyRole("AGENT", "SUPERVISOR", "ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.DELETE, "/api/**")
                            .hasAnyRole("AGENT", "SUPERVISOR", "ADMINISTRATOR");
                    requests.anyRequest().authenticated();
                })
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(platformProperties.frontendOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
