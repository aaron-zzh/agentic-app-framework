package com.xuejiai.aaf.module.examples.neo4j.controller;

import com.xuejiai.aaf.module.examples.neo4j.domain.Movie;
import com.xuejiai.aaf.module.examples.neo4j.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电影 REST API 示例。
 *
 * <p>演示 Spring Data Neo4j 的 REST 接口用法，与 GraphQL 接口提供相同数据的不同访问方式。
 *
 * <p>接口列表：
 *
 * <ul>
 *   <li>GET /examples/neo4j/movies — 查询所有电影
 *   <li>GET /examples/neo4j/movies/search?q=Matrix — 按标题搜索
 *   <li>GET /examples/neo4j/movies/{title} — 查询电影详情
 *   <li>POST /examples/neo4j/movies/{title}/vote — 投票
 *   <li>GET /examples/neo4j/movies/graph — 图数据（D3.js 格式）
 * </ul>
 */
@Tag(name = "示例 - Neo4j 电影（REST）")
@RestController
@RequestMapping("/examples/neo4j/movies")
@RequiredArgsConstructor
public class MovieRestController {

    private final MovieService movieService;

    @Operation(summary = "查询所有电影")
    @GetMapping
    public List<Movie> findAll() {
        return movieService.findAll();
    }

    @Operation(summary = "按标题模糊搜索")
    @GetMapping("/search")
    public List<Movie> search(@RequestParam("q") String title) {
        return movieService.search(title.replace("*", ""));
    }

    @Operation(summary = "查询电影详情（含演员）")
    @GetMapping("/{title}")
    public Movie findByTitle(@PathVariable String title) {
        return movieService.findByTitle(title);
    }

    @Operation(summary = "投票")
    @PostMapping("/{title}/vote")
    public void vote(@PathVariable String title) {
        movieService.vote(title);
    }

    @Operation(summary = "图数据（D3.js 格式，用于可视化）")
    @GetMapping("/graph")
    public Map<String, List<Object>> graph() {
        return movieService.fetchGraph();
    }
}
