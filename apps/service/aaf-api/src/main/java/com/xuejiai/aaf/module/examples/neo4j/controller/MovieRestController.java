package com.xuejiai.aaf.module.examples.neo4j.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.module.examples.neo4j.domain.Movie;
import com.xuejiai.aaf.module.examples.neo4j.service.MovieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 电影 REST API 示例。
 *
 * <p>演示 Spring Data Neo4j 的 REST 接口用法，与 {@link MovieGraphQlController} 提供相同数据的不同访问方式。
 *
 * <p>数据模型：{@code (Person)-[:ACTED_IN]->(Movie)}
 *
 * <p>前置条件：Neo4j 已启动并导入示例数据（见 README.md）。
 */
@Tag(
        name = "示例 - Neo4j 电影（REST）",
        description = "演示 Spring Data Neo4j + REST 接口，与 GraphQL 接口提供相同数据")
@RestController
@RequestMapping("/examples/neo4j/movies")
@RequiredArgsConstructor
public class MovieRestController {

    private final MovieService movieService;

    @Operation(summary = "查询所有电影", description = "返回 Neo4j 中所有 Movie 节点，含关联演员（通过 ACTED_IN 关系加载）")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(schema = @Schema(implementation = Movie.class)))
    @GetMapping
    public List<Movie> findAll() {
        return movieService.findAll();
    }

    @Operation(summary = "按标题模糊搜索", description = "使用 Cypher CONTAINS 进行模糊匹配，前后的 * 通配符会被自动去除")
    @ApiResponse(responseCode = "200", description = "匹配的电影列表，无结果时返回空数组")
    @GetMapping("/search")
    public List<Movie> search(
            @Parameter(description = "搜索关键词，支持 * 通配符（自动去除）", example = "Matrix", required = true)
                    @RequestParam("q")
                    String title) {
        return movieService.search(title.replace("*", ""));
    }

    @Operation(summary = "查询电影详情", description = "按标题精确查询，返回电影信息及通过 ACTED_IN 关系关联的演员列表")
    @ApiResponse(
            responseCode = "200",
            description = "电影详情",
            content = @Content(schema = @Schema(implementation = Movie.class)))
    @ApiResponse(responseCode = "200", description = "电影不存在时返回 null")
    @GetMapping("/{title}")
    public Movie findByTitle(
            @Parameter(description = "电影标题（精确匹配，区分大小写）", example = "The Matrix", required = true)
                    @PathVariable
                    String title) {
        return movieService.findByTitle(title);
    }

    @Operation(summary = "为电影投票", description = "将指定电影的 votes 属性 +1，演示原生 Driver 写操作")
    @ApiResponse(responseCode = "200", description = "投票成功")
    @PostMapping("/{title}/vote")
    public void vote(
            @Parameter(description = "电影标题", example = "The Matrix", required = true) @PathVariable
                    String title) {
        movieService.vote(title);
    }

    @Operation(
            summary = "获取图数据（D3.js 格式）",
            description =
                    """
                    返回适合 D3.js force-directed graph 渲染的节点和边数据。
                    格式：{"nodes": [{"label": "movie|actor", "title": "..."}], "links": [{"source": 0, "target": 1}]}
                    演示原生 Neo4j Driver 的复杂聚合查询。
                    """)
    @ApiResponse(responseCode = "200", description = "图数据，nodes 为节点列表，links 为边列表")
    @GetMapping("/graph")
    public Map<String, List<Object>> graph() {
        return movieService.fetchGraph();
    }
}
