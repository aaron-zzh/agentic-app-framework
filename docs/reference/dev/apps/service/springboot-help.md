---
level: Practice
layer: Model
purpose: Spring Boot 项目快速入门参考
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.6/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.6/maven-plugin/build-image.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.0.6/reference/packaging/native-image/introducing-graalvm-native-images.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.6/reference/using/devtools.html)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.0.6/specification/configuration-metadata/annotation-processor.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.6/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.6/reference/web/servlet.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/4.0.6/reference/web/reactive.html)
* [Reactive HTTP Client](https://docs.spring.io/spring-boot/4.0.6/reference/io/rest-client.html#io.rest-client.webclient)
* [Spring for GraphQL](https://docs.spring.io/spring-boot/4.0.6/reference/web/spring-graphql.html)
* [SpringDoc OpenAPI](https://springdoc.org/)
* [Spring Data R2DBC](https://docs.spring.io/spring-boot/4.0.6/reference/data/sql.html#data.sql.r2dbc)
* [Spring Security](https://docs.spring.io/spring-boot/4.0.6/reference/web/spring-security.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.6/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.6/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Spring Data Neo4j](https://docs.spring.io/spring-boot/4.0.6/reference/data/nosql.html#data.nosql.neo4j)
* [Spring Data Redis (Access+Driver)](https://docs.spring.io/spring-boot/4.0.6/reference/data/nosql.html#data.nosql.redis)
* [WebSocket](https://docs.spring.io/spring-boot/4.0.6/reference/messaging/websockets.html)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.6/reference/io/email.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.6/reference/actuator/index.html)
* [Prometheus](https://docs.spring.io/spring-boot/4.0.6/reference/actuator/metrics.html#actuator.metrics.export.prometheus)
* [Model Context Protocol Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
* [OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
* [Model Context Protocol Client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
* [PGvector Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
* [Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
* [Markdown Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_markdown)
* [JSoup Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html)
* [Milvus Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/milvus.html)
* [JDBC Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [Tika Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_tika_docx_pptx_html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)
* [Building a GraphQL service](https://spring.io/guides/gs/graphql-server/)
* [SpringDoc OpenAPI](https://github.com/springdoc/springdoc-openapi-demos/)
* [Accessing data with R2DBC](https://spring.io/guides/gs/accessing-data-r2dbc/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing Data with Neo4j](https://spring.io/guides/gs/accessing-data-neo4j/)
* [Messaging with Redis](https://spring.io/guides/gs/messaging-redis/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.0.6/how-to/aot.html)
* [R2DBC Homepage](https://r2dbc.io)

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
It is also possible to run your tests in a native image.

### Lightweight Container with Cloud Native Buildpacks
If you're already familiar with Spring Boot container images support, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following goal:

```
$ ./mvnw spring-boot:build-image -Pnative
```

Then, you can run the app like any other container:

```
$ docker run --rm -p 8080:8080 agentic-app-framework:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools
Use this option if you want to explore more options such as running your tests in a native image.
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To create the executable, run the following goal:

```
$ ./mvnw native:compile -Pnative
```

Then, you can run the app as follows:
```
$ target/agentic-app-framework
```

You can also run your existing tests suite in a native image.
This is an efficient way to validate the compatibility of your application.

To run your existing tests in a native image, run the following goal:

```
$ ./mvnw test -PnativeTest
```


### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

