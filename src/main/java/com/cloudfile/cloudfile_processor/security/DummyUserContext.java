package com.cloudfile.cloudfile_processor.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
//@Profile("!prod")
public class DummyUserContext implements UserContext{

    @Override
    public String getUserId() {
        return "user18";
    }
}
