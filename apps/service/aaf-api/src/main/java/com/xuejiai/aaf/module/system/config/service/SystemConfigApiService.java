package com.xuejiai.aaf.module.system.config.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.system.config.domain.SystemConfig;
import com.xuejiai.aaf.framework.system.config.repository.SystemConfigRepository;
import com.xuejiai.aaf.module.system.config.vo.SystemConfigCreateDTO;
import com.xuejiai.aaf.module.system.config.vo.SystemConfigVO;

import lombok.RequiredArgsConstructor;

/**
 * 系统配置委托层——VO 相关操作（创建、删除、响应转换）。 基础读写能力委托给 {@link
 * com.xuejiai.aaf.framework.system.config.service.SystemConfigService}。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class SystemConfigApiService {

    private final com.xuejiai.aaf.framework.system.config.service.SystemConfigService configService;
    private final SystemConfigRepository configRepository;

    public List<SystemConfigVO> listByCategory(String category) {
        return configService.listByCategory(category).stream().map(this::toVO).toList();
    }

    @Transactional
    public SystemConfigVO create(SystemConfigCreateDTO dto) {
        var config = new SystemConfig();
        config.setCategory(dto.category());
        config.setConfigKey(dto.configKey());
        config.setValue(dto.value());
        config.setDefaultValue(dto.defaultValue());
        config.setValueType(dto.valueType() != null ? dto.valueType() : "string");
        config.setName(dto.name());
        config.setDescription(dto.description());
        config.setVisible(dto.visible() != null ? dto.visible() : true);
        config.setEditable(dto.editable() != null ? dto.editable() : true);
        return toVO(configRepository.save(config));
    }

    @Transactional
    public void delete(Long id) {
        var config =
                configRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("配置项不存在"));
        configService.evict(config.getConfigKey());
        configRepository.deleteById(id);
    }

    public SystemConfigVO toVO(SystemConfig c) {
        var value = Boolean.TRUE.equals(c.getVisible()) ? c.getValue() : null;
        return new SystemConfigVO(
                c.getId(),
                c.getCategory(),
                c.getConfigKey(),
                value,
                c.getDefaultValue(),
                c.getValueType(),
                c.getName(),
                c.getDescription(),
                c.getVisible(),
                c.getEditable(),
                c.getUpdateTime());
    }
}
