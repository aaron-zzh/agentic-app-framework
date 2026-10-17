package com.xuejiai.aaf.module.elder.album.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.album.domain.ElderPhoto;
import com.xuejiai.aaf.module.elder.album.service.ElderPhotoService;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoCreateDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoPageDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoUpdateDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 长者相册管理接口。 */
@Tag(name = "养老-相册")
@RestController
@RequestMapping(ElderPhotoService.BASE_PATH)
public class ElderPhotoController
        extends BaseCrudController<
                ElderPhoto,
                ElderPhotoVO,
                ElderPhotoCreateDTO,
                ElderPhotoUpdateDTO,
                ElderPhotoPageDTO> {

    private final ElderPhotoService service;

    public ElderPhotoController(ElderPhotoService service) {
        this.service = service;
    }

    @Override
    protected ElderPhotoService getService() {
        return service;
    }

    /** 内容地址先执行照片读取权限，再从 MASTER 存储读取正文。 */
    @Operation(summary = "受控读取照片原图")
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        var content = service.openContent(id);
        var disposition =
                ContentDisposition.builder("inline")
                        .filename(content.filename(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.size())
                .body(new InputStreamResource(content.input()));
    }
}
