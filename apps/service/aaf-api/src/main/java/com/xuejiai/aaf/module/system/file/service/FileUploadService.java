package com.xuejiai.aaf.module.system.file.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.module.system.file.api.StoredFile;

import lombok.RequiredArgsConstructor;

/** 文件上传门面，原子组合物理上传与 sys_file 登记。 */
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileService fileService;
    private final FileRecordService fileRecordService;

    /** 当前用户上传 Multipart 文件。 */
    public StoredFile uploadCurrent(MultipartFile file) {
        var physical = fileService.upload(file);
        return registerCurrentOrCompensate(physical);
    }

    /** 从远程 URL 持久化系统生成文件。 */
    public StoredFile uploadFromUrl(
            String url, String path, String contentType, Long uploaderId) {
        var physical = fileService.uploadFromUrl(url, path, contentType);
        return registerOrCompensate(physical, uploaderId);
    }

    /** 从字节数组持久化系统生成文件。 */
    public StoredFile uploadFromBytes(
            byte[] bytes, String path, String contentType, Long uploaderId) {
        var physical = fileService.uploadFromBytes(bytes, path, contentType);
        return registerOrCompensate(physical, uploaderId);
    }

    /** 从 Base64 持久化系统生成文件。 */
    public StoredFile uploadFromBase64(String base64, String path, Long uploaderId) {
        var physical = fileService.uploadFromBase64(base64, path);
        return registerOrCompensate(physical, uploaderId);
    }

    private StoredFile registerCurrentOrCompensate(FileVO physical) {
        try {
            return fileRecordService.registerCurrent(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash());
        } catch (RuntimeException registrationFailure) {
            compensatePhysicalObject(physical.key(), registrationFailure);
            throw registrationFailure;
        }
    }

    private StoredFile registerOrCompensate(FileVO physical, Long uploaderId) {
        try {
            return fileRecordService.register(
                    physical.key(),
                    physical.filename(),
                    physical.contentType(),
                    physical.size(),
                    physical.contentHash(),
                    uploaderId);
        } catch (RuntimeException registrationFailure) {
            compensatePhysicalObject(physical.key(), registrationFailure);
            throw registrationFailure;
        }
    }

    private void compensatePhysicalObject(String key, RuntimeException registrationFailure) {
        try {
            fileService.delete(key);
        } catch (RuntimeException compensationFailure) {
            registrationFailure.addSuppressed(compensationFailure);
        }
    }
}
