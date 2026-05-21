# Neo4j 电影示例

演示 Spring Data Neo4j 与 Spring for GraphQL 的集成，同一份数据同时提供 **REST** 和 **GraphQL** 两种访问方式。

## 数据模型

```
(Person)-[:ACTED_IN]->(Movie)
```

- `Movie`：电影节点，属性：`title`（主键）、`tagline`、`released`
- `Person`：人物节点，属性：`name`（主键）、`born`
- `ACTED_IN`：关系，表示演员参演了某部电影

## 前置条件

1. 启动 Neo4j（默认 `bolt://localhost:7687`，账号 `neo4j/neo4j`）
2. 导入示例数据（Neo4j Browser 执行）：

```cypher
// 创建电影和演员
CREATE (matrix:Movie {title: 'The Matrix', tagline: 'Welcome to the Real World', released: 1999})
CREATE (keanu:Person {name: 'Keanu Reeves', born: 1964})
CREATE (laurence:Person {name: 'Laurence Fishburne', born: 1961})
CREATE (keanu)-[:ACTED_IN {role: 'Neo'}]->(matrix)
CREATE (laurence)-[:ACTED_IN {role: 'Morpheus'}]->(matrix)

CREATE (matrix2:Movie {title: 'The Matrix Reloaded', tagline: 'Free your mind', released: 2003})
CREATE (keanu)-[:ACTED_IN {role: 'Neo'}]->(matrix2)
```

## REST 接口

基础路径：`/examples/neo4j/movies`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/examples/neo4j/movies` | 查询所有电影 |
| GET | `/examples/neo4j/movies/search?q=Matrix` | 按标题模糊搜索 |
| GET | `/examples/neo4j/movies/{title}` | 查询电影详情（含演员） |
| POST | `/examples/neo4j/movies/{title}/vote` | 为电影投票 |
| GET | `/examples/neo4j/movies/graph` | 图数据（D3.js 格式） |

示例：

```bash
# 查询所有电影
curl http://localhost:8080/examples/neo4j/movies

# 搜索
curl "http://localhost:8080/examples/neo4j/movies/search?q=Matrix"

# 查详情
curl "http://localhost:8080/examples/neo4j/movies/The%20Matrix"

# 投票
curl -X POST "http://localhost:8080/examples/neo4j/movies/The%20Matrix/vote"
```

## GraphQL 接口

GraphiQL 调试界面（dev 环境）：http://localhost:8080/graphiql

### 查询示例

```graphql
# 查询所有电影（含演员）
query {
  movies {
    title
    tagline
    released
    actors {
      name
      born
    }
  }
}

# 按标题搜索
query {
  searchMovies(title: "Matrix") {
    title
    released
  }
}

# 查询单部电影详情
query {
  movie(title: "The Matrix") {
    title
    tagline
    released
    actors { name }
  }
}

# 投票
mutation {
  vote(title: "The Matrix")
}
```

## 技术要点

### REST vs GraphQL 对比

| 维度 | REST | GraphQL |
|------|------|---------|
| 接口数量 | 5 个端点 | 1 个端点（/graphql） |
| 字段控制 | 固定返回结构 | 客户端按需选择字段 |
| 关联数据 | 需要多次请求或 join | 单次查询获取嵌套数据 |
| 调试工具 | Swagger UI | GraphiQL |
| 适用场景 | 简单 CRUD、移动端 | 复杂关联查询、前端灵活性要求高 |

### Spring Data Neo4j 两种查询方式

**方式一：Repository 派生查询（简单场景）**

```java
// 自动生成 Cypher：MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m
List<Movie> findByTitleContaining(String title);
```

**方式二：原生 Driver（复杂聚合）**

```java
// 直接执行 Cypher，适合图遍历、聚合等复杂查询
try (var session = driver.session()) {
    session.executeRead(tx -> tx.run("MATCH (m:Movie)<-[:ACTED_IN]-(p:Person) ...").list());
}
```

### 关键配置

```yaml
# application-dev.yaml
spring:
  graphql:
    graphiql:
      enabled: true   # 开发环境启用 GraphiQL
  data:
    neo4j:
      schema-generate: create  # 自动创建 Neo4j 约束/索引
```
