package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security configuration for Majordomo.
 *
 * <p>Configures form-based login at {@code /login}, OAuth2 login with Google,
 * API key authentication via the {@code X-API-Key} header, permits public access
 * to the root URL and Swagger UI, and requires authentication for all API
 * endpoints.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyRepository apiKeyRepository;
    private final OAuth2UserService oAuth2UserService;

    /**
     * Constructs the security configuration.
     *
     * @param apiKeyRepository  the outbound port for API key lookups
     * @param oAuth2UserService the custom service that links OAuth2 identities to users
     */
    public SecurityConfig(ApiKeyRepository apiKeyRepository,
                          OAuth2UserService oAuth2UserService) {
        this.apiKeyRepository = apiKeyRepository;
        this.oAuth2UserService = oAuth2UserService;
    }

    /**
     * Defines the HTTP security filter chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(apiKeyAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService))
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Use the plain handler instead of the BREACH-mitigating
                // XorCsrfTokenRequestAttributeHandler so the value sent back
                // in the X-XSRF-TOKEN header / _csrf form param matches the
                // raw value stored in the XSRF-TOKEN cookie. Required for
                // curl-based scripts (smoke test, ad-hoc API probing) and
                // any non-browser client. The browser flow still works via
                // Thymeleaf-rendered _csrf inputs. Trade-off: no BREACH
                // protection on CSRF token transport — acceptable for a
                // localhost-by-default personal-management app.
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            );

        return http.build();
    }

    /**
     * Creates the API key authentication filter.
     *
     * @return a new {@link ApiKeyAuthenticationFilter} instance
     */
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter(apiKeyRepository);
    }

    /**
     * Provides Argon2id password encoder for credential verification.
     *
     * @return an {@link Argon2PasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
