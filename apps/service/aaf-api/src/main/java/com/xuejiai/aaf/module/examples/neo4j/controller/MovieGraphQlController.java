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
 * <p>演示 Spring for GraphQL 与 Neo4j 的集成，与 REST 接口提供相同数据的不同访问方式。
 *
 * <p>GraphQL Playground 访问地址（dev 环境）：http://localhost:8080/graphiql
 *
 * <p>示例查询：
 *
 * <pre>{@code
 * # 查询所有电影
 * query {
 *   movies {
 *     title
 *     tagline
 *     released
 *     actors { name }
 *   }
 * }
 *
 * # 搜索电影
 * query {
 *   searchMovies(title: "Matrix") {
 *     title
 *     released
 *   }
 * }
 *
 * # 投票
 * mutation {
 *   vote(title: "The Matrix")
 * }
 * }</pre>
 */
@Controller
@RequiredArgsConstructor
public class MovieGraphQlController {

    private final MovieService movieService;

    @QueryMapping
    public List<Movie> movies() {
        return movieService.findAll();
    }

    @QueryMapping
    public List<Movie> searchMovies(@Argument String title) {
        return movieService.search(title);
    }

    @QueryMapping
    public Movie movie(@Argument String title) {
        return movieService.findByTitle(title);
    }

    @MutationMapping
    public boolean vote(@Argument String title) {
        movieService.vote(title);
        return true;
    }
}
