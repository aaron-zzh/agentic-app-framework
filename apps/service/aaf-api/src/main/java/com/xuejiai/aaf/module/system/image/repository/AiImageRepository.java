package com.xuejiai.aaf.module.system.image.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.image.domain.AiImage;

/** AI 图像生成记录仓储。 */
public interface AiImageRepository extends JpaRepository<AiImage, Long> {

    List<AiImage> findByStatusAndPlatform(String status, String platform);

    Optional<AiImage> findByTaskId(String taskId);

    List<AiImage> findByUserIdAndDeletedFalseOrderByCreateTimeDesc(Long userId);
}
