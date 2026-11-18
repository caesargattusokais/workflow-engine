package com.github.wf.server.config;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.dingtalk.DingTalkOrgService;
import com.github.wf.ext.feishu.FeishuOrgService;
import com.github.wf.ext.ldap.LdapOrgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.Properties;

/**
 * OrgService beans — pluggable organization-directory backends.
 *
 * At most one production backend (LDAP / Feishu / DingTalk) is active,
 * selected purely by which property prefix is configured. The mock backend
 * is only active under the mock-ldap profile (for tests/demos) and takes
 * precedence via {@code @Primary}.
 */
@Configuration
public class OrgServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceConfig.class);

    @Bean
    @Profile("mock-ldap")
    @Primary
    public OrgService mockOrgService() {
        return new com.github.wf.mockldap.MockOrgService();
    }

    @Bean
    @ConditionalOnProperty("feishu.app-id")
    public OrgService feishuOrgService(Environment env) {
        return new FeishuOrgService(prefixProps(env, "feishu", "app-id", "app-secret"));
    }

    @Bean
    @ConditionalOnProperty("dingtalk.app-key")
    public OrgService dingTalkOrgService(Environment env) {
        return new DingTalkOrgService(prefixProps(env, "dingtalk", "app-key", "app-secret"));
    }

    @Bean
    @ConditionalOnProperty("ldap.url")
    public OrgService ldapOrgService(Environment env) {
        log.info("[OrgServiceConfig] Creating LdapOrgService — ldap.url={}", env.getProperty("ldap.url"));
        return new LdapOrgService(prefixProps(env, "ldap",
                "url", "base", "user", "password", "userFilter", "groupFilter",
                "groupMemberAttr", "uidAttr", "userObjectClass", "userBase", "groupBase"));
    }

    /** Collects "{prefix}.{key}" properties that exist into a Properties map (keys keep the full prefix). */
    private static Properties prefixProps(Environment env, String prefix, String... keys) {
        Properties p = new Properties();
        for (String key : keys) {
            String val = env.getProperty(prefix + "." + key);
            if (val != null) p.setProperty(prefix + "." + key, val);
        }
        return p;
    }
}
