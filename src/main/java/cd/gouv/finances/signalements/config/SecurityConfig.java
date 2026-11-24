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
                    requests.requestMatchers(HttpMethod.GET, "/api/auth").authenticated();
                    requests.requestMatchers(HttpMethod.DELETE, "/api/auth").authenticated();
                    requests.requestMatchers(HttpMethod.PUT, "/api/accounts/*/password").authenticated();
                    requests.requestMatchers(HttpMethod.GET, "/api/accounts").hasAuthority("user.read");
                    requests.requestMatchers(HttpMethod.POST, "/api/accounts").hasAuthority("user.create");
                    requests.requestMatchers(HttpMethod.PATCH, "/api/accounts/*").hasAuthority("user.update");
                    requests.requestMatchers(HttpMethod.DELETE, "/api/accounts/*").hasAuthority("user.delete");
                    requests.requestMatchers(HttpMethod.POST, "/api/accounts/*/unlock").hasAuthority("user.unlock");
                    requests.requestMatchers(HttpMethod.GET, "/api/access/roles").hasAuthority("rbac.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/reports/assignees").hasAuthority("report.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/reports", "/api/reports/**").hasAuthority("report.read");
                    requests.requestMatchers(HttpMethod.PUT, "/api/reports/*/assignment").hasAuthority("report.assign");
                    requests.requestMatchers(HttpMethod.POST, "/api/reports/*/assessment", "/api/reports/*/assessment/fiscal-status", "/api/reports/*/assessment/decision", "/api/reports/*/duplicates/*", "/api/reports/*/photos/*/requests", "/api/reports/*/photo-requests/*/links", "/api/reports/*/photo-requests/*/deliveries").hasAuthority("report.review");
                    requests.requestMatchers(HttpMethod.POST, "/api/reports/*/analysis", "/api/reports/*/analysis/start", "/api/reports/*/photos/*/readings").hasAuthority("report.analyze");
                    requests.requestMatchers(HttpMethod.POST, "/api/reports/*/supervision").hasAuthority("supervision.write");
                    requests.requestMatchers(HttpMethod.GET, "/api/report-imports", "/api/report-imports/**").hasAuthority("import.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/declarants/overview", "/api/taxpayers/overview").hasRole("ADMINISTRATOR");
                    requests.requestMatchers("/api/exception-regime", "/api/exception-regime/**").hasRole("ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.GET, "/api/dossier-payments").hasAuthority("payment.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/report-analysis/overview").hasAuthority("import.read");
                    requests.requestMatchers(HttpMethod.POST, "/api/report-analysis/retry").hasRole("ADMINISTRATOR");
                    requests.requestMatchers(HttpMethod.GET, "/api/taxpayers").hasAuthority("taxpayer.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/seller-files", "/api/seller-files/companies").hasAuthority("dossier.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/declarants").hasAuthority("declarant.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/letters", "/api/letters/**", "/api/letter-routes").hasAuthority("letter.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/declarant-messages").hasAuthority("message.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/document-models").hasAuthority("template.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/rewards").hasAnyAuthority("reward.read", "payment.execute");
                    requests.requestMatchers(HttpMethod.GET, "/api/payout-numbers", "/api/beneficiaries").hasAuthority("reward.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/payment-statements", "/api/payment-statements/**", "/api/payment-transfers").hasAuthority("payment.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/payment-samples", "/api/payment-samples/**").hasAuthority("sample.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/draws").hasAuthority("draw.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/reexaminations").hasAuthority("reexam.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/corrections").hasAuthority("correction.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/abuse-indices", "/api/reward-suspensions").hasAuthority("abuse.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/supervision").hasAuthority("supervision.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/indicators", "/api/campaign-reports").hasAuthority("indicator.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/campaign-budget", "/api/reward-settings").hasAuthority("setting.read");
                    requests.requestMatchers(HttpMethod.GET, "/api/audit").hasAuthority("audit.read");
                    requests.requestMatchers(HttpMethod.POST, "/api/report-imports", "/api/report-imports/*/retry").hasAuthority("import.run");
                    requests.requestMatchers(HttpMethod.POST, "/api/taxpayers", "/api/taxpayers/*/verifications").hasAuthority("taxpayer.verify");
                    requests.requestMatchers(HttpMethod.POST, "/api/letters", "/api/letters/weekly").hasAuthority("letter.prepare");
                    requests.requestMatchers(HttpMethod.POST, "/api/letters/*/decisions").hasAuthority("letter.validate");
                    requests.requestMatchers(HttpMethod.POST, "/api/letter-routes", "/api/letters/*/dispatches").hasAuthority("letter.dispatch");
                    requests.requestMatchers(HttpMethod.POST, "/api/letters/*/responses").hasAuthority("letter.response");
                    requests.requestMatchers(HttpMethod.POST, "/api/declarant-messages").hasAuthority("message.prepare");
                    requests.requestMatchers(HttpMethod.POST, "/api/declarant-messages/*/deliveries").hasAuthority("message.send");
                    requests.requestMatchers(HttpMethod.POST, "/api/document-models").hasAuthority("template.write");
                    requests.requestMatchers(HttpMethod.POST, "/api/rewards/*/reviews").hasAuthority("reward.approve");
                    requests.requestMatchers(HttpMethod.POST, "/api/rewards/*/transfers").hasAuthority("payment.execute");
                    requests.requestMatchers(HttpMethod.POST, "/api/beneficiaries").hasAuthority("beneficiary.verify");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-statements").hasAuthority("payment.prepare");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-statements/*/controls").hasAuthority("payment.control");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-statements/*/visas").hasAuthority("payment.visa");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-statements/*/authorizations").hasAuthority("payment.authorize");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-statements/*/transfers", "/api/payment-statements/*/attempts").hasAuthority("payment.execute");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-transfers/*/checks").hasAuthority("payment.check");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-transfers/tests").hasAuthority("payment.test");
                    requests.requestMatchers(HttpMethod.POST, "/api/payment-samples", "/api/payment-samples/lines/*/reviews").hasAuthority("sample.review");
                    requests.requestMatchers(HttpMethod.POST, "/api/draw-tickets", "/api/draws", "/api/draws/*/publications", "/api/draws/prizes/*/handover").hasAuthority("draw.manage");
                    requests.requestMatchers(HttpMethod.POST, "/api/reexaminations", "/api/reexaminations/*/decisions").hasAuthority("reexam.decide");
                    requests.requestMatchers(HttpMethod.POST, "/api/corrections").hasAuthority("correction.decide");
                    requests.requestMatchers(HttpMethod.POST, "/api/abuse-indices/*/notes", "/api/reward-suspensions").hasAuthority("abuse.write");
                    requests.requestMatchers(HttpMethod.POST, "/api/campaign-reports").hasAuthority("reporting.write");
                    requests.requestMatchers(HttpMethod.POST, "/api/campaign-budget", "/api/reward-settings").hasAuthority("setting.write");
                    requests.requestMatchers(HttpMethod.POST, "/api/sms/tests").hasAuthority("sms.test");
                    requests.anyRequest().denyAll();
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
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8);
        objectMapper.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
