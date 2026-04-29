# AAF — Agentic App Framework

基于 Spring Boot 4 + Spring AI 2.0 的生产级多智能体应用开发框架，面向 Java/Spring 生态开发者。

## 技术栈

Java 25 · Spring Boot 4.0.6 · Spring AI 2.0-M4 · PostgreSQL/PgVector · Redis · Neo4j · WebFlux · GraphQL · MCP · Flyway

## 快速开始

```bash
./mvnw spring-boot:run
```

## 路线图

- **v0.1** — Agent 核心抽象 + Tool 系统 + Chat Agent 示例
- **v0.2** — 编排引擎 + RAG Pipeline + MCP 集成
- **v0.3** — 多 Agent 协作 + 事件总线
- **v0.4** — 可观测性 + 安全 + 多租户

## 文档

- [开发文档](docs/README.md)
- [路线图](docs/roadmap.md)
- [架构设计](docs/architecture.md)
- [AI 协作开发体系](docs/ai-development.md)

## 开发

本项目使用 Kiro 多智能体协作开发，详见 [AGENTS.md](AGENTS.md)。
