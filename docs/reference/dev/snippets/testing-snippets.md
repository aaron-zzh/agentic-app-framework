---
level: Practice
layer: Product
purpose: 提供 AAF 项目单元测试和验收测试的代码片段
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 单元测试、验收测试
gains:
  - 能快速编写标准测试代码
---

# 测试代码片段

## 单元测试

```java
@SpringBootTest
public class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    public void testCreateUser() {
        // given
        UserDTO userDTO = new UserDTO();
        
        // when
        User user = userService.create(userDTO);
        
        // then
        assertNotNull(user.getId());
    }
}
```

## 验收测试（JUnit 5 + Gherkin DisplayName）

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("AC-1: 管理员创建用户 → 用户创建成功")
    void admin_creates_user_successfully() throws Exception {
        // Given 管理员已登录
        // When 创建用户名为 "test" 的用户
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "test"}
                    """))
                // Then 用户创建成功
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("test"));
    }
}
```
