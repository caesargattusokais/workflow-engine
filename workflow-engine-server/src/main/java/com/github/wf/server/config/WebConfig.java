package com.github.wf.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserIdInterceptor userIdInterceptor;

    public WebConfig(UserIdInterceptor userIdInterceptor) {
        this.userIdInterceptor = userIdInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/auth/** is handled by the AuthenticationFilter (login is public;
        // me/logout validate the session token). Exclude it from the legacy
        // header check so a browser without X-User-Id can still log in.
        registry.addInterceptor(userIdInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
