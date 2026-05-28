package com.xuejiai.aaf.module.examples.neo4j.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.examples.neo4j.domain.Movie;

/** 电影 Neo4j Repository。 */
public interface MovieRepository extends Neo4jRepository<Movie, String> {

    /** 按标题模糊搜索。 */
    @Query("MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m")
    List<Movie> findByTitleContaining(@Param("title") String title);

    /**
     * 查询图数据（用于 D3.js 可视化）。
     *
     * <p>返回格式：[{movie: "...", actors: ["...", "..."]}]
     */
    @Query(
            """
            MATCH (m:Movie)<-[:ACTED_IN]-(p:Person)
            WITH m, p ORDER BY m.title, p.name
            RETURN m.title AS movie, collect(p.name) AS actors
            """)
    List<Map<String, Object>> fetchGraph();
}
