# 已知问题

## @nx/maven 插件 createDependencies 报错（#34254）

- **状态**：Open，priority: medium，已分配给 @lourw
- **Issue**：https://github.com/nrwl/nx/issues/34254
- **影响版本**：Nx 22.x（`@nx/maven` 标记为 Experimental）
- **表现**：
  - `@nx/maven` 的 Maven Analyzer 能成功分析项目并生成 `nx-maven-projects.json`
  - 但在 `createDependencies` 阶段报错：`Source file "apps\service\pom.xml" does not exist in the workspace`
  - 根本原因是插件不支持子目录下的 Maven 项目，要求 `pom.xml` 必须在工作区根目录
- **临时方案**：
  - 从 `nx.json` 的 `plugins` 中移除 `@nx/maven`
  - 在 `apps/service/project.json` 中手动配置 targets（build/serve/test/clean），使用 `nx:run-commands` 调用 `mvnw.cmd`
- **恢复条件**：等 #34254 修复后，重新启用 `@nx/maven` 插件，删除手动 `project.json` 配置
