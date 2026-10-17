package com.cloudfile.cloudfile_processor.security;

import com.cloudfile.cloudfile_processor.config.RolePermissions;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.util.List;

import static com.cloudfile.cloudfile_processor.config.Endpoints.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.oidc.enabled", havingValue = "true")
public class WebConfigurerSecurity {

    private final JwtProperties jwtProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                SWAGGER.getUrl(),
                                SWAGGER_API.getUrl(),
                                ACTUATOR.getUrl()
                        ).permitAll()

                        .requestMatchers(
                                FILE_UPLOAD.getUrl(),
                                FILE_LIST.getUrl(),
                                FILE_HISTORY.getUrl(),
                                FILE_DOWNLOAD_BY_ID.getUrl(),
                                FILE_DELETE_BY_ID.getUrl()
                        ).hasRole(RolePermissions.USER.getRole())

                        .requestMatchers(
                                ADMIN_LIST_USERS.getUrl(),
                                ADMIN_LIST_USER_FILES.getUrl(),
                                ADMIN_FILE_REPROCESS.getUrl(),
                                ADMIN_FILE_DELETE_BY_ID.getUrl()
                        ).hasRole(RolePermissions.ADMIN.getRole())

                        .anyRequest().authenticated()
                )

                // 401 and 403 raised by the security filters never reach the
                // GlobalExceptionHandler, so they are answered here in the same
                // ErrorResponse format the rest of the API uses.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler())
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler())
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter groupsConverter = new JwtGrantedAuthoritiesConverter();
        groupsConverter.setAuthoritiesClaimName("cognito:groups");
        groupsConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String clientId = jwt.getClaimAsString("client_id");
            if (!jwtProperties.getClientId().equals(clientId)) {
                throw new JwtValidationException("Invalid client_id", List.of());
            }

            return groupsConverter.convert(jwt);
        });

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwksUri = jwtProperties.getIssuerUri() + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwksUri)
                .build();

        // validate issuer manually
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuerUri());

        decoder.setJwtValidator(issuerValidator);
        return decoder;
    }

    // Only needed for local development (Angular on localhost:4200).
    // In the cloud the front-end and the API share the CloudFront origin.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Missing, expired or invalid token -> 401 with ErrorResponse body. */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, ex) ->
                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    /** Valid token but missing role (e.g. a regular user calling an admin route) -> 403. */
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, ex) ->
                writeError(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    /** Writes the same JSON shape as ErrorResponse: {"error": "...", "fields": null}. */
    private static void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\",\"fields\":null}");
    }
}