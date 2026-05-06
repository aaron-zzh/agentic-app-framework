---
level: Practice
layer: Product
purpose: 提供 AAF 领域建模的代码片段（实体、值对象、聚合根、Repository、领域事件、Service）
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 实体定义、充血模型、聚合根、值对象、Repository、领域事件、Service、对象映射
gains:
  - 能快速复用领域建模代码模式
---

# 领域建模代码片段

## 基础实体

```java
/**
 * 用户实体
 * @author AaronZZH
 * @since 1.0.0
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "sys_user")
@SQLDelete(sql = "UPDATE sys_user SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class User extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(length = 50, nullable = false)
    @EqualsAndHashCode.Include
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.INACTIVE;

    @Embedded
    private Address address;
}
```

## 充血模型

```java
// 在实体类中添加业务方法
public void activate() {
    if (this.status != UserStatus.INACTIVE) {
        throw new BusinessException("仅未激活用户可激活");
    }
    this.status = UserStatus.ACTIVE;
}

public void disable() {
    this.status = UserStatus.DISABLED;
}
```

## 聚合根

```java
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "wf_process")
@SQLDelete(sql = "UPDATE wf_process SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Process extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(length = 100, nullable = false)
    @EqualsAndHashCode.Include
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProcessStatus status = ProcessStatus.DRAFT;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "process_id")
    @Builder.Default
    private List<ProcessNode> nodes = new ArrayList<>();

    // 聚合根管理子实体的增删
    public void addNode(ProcessNode node) {
        this.nodes.add(node);
    }

    public void removeNode(Long nodeId) {
        this.nodes.removeIf(n -> n.getId().equals(nodeId));
    }

    public void publish() {
        if (this.nodes.isEmpty()) {
            throw new BusinessException("流程至少包含一个节点");
        }
        this.status = ProcessStatus.PUBLISHED;
    }
}
```

## 值对象

### @Embeddable（嵌入实体表）

```java
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @Column(length = 20)
    private String province;

    @Column(length = 20)
    private String city;

    @Column(length = 200)
    private String detail;
}
```

### record（用于 VO/DTO 场景）

```java
public record MoneyVO(BigDecimal amount, String currency) {
    public MoneyVO add(MoneyVO other) {
        if (!this.currency.equals(other.currency)) {
            throw new BusinessException("币种不一致");
        }
        return new MoneyVO(this.amount.add(other.amount), this.currency);
    }
}
```

## Repository

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // 方法名派生查询
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByStatus(UserStatus status);

    // 复杂查询用 @Query
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.username LIKE %:keyword%")
    Page<User> searchByStatusAndKeyword(@Param("status") UserStatus status,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);
}
```

## 领域事件

### 方式一：JPA @DomainEvents（推荐）

```java
// 聚合根内注册事件，JPA save 时自动发布
@Entity
public class User extends BaseEntity {

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    public void activate() {
        if (this.status != UserStatus.INACTIVE) {
            throw new BusinessException("仅未激活用户可激活");
        }
        this.status = UserStatus.ACTIVE;
        domainEvents.add(UserActivatedEvent.of(this));
    }

    @DomainEvents
    public Collection<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @AfterDomainEventPublication
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
```

### 方式二：Service 中手动发布

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.activate();
        userRepository.save(user);
        eventPublisher.publishEvent(UserActivatedEvent.of(user));
    }
}
```

### 事件定义

```java
public record UserActivatedEvent(Long userId, String username, LocalDateTime occurredTime) {
    public static UserActivatedEvent of(User user) {
        return new UserActivatedEvent(user.getId(), user.getUsername(), LocalDateTime.now());
    }
}
```

### 事件监听

```java
@Component
@Slf4j
public class UserEventListener {

    @EventListener
    public void onUserActivated(UserActivatedEvent event) {
        log.info("用户 [{}] 已激活", event.username());
    }

    // 异步监听（跨聚合最终一致性场景）
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWelcomeEmail(UserActivatedEvent event) {
        // 发送欢迎邮件等副作用操作
    }
}
```

## 对象映射（MapStruct）

```java
@Mapper(componentModel = "spring")
public interface UserConvert {
    UserVO toVO(User user);
    User toEntity(UserCreateDTO dto);
    void updateEntity(UserUpdateDTO dto, @MappingTarget User user);
}
```

## Service 层

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final UserConvert userConvert;

    @Transactional(readOnly = true)
    public UserVO getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return userConvert.toVO(user);
    }

    @Transactional
    public UserVO createUser(UserCreateDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        User user = userConvert.toEntity(dto);
        userRepository.save(user);
        return userConvert.toVO(user);
    }

    @Transactional
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.activate(); // 充血模型：业务规则在实体内
        userRepository.save(user); // JPA 脏检查会自动更新，显式 save 触发 @DomainEvents
    }
}
```

## 异常处理

```java
// 业务异常 - 直接使用 BusinessException
throw new BusinessException("用户名已存在");
throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");

// 参数异常 - 使用 Bean Validation
@NotBlank(message = "用户名不能为空")
private String username;

@Valid  // Controller 层自动校验
public Result<UserVO> createUser(@RequestBody @Valid UserCreateDTO dto) { ... }
```
