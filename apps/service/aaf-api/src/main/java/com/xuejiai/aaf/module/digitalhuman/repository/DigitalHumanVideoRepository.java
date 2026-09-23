package com.xuejiai.aaf.module.digitalhuman.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVideoVO;

import lombok.RequiredArgsConstructor;

/**
 * 数字人视频查询Repository
 *
 * <p>使用原生SQL查询AIGC项目和媒体数据，
 * 避免复杂的JPA关联查询。
 *
 * @author Kiro
 */
@Repository
@RequiredArgsConstructor
public class DigitalHumanVideoRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 查询数字人的视频历史列表
     *
     * <p>查询逻辑：
     * 1. 从aigc_project_slot表中找到包含digitalHumanId的项目
     * 2. 关联aigc_project表获取项目信息
     * 3. 关联aigc_asset表获取视频资源
     * 4. 按创建时间倒序分页返回
     */
    public Page<DigitalHumanVideoVO> findVideosByDigitalHumanId(
            Long digitalHumanId,
            String projectName,
            Pageable pageable) {

        // 构建查询条件
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("digitalHumanId", digitalHumanId.toString())
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        // 构建WHERE子句
        String projectNameCondition = "";
        if (projectName != null && !projectName.isBlank()) {
            projectNameCondition = "AND p.name LIKE :projectName";
            params.addValue("projectName", "%" + projectName + "%");
        }

        // 查询SQL
        String sql = """
            SELECT
                a.id,
                :digitalHumanId::bigint as digital_human_id,
                dh.name as digital_human_name,
                p.id as project_id,
                p.name as project_name,
                COALESCE(a.name, p.name || ' - 视频') as title,
                a.url as video_url,
                a.thumbnail_url,
                COALESCE((a.metadata->>'duration')::integer, 0) as duration,
                COALESCE(a.metadata->>'aspectRatio', '16:9') as aspect_ratio,
                COALESCE(a.metadata->>'provider', 'unknown') as provider,
                COALESCE((a.metadata->>'sceneCount')::integer, 1) as scene_count,
                COALESCE((a.metadata->>'creditsConsumed')::integer, 0) as credits_consumed,
                CASE
                    WHEN a.url IS NOT NULL THEN 'completed'
                    WHEN p.status = 'failed' THEN 'failed'
                    ELSE 'pending'
                END as status,
                a.created_at,
                a.updated_at as completed_at
            FROM aigc_project p
            JOIN aigc_project_slot s ON p.id = s.project_id
            JOIN digital_human dh ON dh.id = :digitalHumanId
            LEFT JOIN aigc_asset a ON p.id = a.project_id
                AND a.asset_type = 'VIDEO'
                AND a.deleted = false
            WHERE p.blueprint_code IN ('digital-human-marketing-video', 'digital-human-asset-generation')
                AND (
                    s.input_data->>'digitalHumanId' = :digitalHumanId
                    OR s.metadata->>'digitalHumanId' = :digitalHumanId
                )
                AND p.deleted = false
                AND s.deleted = false
                %s
            ORDER BY a.created_at DESC NULLS LAST, p.created_at DESC
            LIMIT :limit OFFSET :offset
            """.formatted(projectNameCondition);

        // 执行查询
        List<DigitalHumanVideoVO> videos = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            DigitalHumanVideoVO vo = new DigitalHumanVideoVO();
            vo.setId(rs.getLong("id"));
            vo.setDigitalHumanId(rs.getLong("digital_human_id"));
            vo.setDigitalHumanName(rs.getString("digital_human_name"));
            vo.setProjectId(rs.getLong("project_id"));
            vo.setProjectName(rs.getString("project_name"));
            vo.setTitle(rs.getString("title"));
            vo.setVideoUrl(rs.getString("video_url"));
            vo.setThumbnailUrl(rs.getString("thumbnail_url"));
            vo.setDuration(rs.getInt("duration"));
            vo.setAspectRatio(rs.getString("aspect_ratio"));
            vo.setProvider(rs.getString("provider"));
            vo.setSceneCount(rs.getInt("scene_count"));
            vo.setCreditsConsumed(rs.getInt("credits_consumed"));
            vo.setStatus(rs.getString("status"));
            vo.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

            var completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                vo.setCompletedAt(completedAt.toLocalDateTime());
            }

            return vo;
        });

        // 查询总数
        String countSql = """
            SELECT COUNT(DISTINCT p.id)
            FROM aigc_project p
            JOIN aigc_project_slot s ON p.id = s.project_id
            WHERE p.blueprint_code IN ('digital-human-marketing-video', 'digital-human-asset-generation')
                AND (
                    s.input_data->>'digitalHumanId' = :digitalHumanId
                    OR s.metadata->>'digitalHumanId' = :digitalHumanId
                )
                AND p.deleted = false
                AND s.deleted = false
                %s
            """.formatted(projectNameCondition);

        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);

        return new PageImpl<>(videos, pageable, total != null ? total : 0);
    }

    /**
     * 根据ID查询单个视频详情
     */
    public DigitalHumanVideoVO findVideoById(Long digitalHumanId, Long videoId) {
        String sql = """
            SELECT
                a.id,
                :digitalHumanId::bigint as digital_human_id,
                dh.name as digital_human_name,
                p.id as project_id,
                p.name as project_name,
                COALESCE(a.name, p.name || ' - 视频') as title,
                a.url as video_url,
                a.thumbnail_url,
                COALESCE((a.metadata->>'duration')::integer, 0) as duration,
                COALESCE(a.metadata->>'aspectRatio', '16:9') as aspect_ratio,
                COALESCE(a.metadata->>'provider', 'unknown') as provider,
                COALESCE((a.metadata->>'sceneCount')::integer, 1) as scene_count,
                COALESCE((a.metadata->>'creditsConsumed')::integer, 0) as credits_consumed,
                CASE
                    WHEN a.url IS NOT NULL THEN 'completed'
                    WHEN p.status = 'failed' THEN 'failed'
                    ELSE 'pending'
                END as status,
                a.created_at,
                a.updated_at as completed_at
            FROM aigc_asset a
            JOIN aigc_project p ON a.project_id = p.id
            JOIN aigc_project_slot s ON p.id = s.project_id
            JOIN digital_human dh ON dh.id = :digitalHumanId
            WHERE a.id = :videoId
                AND a.asset_type = 'VIDEO'
                AND a.deleted = false
                AND (
                    s.input_data->>'digitalHumanId' = :digitalHumanId
                    OR s.metadata->>'digitalHumanId' = :digitalHumanId
                )
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("digitalHumanId", digitalHumanId.toString())
                .addValue("videoId", videoId);

        List<DigitalHumanVideoVO> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            DigitalHumanVideoVO vo = new DigitalHumanVideoVO();
            vo.setId(rs.getLong("id"));
            vo.setDigitalHumanId(rs.getLong("digital_human_id"));
            vo.setDigitalHumanName(rs.getString("digital_human_name"));
            vo.setProjectId(rs.getLong("project_id"));
            vo.setProjectName(rs.getString("project_name"));
            vo.setTitle(rs.getString("title"));
            vo.setVideoUrl(rs.getString("video_url"));
            vo.setThumbnailUrl(rs.getString("thumbnail_url"));
            vo.setDuration(rs.getInt("duration"));
            vo.setAspectRatio(rs.getString("aspect_ratio"));
            vo.setProvider(rs.getString("provider"));
            vo.setSceneCount(rs.getInt("scene_count"));
            vo.setCreditsConsumed(rs.getInt("credits_consumed"));
            vo.setStatus(rs.getString("status"));
            vo.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

            var completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                vo.setCompletedAt(completedAt.toLocalDateTime());
            }

            return vo;
        });

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 软删除视频记录（设置deleted=true）
     */
    public void deleteVideo(Long videoId) {
        String sql = "UPDATE aigc_asset SET deleted = true, updated_at = CURRENT_TIMESTAMP WHERE id = :videoId";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("videoId", videoId);

        jdbcTemplate.update(sql, params);
    }
}
