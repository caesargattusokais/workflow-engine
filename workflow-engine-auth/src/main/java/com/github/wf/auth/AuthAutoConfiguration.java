package com.github.wf.auth;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/** Auto-wires the auth module when it is on the classpath. */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(UserStore.class)
    public UserStore userStore(AuthProperties props) {
        return new InMemoryUserStore(props);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(TokenService.class)
    public TokenService tokenService(AuthProperties props) {
        return new TokenService(props);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(AuthService.class)
    public AuthService authService(UserStore store, TokenService tokens) {
        return new AuthService(store, tokens);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(AuthController.class)
    public AuthController authController(AuthService authService, AuthProperties props) {
        return new AuthController(authService, props);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(AuthenticationFilter.class)
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(AuthService authService, AuthProperties props) {
        AuthenticationFilter filter = new AuthenticationFilter(authService, props);
        FilterRegistrationBean<AuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
