# 贡献指南

感谢你对 Agentic App Framework (AAF) 的关注！本文档将帮助你了解如何为项目做贡献。

## 贡献方式

### 1. 报告问题

发现 Bug 或有功能建议？请通过 GitHub Issues 提交。

**提交 Issue 前请确认**：

- [ ] 搜索已有 Issue，避免重复
- [ ] 使用清晰的标题描述问题
- [ ] 提供详细的复现步骤
- [ ] 附上相关日志和截图

**Issue 模板**：

```markdown
### 问题描述
简要描述遇到的问题

### 复现步骤
1. 执行 xxx
2. 访问 xxx
3. 看到错误 xxx

### 期望行为
应该发生什么

### 实际行为
实际发生了什么

### 环境信息
- AAF 版本：
- JDK 版本：
- 操作系统：
- 数据库版本：

### 相关日志
```log
粘贴相关日志
```

```

### 2. 提交代码

欢迎提交 Pull Request！

**提交前请确认**：
- [ ] 代码符合项目规范
- [ ] 通过所有测试
- [ ] 添加了必要的测试用例
- [ ] 更新了相关文档

**PR 流程**：

1. Fork 项目到你的账号
2. 从最新 main 创建分支：`git checkout -b feature/AAF-123-xxx`
3. 提交代码：`git commit -m "feat: 添加 xxx 功能"`
4. 推送分支：`git push origin feature/AAF-123-xxx`
5. 创建 Pull Request，至少 1 人审查通过后合并

详见 [协作流程](../reference/dev/git/collaboration-guide.md)。

### 3. 完善文档

文档和代码同样重要！

**文档贡献包括**：
- 修正文档错误
- 补充使用示例
- 翻译文档
- 添加最佳实践

### 4. 分享经验

- 撰写使用教程
- 分享实践案例
- 回答社区问题
- 推广项目

## 开发环境搭建

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.3+（可选）
- Git

### 克隆项目

```bash
git clone https://github.com/xuejiai/agentic-app-framework.git
cd agentic-app-framework
```

### 构建项目

```bash
# 安装依赖
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests
```

### 配置数据库

1. 创建数据库：

```sql
CREATE DATABASE aaf_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

1. 修改配置文件 `aaf-server/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aaf_db
    username: root
    password: your_password
```

### 启动项目

```bash
cd aaf-server
mvn spring-boot:run
```

或在 IDE 中直接运行 `AafApplication.java`。

## 开发规范

### 代码规范

请严格遵循 [开发规范](../reference/dev/development-standard.md)：

- 模块命名：小写 + 连字符
- 包命名：`com.xuejiai.aaf.{module}.{feature}`
- 类命名：驼峰命名
- 方法命名：驼峰命名，动词开头

### 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**：

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(agent): 添加智能体协作功能` |
| `fix` | 修复 Bug | `fix(workflow): 修复流程执行异常` |
| `docs` | 文档更新 | `docs: 更新安装指南` |
| `style` | 代码格式 | `style: 格式化代码` |
| `refactor` | 重构 | `refactor(memory): 重构记忆系统` |
| `test` | 测试 | `test: 添加单元测试` |
| `chore` | 构建/工具 | `chore: 升级依赖版本` |
| `perf` | 性能优化 | `perf: 优化查询性能` |

**示例**：

```
feat(agent): 添加多智能体协作功能

- 实现智能体注册机制
- 添加智能体通信协议
- 完善智能体生命周期管理

Closes #123
```

### 分支规范

采用 GitHub Flow，详见 [分支管理](../reference/dev/git/branch-manage-standard.md)。

| 分支类型 | 命名规则 | 说明 |
|---------|---------|------|
| 主分支 | `main` | 始终可部署 |
| 功能分支 | `feature/<task-id>-xxx` | 新功能开发 |
| 修复分支 | `fix/<task-id>-xxx` | Bug 修复 |
| 热修复分支 | `hotfix/<task-id>-xxx` | 紧急修复 |

### 测试规范

**单元测试**：

```java
@SpringBootTest
public class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    public void testCreateUser() {
        // given
        UserDTO dto = new UserDTO();
        dto.setUsername("test");
        
        // when
        User user = userService.create(dto);
        
        // then
        assertNotNull(user.getId());
        assertEquals("test", user.getUsername());
    }
}
```

**测试覆盖率**：

- 核心业务逻辑：≥ 80%
- 工具类：≥ 90%
- 控制器：≥ 60%

## 代码审查

### 审查清单

**功能性**：

- [ ] 功能实现正确
- [ ] 边界条件处理
- [ ] 异常处理完善

**代码质量**：

- [ ] 代码可读性好
- [ ] 命名清晰准确
- [ ] 注释充分合理
- [ ] 无重复代码

**性能**：

- [ ] 无明显性能问题
- [ ] 数据库查询优化
- [ ] 合理使用缓存

**安全**：

- [ ] 输入验证
- [ ] 权限检查
- [ ] 敏感信息保护

**测试**：

- [ ] 单元测试完善
- [ ] 测试用例充分
- [ ] 测试通过

## 发布流程

### 版本号规范

使用 [语义化版本](https://semver.org/lang/zh-CN/)：`主版本号.次版本号.修订号`

- **主版本号**：不兼容的 API 修改
- **次版本号**：向下兼容的功能性新增
- **修订号**：向下兼容的问题修正

### 发布步骤

1. 更新版本号：`<version>1.0.0</version>`

2. 更新 CHANGELOG.md

3. 提交并打标签：

```bash
git commit -m "chore: 发布 v1.0.0"
git tag v1.0.0
git push origin main --tags
```

## 社区规范

### 行为准则

- 尊重他人，友善交流
- 建设性反馈，避免人身攻击
- 保护隐私，不泄露敏感信息
- 遵守开源协议

### 沟通渠道

- **GitHub Issues**：Bug 报告、功能建议
- **GitHub Discussions**：技术讨论、问题求助
- **微信群**：日常交流（见 README）

## 常见问题

### Q: 如何选择合适的 Issue？

A: 查找标签为 `good first issue` 或 `help wanted` 的 Issue，这些适合新手贡献。

### Q: PR 多久会被审查？

A: 通常 3-5 个工作日内会有反馈，复杂的 PR 可能需要更长时间。

### Q: 代码被拒绝了怎么办？

A: 不要气馁！根据反馈修改代码，或在评论中讨论。

### Q: 可以同时提交多个 PR 吗？

A: 可以，但建议每个 PR 只解决一个问题，便于审查。

### Q: 如何成为核心贡献者？

A: 持续贡献高质量代码、积极参与社区讨论、帮助他人解决问题。

## 致谢

感谢所有为 AAF 做出贡献的开发者！

你的每一次贡献，无论大小，都让 AAF 变得更好。

---

## 参考资源

- [GitHub 贡献指南](https://docs.github.com/cn/get-started/quickstart/contributing-to-projects)
- [开源贡献最佳实践](https://opensource.guide/how-to-contribute/)
- [Conventional Commits](https://www.conventionalcommits.org/)
