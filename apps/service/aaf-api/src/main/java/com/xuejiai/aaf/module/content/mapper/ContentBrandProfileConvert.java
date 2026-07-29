package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentBrandProfile;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileCreateDTO;

/**
 * 品牌/IP 资料对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentBrandProfileConvert {

    ContentBrandProfileConvert INSTANCE = Mappers.getMapper(ContentBrandProfileConvert.class);

    ContentBrandProfile toEntity(ContentBrandProfileCreateDTO dto);
}
