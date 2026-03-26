package com.cloudfile.cloudfile_processor.security;

import com.cloudfile.cloudfile_processor.exceptions.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
//@Profile("!prod")
public class DummyUserContext implements UserContext{

    @Override
    public String getUserId() {
        String userId = "user17";

        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Invalid user context");
        }
        return userId;
    }
}
