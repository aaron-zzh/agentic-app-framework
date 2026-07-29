package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunCreateDTO;

/**
 * 执行记录对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentExecutionRunConvert {

    ContentExecutionRunConvert INSTANCE = Mappers.getMapper(ContentExecutionRunConvert.class);

    ContentExecutionRun toEntity(ContentExecutionRunCreateDTO dto);
}
