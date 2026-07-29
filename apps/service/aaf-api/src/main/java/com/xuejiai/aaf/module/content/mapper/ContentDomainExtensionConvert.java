package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentDomainExtension;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionCreateDTO;

/**
 * 行业扩展对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentDomainExtensionConvert {

    ContentDomainExtensionConvert INSTANCE = Mappers.getMapper(ContentDomainExtensionConvert.class);

    ContentDomainExtension toEntity(ContentDomainExtensionCreateDTO dto);
}
