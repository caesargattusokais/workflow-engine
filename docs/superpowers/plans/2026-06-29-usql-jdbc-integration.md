# USQL JDBC 对接 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 usql-jdbc 替换 workflow-engine 中的 mysql-connector-j，通过 BeanPostProcessor 包装 DataSource 实现 SQL 透明编译。

**Architecture:** Spring Boot 自动配置的 HikariCP DataSource 被 `USqlDataSource` 包装，所有 SQL 经 USQL 编译器翻译后转发到真实 MySQL Connection。usql-jdbc 内部已包含 mysql-connector-j，无需额外声明。

**Tech Stack:** Java 17, Maven, Spring Boot 3.3, usql-core + usql-jdbc

## Global Constraints

- Java 版本: 17 (usql 和 workflow-engine 统一)
- usql-jdbc 版本: 1.0.0-SNAPSHOT (本地 Maven 仓库安装)
- application.yml 不改动 — 连接串和驱动类保持原样
- 连接池 HikariCP 在 USqlDataSource 包装层下方正常工作

---

### Task 1: usql — 降 Java 版本并安装到本地仓库

**Files:**
- Modify: `D:\usql\pom.xml:23`

- [ ] **Step 1: 修改 Java 版本 21 → 17**

在 `D:\usql\pom.xml` 中，将 `<java.version>21</java.version>` 改为 `<java.version>17</java.version>`：

```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

- [ ] **Step 2: 编译并安装到本地 Maven 仓库**

```bash
cd D:\usql && mvn install -DskipTests
```

Expected: BUILD SUCCESS，usql-core 和 usql-jdbc 安装到 `~/.m2/repository/com/usql/`

- [ ] **Step 3: 提交 usql 改动**

```bash
cd D:\usql && git add pom.xml && git commit -m "chore: downgrade Java version from 21 to 17 for workflow-engine compatibility"
```

---

### Task 2: workflow-engine — 替换 Maven 依赖

**Files:**
- Modify: `workflow-engine-server/pom.xml:39-42`

- [ ] **Step 1: 替换 MySQL 驱动依赖为 usql-jdbc**

在 `workflow-engine-server/pom.xml` 中，将：

```xml
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
```

替换为：

```xml
        <dependency>
            <groupId>com.usql</groupId>
            <artifactId>usql-jdbc</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
```

说明：usql-jdbc 的 pom.xml 已声明 `mysql-connector-j:8.3.0` 为 runtime 依赖，MySQL 驱动会自动传递引入，无需显式声明。

- [ ] **Step 2: 验证依赖解析**

```bash
cd D:\workflow-engine && mvn dependency:tree -pl workflow-engine-server
```

Expected: 输出中包含 `com.usql:usql-jdbc:1.0.0-SNAPSHOT` 及其传递依赖 `com.mysql:mysql-connector-j:8.3.0`

---

### Task 3: workflow-engine — 新增 UsqlConfig 配置类

**Files:**
- Create: `workflow-engine-server/src/main/java/com/github/wf/server/config/UsqlConfig.java`

- [ ] **Step 1: 创建 UsqlConfig.java**

```java
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
```

- [ ] **Step 2: 编译验证**

```bash
cd D:\workflow-engine && mvn compile -pl workflow-engine-server
```

Expected: BUILD SUCCESS，UsqlConfig 编译通过

---

### Task 4: 全量编译验证

- [ ] **Step 1: 完整构建 workflow-engine**

```bash
cd D:\workflow-engine && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS，所有模块编译打包成功

- [ ] **Step 2: 提交 workflow-engine 改动**

```bash
cd D:\workflow-engine
git add workflow-engine-server/pom.xml workflow-engine-server/src/main/java/com/github/wf/server/config/UsqlConfig.java
git commit -m "feat: integrate usql-jdbc, replace mysql-connector-j with USQL DataSource wrapper"
```
