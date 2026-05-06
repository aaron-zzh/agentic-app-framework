# 如何创建新业务包

AAF 采用单模块分包架构，所有业务代码在 `aaf-api` 模块内按包隔离开发。

## 决策树：是否需要创建新业务包？

```text
N1. 这段逻辑是否属于已有业务包的职责范围？
 ├─ 是 → 不创建，放入已有包
 └─ 否/不确定 → N2

N2. 是否有独立的数据库表（≥1 张专属表）？
 ├─ 是 → N3
 └─ 否 → 大概率不需要独立包，考虑放入最相关的已有包

N3. 是否有独立的 REST API 路径前缀（如 /api/orders）？
 ├─ 是 → 创建新业务包
 └─ 否 → 作为已有包的子功能，不独立建包
```

## 前提条件

- 已完成开发环境搭建
- 了解 Spring Boot 基础
- 了解 Spring Data JPA 基础

## 创建步骤

### 1. 创建包目录

在 `aaf-api/src/main/java/com/xuejiai/aaf/module/` 下创建业务包：

```bash
cd aaf-api/src/main/java/com/xuejiai/aaf/module
mkdir -p order/controller order/service order/repository order/domain order/vo
```

包结构：

```text
aaf-api/src/main/java/com/xuejiai/aaf/module/order/
├── controller/      # REST API 控制器
├── service/         # 业务逻辑
├── repository/      # JPA Repository
├── domain/          # 实体 + 值对象
├── vo/              # DTO / VO
├── mapper/          # MapStruct 对象映射（按需）
├── enums/           # 枚举定义（按需）
└── event/           # 领域事件（按需）
```

> 简单 CRUD 模块只需 controller/service/repository/domain/vo 五个包，其余按需添加。

### 2. 创建实体类

```java
package com.xuejiai.aaf.module.order.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "t_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer status = 0;
}
```

### 3. 创建 Repository

```java
package com.xuejiai.aaf.module.order.repository;

import com.xuejiai.aaf.module.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

### 4. 创建 Service

```java
package com.xuejiai.aaf.module.order.service;

import com.xuejiai.aaf.module.order.domain.Order;
import com.xuejiai.aaf.module.order.repository.OrderRepository;
import com.xuejiai.aaf.module.order.vo.OrderCreateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order create(OrderCreateVO vo) {
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis());
        order.setUserId(vo.getUserId());
        order.setAmount(vo.getAmount());
        return orderRepository.save(order);
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
    }
}
```

### 5. 创建 Controller

```java
package com.xuejiai.aaf.module.order.controller;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.order.domain.Order;
import com.xuejiai.aaf.module.order.service.OrderService;
import com.xuejiai.aaf.module.order.vo.OrderCreateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<Order> create(@Valid @RequestBody OrderCreateVO vo) {
        return Result.success(orderService.create(vo));
    }

    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }
}
```

### 6. 创建数据库迁移脚本

在 `aaf-api/src/main/resources/db/migration/` 下创建 Flyway 脚本：

```sql
-- V{version}__create_order_table.sql
CREATE TABLE t_order (
    id          BIGSERIAL PRIMARY KEY,
    order_no    VARCHAR(32)    NOT NULL,
    user_id     BIGINT         NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    status      INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_no UNIQUE (order_no)
);
```

### 7. 构建和测试

```bash
# 构建
mvn clean install

# 启动（开发环境）
cd aaf-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 引入额外依赖

先在 `aaf-dependencies/pom.xml` 注册版本：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

再在 `aaf-api/pom.xml` 中引入（不写版本号）：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-sdk</artifactId>
</dependency>
```

## 检查清单

- [ ] 包名遵循 `com.xuejiai.aaf.module.{name}` 格式
- [ ] 实体类放在 `domain/` 包，使用 JPA 注解
- [ ] Repository 继承 `JpaRepository`
- [ ] Service 层不直接返回 Entity 给 Controller（转为 VO）
- [ ] 新依赖已在 `aaf-dependencies` 注册版本
- [ ] 数据库变更通过 Flyway 脚本管理
