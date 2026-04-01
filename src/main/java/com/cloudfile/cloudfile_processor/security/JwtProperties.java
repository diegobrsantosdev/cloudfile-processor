package com.cloudfile.cloudfile_processor.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class JwtProperties {

    @Value("${cognito.client-id}")
    private String clientId;

    @Value("${cognito.issuer-uri}")
    private String issuerUri;
}
