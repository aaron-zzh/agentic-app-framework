package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefCreateDTO;

/**
 * 项目资料引用对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectProfileRefConvert {

    ContentProjectProfileRefConvert INSTANCE =
            Mappers.getMapper(ContentProjectProfileRefConvert.class);

    ContentProjectProfileRef toEntity(ContentProjectProfileRefCreateDTO dto);
}
