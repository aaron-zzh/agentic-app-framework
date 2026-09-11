package com.xuejiai.aaf.module.channel.service;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.SystemAssistantTemplateIds;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.channel.repository.BotBindingRepository;
import com.xuejiai.aaf.module.channel.repository.ChannelPlatformRepository;

import lombok.RequiredArgsConstructor;

/** 解析官网匿名客服唯一启用渠道绑定。 */
@Service
@RequiredArgsConstructor
public class WebCustomerServiceBindingResolver {

    private static final ErrorCode CONFIGURATION_UNAVAILABLE =
            ErrorCode.of(3_000_100, 503, "匿名客服渠道配置不可用");

    private final ChannelPlatformRepository platformRepository;
    private final BotBindingRepository bindingRepository;

    public Binding resolve() {
        var platforms =
                OrgContext.runIgnoring(
                        () ->
                                platformRepository.findByTypeAndStatusAndDeletedFalse(
                                        ChannelTypeEnum.WEB, 0));
        if (platforms.size() != 1) {
            throw unavailable();
        }
        var platform = platforms.getFirst();
        if (!validId(platform.getId())
                || !validId(platform.getOrgId())
                || !validId(platform.getOwnerId())) {
            throw unavailable();
        }

        var bindings =
                OrgContext.runIgnoring(
                        () ->
                                bindingRepository.findByPlatformIdAndStatusAndDeletedFalse(
                                        platform.getId(), 0));
        if (bindings.size() != 1) {
            throw unavailable();
        }
        var binding = bindings.getFirst();
        if (!validId(binding.getId())
                || !platform.getOrgId().equals(binding.getOrgId())
                || !platform.getOwnerId().equals(binding.getOwnerId())
                || !SystemAssistantTemplateIds.CUSTOMER_SERVICE.equals(binding.getAssistantId())) {
            throw unavailable();
        }
        return new Binding(
                platform.getId(),
                binding.getId(),
                platform.getOrgId(),
                platform.getOwnerId(),
                binding.getAssistantId());
    }

    private static boolean validId(Long value) {
        return value != null && value > 0;
    }

    private static BusinessException unavailable() {
        return new BusinessException(CONFIGURATION_UNAVAILABLE);
    }

    /** 服务端可信的 WEB 客服执行绑定。 */
    public record Binding(
            Long platformId,
            Long bindingId,
            Long orgId,
            Long responsibleOwnerId,
            String assistantId) {}
}
