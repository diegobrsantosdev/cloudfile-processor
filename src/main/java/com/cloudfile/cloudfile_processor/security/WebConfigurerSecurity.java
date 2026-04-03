package com.cloudfile.cloudfile_processor.security;

import com.cloudfile.cloudfile_processor.config.RolePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
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
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
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
}
