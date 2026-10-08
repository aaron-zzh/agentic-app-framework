package com.xuejiai.aaf.module.digitalhuman.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants;
import com.xuejiai.aaf.module.digitalhuman.repository.DigitalHumanVideoRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVideoVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数字人视频服务
 *
 * <p>负责查询和管理数字人生成的视频历史。 视频数据来源于AIGC项目的交付物（aigc_asset表）。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanVideoService {

    private final DigitalHumanService digitalHumanService;
    private final DigitalHumanVideoRepository videoRepository;

    /**
     * 获取数字人的视频历史列表
     *
     * <p>查询逻辑：
     *
     * <ol>
     *   <li>验证数字人存在且用户有访问权限
     *   <li>通过Repository查询关联的AIGC项目和视频资源
     *   <li>返回分页结果
     * </ol>
     */
    @Transactional(readOnly = true)
    public Page<DigitalHumanVideoVO> listVideos(
            Long digitalHumanId, String projectName, Pageable pageable) {
        // 验证数字人存在且有访问权限
        verifyDigitalHumanAccess(digitalHumanId);

        // 查询视频列表
        return videoRepository.findVideosByDigitalHumanId(digitalHumanId, projectName, pageable);
    }

    /** 获取单个视频详情 */
    @Transactional(readOnly = true)
    public DigitalHumanVideoVO getVideo(Long digitalHumanId, Long videoId) {
        verifyDigitalHumanAccess(digitalHumanId);

        // 查询视频详情
        DigitalHumanVideoVO video = videoRepository.findVideoById(digitalHumanId, videoId);

        if (video == null) {
            throw exception(ErrorCodeConstants.VIDEO_NOT_FOUND);
        }

        return video;
    }

    /**
     * 删除视频记录
     *
     * <p>仅删除关联记录，不删除实际视频文件。 实际上是软删除aigc_asset记录。
     */
    @Transactional
    public void deleteVideo(Long digitalHumanId, Long videoId) {
        verifyDigitalHumanAccess(digitalHumanId);

        // 验证视频存在且属于该数字人
        DigitalHumanVideoVO video = videoRepository.findVideoById(digitalHumanId, videoId);
        if (video == null) {
            throw exception(ErrorCodeConstants.VIDEO_NOT_FOUND);
        }

        // 软删除视频记录
        videoRepository.deleteVideo(videoId);

        log.info("Deleted video record: digitalHumanId={}, videoId={}", digitalHumanId, videoId);
    }

    /** 验证数字人存在且当前用户可读；授权由 CRUD 安全管线统一判定。 */
    private void verifyDigitalHumanAccess(Long digitalHumanId) {
        digitalHumanService.getById(digitalHumanId);
    }
}
