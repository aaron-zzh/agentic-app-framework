---
level: Practice
layer: Product
purpose: 提供 AAF 模块创建和依赖管理的代码片段
status: published
version: 1.1.0
date: 2026-04-30
author: AaronZZH
scope:
  includes:
    - 依赖版本注册、业务包创建流程
gains:
  - 能快速在 aaf-api 中创建业务包
---

# 模块代码片段

## 注册依赖版本

在 `aaf-dependencies/pom.xml` 中注册：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>some-library</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 引入依赖

在 `aaf-api/pom.xml` 中引入（不写版本号）：

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>some-library</artifactId>
        <!-- 版本号从 aaf-dependencies 继承 -->
    </dependency>
</dependencies>
```

## 创建新业务包

在 `aaf-api/src/main/java/com/xuejiai/aaf/module/` 下新建包目录：

```bash
cd aaf-api/src/main/java/com/xuejiai/aaf/module
mkdir -p order/controller order/service order/repository order/entity order/vo
```

包结构：

```text
aaf-api/src/main/java/com/xuejiai/aaf/module/order/
├── controller/
├── service/
├── repository/
├── entity/
└── vo/
```

## 业务包 pom 无需修改

所有业务包共用 `aaf-api/pom.xml` 的依赖，无需额外配置。
如需引入特定依赖（如某业务包需要额外 SDK），直接在 `aaf-api/pom.xml` 中添加即可。
