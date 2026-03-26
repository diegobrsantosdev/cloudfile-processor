////package com.cloudfile.cloudfile_processor.security;
////
////import org.springframework.context.annotation.Profile;
////import org.springframework.stereotype.Component;
////
//@Component
//@Profile("prod")
//public class JwtUserContext implements UserContext {
//
//    @Override
//    public String getUserId() {
//        var authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
//            String userId = jwtAuth.getToken().getSubject();
//
//            if (userId == null || userId.isBlank()) {
//                throw new UnauthorizedException("Invalid user context");
//            }
//
//            return userId;
//        }
//
//        throw new UnauthorizedException("User not authenticated");
//    }
//}
