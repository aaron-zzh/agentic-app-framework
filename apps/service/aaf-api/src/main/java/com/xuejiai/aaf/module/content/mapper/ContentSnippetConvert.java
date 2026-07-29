package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentSnippet;
import com.xuejiai.aaf.module.content.vo.ContentSnippetCreateDTO;

/**
 * 创作片段对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentSnippetConvert {

    ContentSnippetConvert INSTANCE = Mappers.getMapper(ContentSnippetConvert.class);

    ContentSnippet toEntity(ContentSnippetCreateDTO dto);
}
