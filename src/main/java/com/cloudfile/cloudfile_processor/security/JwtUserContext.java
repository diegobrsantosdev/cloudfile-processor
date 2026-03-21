//package com.cloudfile.cloudfile_processor.security;
//
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//
//@Component
//@Profile("prod")
//public class JwtUserContext implements UserContext{
//
//    @Override
//    public String getUserId() {
//        var authentication = SecurityContextHolder.getContext().getAuthentication();
//        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
//
//            // uses "sub" of JWT, (cognito ID)
//            return jwtAuth.getToken().getSubject();
//        }
//        throw new IllegalStateException("User not authenticated");
//    }
//
//}
