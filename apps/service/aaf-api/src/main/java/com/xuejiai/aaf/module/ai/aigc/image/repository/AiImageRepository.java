package com.xuejiai.aaf.module.ai.aigc.image.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.image.domain.AiImage;

/**
 * AI 图像生成记录仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AiImageRepository extends JpaRepository<AiImage, Long> {

    List<AiImage> findByStatusAndPlatform(String status, String platform);

    Optional<AiImage> findByTaskId(String taskId);

    List<AiImage> findByUserIdAndDeletedFalseOrderByCreateTimeDesc(Long userId);

    Page<AiImage> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
}
