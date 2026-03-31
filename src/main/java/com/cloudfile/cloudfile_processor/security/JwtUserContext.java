package com.cloudfile.cloudfile_processor.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.cloudfile.cloudfile_processor.exceptions.UnauthorizedException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class JwtUserContext implements UserContext {

    @Override
    public String getUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getSubject();

            if (userId == null || userId.isBlank()) {
                throw new UnauthorizedException("Invalid user context");
            }

            return userId;
        }

        throw new UnauthorizedException("User not authenticated");
    }
}
