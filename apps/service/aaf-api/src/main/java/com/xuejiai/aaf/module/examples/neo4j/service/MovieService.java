package com.xuejiai.aaf.module.examples.neo4j.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.examples.neo4j.domain.Movie;
import com.xuejiai.aaf.module.examples.neo4j.repository.MovieRepository;

import lombok.RequiredArgsConstructor;

/** 电影服务，演示 Spring Data Neo4j + 原生 Driver 两种查询方式。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;
    private final Driver driver; // 原生 Driver，用于复杂 Cypher

    /** 查询所有电影（Spring Data Neo4j）。 */
    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    /** 按标题模糊搜索（Spring Data Neo4j @Query）。 */
    public List<Movie> search(String title) {
        return movieRepository.findByTitleContaining(title);
    }

    /** 按标题查详情（原生 Driver + Neo4jClient 风格）。 */
    public Movie findByTitle(String title) {
        return movieRepository.findById(title).orElse(null);
    }

    /**
     * 查询图数据，用于前端 D3.js 可视化。
     *
     * <p>演示原生 Driver 的使用方式（适合复杂聚合查询）。
     *
     * @return {nodes: [...], links: [...]}
     */
    public Map<String, List<Object>> fetchGraph() {
        var nodes = new ArrayList<>();
        var links = new ArrayList<>();

        try (var session = driver.session()) {
            var records =
                    session.executeRead(
                            tx ->
                                    tx.run(
                                                    """
                    MATCH (m:Movie)<-[:ACTED_IN]-(p:Person)
                    WITH m, p ORDER BY m.title, p.name
                    RETURN m.title AS movie, collect(p.name) AS actors
                    """)
                                            .list());

            records.forEach(
                    record -> {
                        var movie =
                                Map.of("label", "movie", "title", record.get("movie").asString());
                        var targetIndex = nodes.size();
                        nodes.add(movie);

                        record.get("actors")
                                .asList(Value::asString)
                                .forEach(
                                        name -> {
                                            var actor = Map.of("label", "actor", "title", name);
                                            int sourceIndex;
                                            if (nodes.contains(actor)) {
                                                sourceIndex = nodes.indexOf(actor);
                                            } else {
                                                nodes.add(actor);
                                                sourceIndex = nodes.size() - 1;
                                            }
                                            links.add(
                                                    Map.of(
                                                            "source",
                                                            sourceIndex,
                                                            "target",
                                                            targetIndex));
                                        });
                    });
        }
        return Map.of("nodes", nodes, "links", links);
    }

    /** 投票（写操作，演示原生 Driver 写入）。 */
    @Transactional
    public void vote(String title) {
        try (var session = driver.session()) {
            session.executeWrite(
                    tx ->
                            tx.run(
                                            """
                    MATCH (m:Movie {title: $title})
                    SET m.votes = coalesce(m.votes, 0) + 1
                    """,
                                            Map.of("title", title))
                                    .consume());
        }
    }
}
