package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.vo.ContentProjectCreateDTO;

/**
 * 内容项目对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectConvert {

    ContentProjectConvert INSTANCE = Mappers.getMapper(ContentProjectConvert.class);

    ContentProject toEntity(ContentProjectCreateDTO dto);
}
