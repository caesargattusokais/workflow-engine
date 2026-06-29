# USQL JDBC 对接 — 设计文档

**日期**: 2026-06-29  
**分支**: 4.1.0  
**状态**: 已批准

## 目标

用 usql-jdbc 替换 workflow-engine 中的 mysql-connector-j，实现 SQL 透明编译能力。应用层代码不改，SQL 经 USQL 编译器翻译后执行。

## 架构

```
workflow-engine (JdbcTemplate)
       │
       ▼
USqlDataSource (包装 HikariCP DataSource)
       │
       ▼
USqlConnection → USqlStatement
       │
       ▼
USQL Compiler: SQL 文本 → 编译 → MySQL 方言 SQL
       │
       ▼
真实 MySQL Connection (mysql-connector-j, 由 usql-jdbc 传递)
       │
       ▼
MySQL 数据库
```

## 改动清单

### 1. usql 项目

| 文件 | 改动 |
|------|------|
| `usql/pom.xml` | Java 版本 21 → 17 |

然后 `mvn install -DskipTests` 安装到本地 Maven 仓库。

### 2. workflow-engine 项目

| 文件 | 改动 |
|------|------|
| `workflow-engine-server/pom.xml` | `mysql-connector-j` → `usql-jdbc` 依赖 |
| `workflow-engine-server/.../config/UsqlConfig.java` | **新增** BeanPostProcessor |
| `application.yml` | **不改** — 连接串/驱动类保持 `jdbc:mysql://...` + `com.mysql.cj.jdbc.Driver` |

### 3. UsqlConfig 实现

```java
@Configuration
public class UsqlConfig {
    @Bean
    static BeanPostProcessor usqlWrapper(Environment env) {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof DataSource ds && !(bean instanceof USqlDataSource)) {
                    String url = env.getProperty("spring.datasource.url");
                    return new USqlDataSource(ds, USqlDriver.detectDialect(url));
                }
                return bean;
            }
        };
    }
}
```

## 兼容性

- **Java**: usql 使用的语言特性（record, sealed interface, switch arrow）均为 Java 17 正式特性，无需代码改动
- **SQL**: workflow-engine 的 Jdbc*Repository 使用标准 INSERT/SELECT/UPDATE/DELETE，U-SQL 解析器可正常处理
- **连接池**: HikariCP 在 USqlDataSource 包装层下方正常工作
- **Redis 配置**: 无影响，Redis 配置与 JDBC 无关
