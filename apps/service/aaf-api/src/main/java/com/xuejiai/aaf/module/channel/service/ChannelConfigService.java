package com.xuejiai.aaf.module.channel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.channel.domain.ChannelConfig;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.repository.ChannelConfigRepository;
import com.xuejiai.aaf.module.channel.repository.ChannelMessageRepository;
import com.xuejiai.aaf.module.channel.vo.ChannelConfigSaveDTO;
import com.xuejiai.aaf.module.channel.vo.ChannelConfigVO;
import com.xuejiai.aaf.module.channel.vo.ChannelStatsVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道配置管理服务。
 *
 * <p>各渠道 AppID/Secret/Token 配置 CRUD + 状态监控 + 连通性测试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigRepository configRepository;
    private final ChannelMessageRepository messageRepository;
    private final ChannelMessageRouter router;

    // ==================== CRUD ====================

    @Transactional
    public ChannelConfigVO create(ChannelConfigSaveDTO dto) {
        var config = new ChannelConfig();
        config.setChannelType(dto.channelType());
        applyEditableFields(config, dto);
        return ChannelConfigVO.from(configRepository.save(config));
    }

    @Transactional
    public ChannelConfigVO update(Long id, ChannelConfigSaveDTO dto) {
        var config =
                configRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "渠道配置不存在"));
        applyEditableFields(config, dto);
        return ChannelConfigVO.from(configRepository.save(config));
    }

    /** 写入可编辑字段；密钥类字段留空表示保持原值（出参已脱敏，客户端无法回填原值）。 */
    private void applyEditableFields(ChannelConfig config, ChannelConfigSaveDTO dto) {
        config.setName(dto.name());
        config.setAppId(dto.appId());
        config.setExtConfig(dto.extConfig());
        if (dto.status() != null) {
            config.setStatus(dto.status());
        }
        if (isPresent(dto.appSecret())) {
            config.setAppSecret(dto.appSecret());
        }
        if (isPresent(dto.token())) {
            config.setToken(dto.token());
        }
        if (isPresent(dto.encodingAesKey())) {
            config.setEncodingAesKey(dto.encodingAesKey());
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional
    public void delete(Long id) {
        configRepository.deleteById(id);
    }

    public ChannelConfigVO getById(Long id) {
        return ChannelConfigVO.from(
                configRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "渠道配置不存在")));
    }

    public List<ChannelConfigVO> listEnabled() {
        return configRepository.findByStatusAndDeletedFalse(0).stream()
                .map(ChannelConfigVO::from)
                .toList();
    }

    // ==================== 状态监控 ====================

    /** 获取渠道状态概览（连接状态 + 消息量统计 + 错误率）。 */
    public List<ChannelStatsVO> getChannelStats() {
        var configs = configRepository.findByStatusAndDeletedFalse(0);
        return configs.stream()
                .map(
                        config -> {
                            var channelType = config.getChannelType();
                            var stats = messageRepository.countByChannelType(channelType);
                            var errorCount =
                                    messageRepository.countErrorsByChannelType(channelType);
                            var totalCount = stats;
                            var errorRate = totalCount > 0 ? (double) errorCount / totalCount : 0.0;

                            // 检查适配器是否可用
                            boolean available;
                            try {
                                var type = ChannelTypeEnum.valueOf(channelType.toUpperCase());
                                var adapter = router.getAdapter(type);
                                available = adapter.isAvailable();
                            } catch (Exception e) {
                                available = false;
                            }

                            return new ChannelStatsVO(
                                    config.getId(),
                                    config.getName(),
                                    channelType,
                                    available,
                                    totalCount,
                                    errorCount,
                                    errorRate);
                        })
                .toList();
    }

    // ==================== 连通性测试 ====================

    /**
     * 发送测试消息验证渠道连通性。
     *
     * @param channelType 渠道类型
     * @return 测试结果描述
     */
    public String testConnection(String channelType) {
        try {
            var type = ChannelTypeEnum.valueOf(channelType.toUpperCase());
            var adapter = router.getAdapter(type);
            if (!adapter.isAvailable()) {
                return "渠道不可用：适配器未激活";
            }
            // 发送测试消息
            var testMsg = UnifiedMessage.outboundText(type, "test_user", "AAF 渠道连通性测试");
            adapter.reply(testMsg);
            return "连通性测试成功";
        } catch (IllegalArgumentException e) {
            return "未注册的渠道类型: " + channelType;
        } catch (Exception e) {
            return "连通性测试失败: " + e.getMessage();
        }
    }

    // ==================== 消息统计 ====================

    /** 按渠道/时间/类型统计消息量。 */
    public Map<String, Object> getMessageStats(
            String channelType, LocalDateTime startTime, LocalDateTime endTime) {
        var total =
                messageRepository.countByChannelTypeAndTimeBetween(channelType, startTime, endTime);
        var inbound =
                messageRepository.countByChannelTypeAndDirectionAndTimeBetween(
                        channelType, "inbound", startTime, endTime);
        var outbound =
                messageRepository.countByChannelTypeAndDirectionAndTimeBetween(
                        channelType, "outbound", startTime, endTime);
        return Map.of(
                "channelType", channelType,
                "total", total,
                "inbound", inbound,
                "outbound", outbound,
                "startTime", startTime.toString(),
                "endTime", endTime.toString());
    }
}
