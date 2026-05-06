---
level: Practice
layer: Product
purpose: 提供 AAF 项目常用编码模式的代码片段
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 注解使用、日志、异常处理、输入验证
gains:
  - 能快速复用常见编码模式
---

# 编码代码片段

## 注解使用

```java
// 控制器
@RestController
@RequestMapping("/api/users")
public class UserController { }

// 服务
@Service
public class UserServiceImpl implements UserService { }

// Mapper
@Mapper
public interface UserMapper extends BaseMapper<User> { }
```

## 日志使用

```java
@Slf4j
public class UserService {
    
    public User create(UserDTO dto) {
        log.info("创建用户: {}", dto.getUsername());
        
        try {
            User user = userMapper.insert(dto);
            log.info("用户创建成功: id={}", user.getId());
            return user;
        } catch (Exception e) {
            log.error("用户创建失败: {}", dto.getUsername(), e);
            throw new BusinessException("用户创建失败");
        }
    }
}
```

## 异常定义

```java
// 业务异常
public class BusinessException extends RuntimeException { }

// 系统异常
public class SystemException extends RuntimeException { }

// 参数异常
public class ParamException extends RuntimeException { }
```

## 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }
}
```

## 输入验证

```java
@PostMapping("/users")
public Result create(@Valid @RequestBody UserDTO dto) {
    // @Valid 自动验证
}
```
