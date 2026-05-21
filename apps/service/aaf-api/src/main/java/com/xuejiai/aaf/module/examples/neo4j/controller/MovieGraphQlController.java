package com.xuejiai.aaf.module.examples.neo4j.controller;

import com.xuejiai.aaf.module.examples.neo4j.domain.Movie;
import com.xuejiai.aaf.module.examples.neo4j.service.MovieService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * 电影 GraphQL API 示例。
 *
 * <p>演示 Spring for GraphQL 与 Neo4j 的集成，与 {@link MovieRestController} 提供相同数据的不同访问方式。
 *
 * <p>GraphQL Schema 定义见 {@code resources/graphql/movie.graphqls}。
 *
 * <p>GraphiQL 调试界面（dev 环境）：<a href="http://localhost:8080/graphiql">http://localhost:8080/graphiql</a>
 *
 * <h3>示例查询</h3>
 *
 * <pre>{@code
 * # 查询所有电影（含演员）
 * query {
 *   movies {
 *     title
 *     tagline
 *     released
 *     actors { name born }
 *   }
 * }
 *
 * # 按标题模糊搜索
 * query {
 *   searchMovies(title: "Matrix") {
 *     title
 *     released
 *   }
 * }
 *
 * # 查询单部电影详情
 * query {
 *   movie(title: "The Matrix") {
 *     title
 *     tagline
 *     actors { name }
 *   }
 * }
 *
 * # 投票（Mutation）
 * mutation {
 *   vote(title: "The Matrix")
 * }
 * }</pre>
 *
 * <h3>与 REST 的区别</h3>
 *
 * <ul>
 *   <li>GraphQL 客户端可按需选择返回字段，避免过度获取（over-fetching）
 *   <li>单次请求可获取嵌套关联数据（电影 + 演员）
 *   <li>所有操作通过同一端点 {@code /graphql} 访问
 * </ul>
 */
@Controller
@RequiredArgsConstructor
public class MovieGraphQlController {

    private final MovieService movieService;

    /**
     * 查询所有电影。
     *
     * <p>对应 Schema：{@code query { movies { ... } }}
     */
    @QueryMapping
    public List<Movie> movies() {
        return movieService.findAll();
    }

    /**
     * 按标题模糊搜索电影。
     *
     * <p>对应 Schema：{@code query { searchMovies(title: "Matrix") { ... } }}
     *
     * @param title 搜索关键词（Cypher CONTAINS 匹配）
     */
    @QueryMapping
    public List<Movie> searchMovies(@Argument String title) {
        return movieService.search(title);
    }

    /**
     * 按标题精确查询电影详情。
     *
     * <p>对应 Schema：{@code query { movie(title: "The Matrix") { ... } }}
     *
     * @param title 电影标题（精确匹配）
     */
    @QueryMapping
    public Movie movie(@Argument String title) {
        return movieService.findByTitle(title);
    }

    /**
     * 为电影投票（写操作）。
     *
     * <p>对应 Schema：{@code mutation { vote(title: "The Matrix") }}
     *
     * <p>演示 GraphQL Mutation 的用法，底层通过原生 Neo4j Driver 执行写入。
     *
     * @param title 电影标题
     * @return 始终返回 true（投票成功）
     */
    @MutationMapping
    public boolean vote(@Argument String title) {
        movieService.vote(title);
        return true;
    }
}
