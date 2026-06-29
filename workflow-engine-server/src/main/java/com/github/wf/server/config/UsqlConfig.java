package com.github.wf.server.config;

import com.usql.jdbc.USqlDataSource;
import com.usql.jdbc.USqlDriver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * USQL JDBC integration — wraps the Spring Boot auto-configured DataSource
 * (HikariCP) with USqlDataSource so all SQL is transparently compiled
 * through the USQL compiler before execution.
 *
 * application.yml requires NO changes — the original jdbc:mysql:// URL
 * and com.mysql.cj.jdbc.Driver class are used to detect the dialect
 * and create the underlying connection pool.
 */
@Configuration
public class UsqlConfig {

    @Bean
    static BeanPostProcessor usqlWrapper(Environment env) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof DataSource ds && !(bean instanceof USqlDataSource)) {
                    String url = env.getProperty("spring.datasource.url");
                    if (url != null) {
                        return new USqlDataSource(ds, USqlDriver.detectDialect(url));
                    }
                }
                return bean;
            }
        };
    }
}
