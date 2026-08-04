package com.xuejiai.aaf.module.ai.aigc.configuration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionCreateDTO;

/**
 * 行业扩展对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface AigcDomainExtensionConvert {

    AigcDomainExtensionConvert INSTANCE = Mappers.getMapper(AigcDomainExtensionConvert.class);

    AigcDomainExtension toEntity(AigcDomainExtensionCreateDTO dto);
}
