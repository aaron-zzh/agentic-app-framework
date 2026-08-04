package com.xuejiai.aaf.module.system.file.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;

import lombok.RequiredArgsConstructor;

/** 文件上传门面，原子组合物理上传、文件登记与引用生命周期。 */
@Service
@RequiredArgsConstructor
public class FileUploadService implements FileStoragePort {

    private final FileService fileService;
    private final FileRecordApi fileRecordApi;

    @Override
    public StoredFile uploadCurrent(MultipartFile file) {
        var physical = fileService.upload(file);
        return registerCurrentOrCompensate(physical);
    }

    @Override
    public StoredFile uploadFromUrl(String url, String path, String contentType, Long uploaderId) {
        var physical = fileService.uploadFromUrl(url, path, contentType);
        return registerOrCompensate(physical, uploaderId);
    }

    @Override
    public StoredFile uploadFromBytes(
            byte[] bytes, String path, String contentType, Long uploaderId) {
        var physical = fileService.uploadFromBytes(bytes, path, contentType);
        return registerOrCompensate(physical, uploaderId);
    }

    @Override
    public StoredFile uploadFromBase64(String base64, String path, Long uploaderId) {
        var physical = fileService.uploadFromBase64(base64, path);
        return registerOrCompensate(physical, uploaderId);
    }

    @Override
    public StoredFile get(Long fileId) {
        return fileRecordApi.get(fileId);
    }

    @Override
    public String getAccessibleUrl(Long fileId) {
        return fileRecordApi.getAccessibleUrl(fileId);
    }

    @Override
    public void retain(Long fileId, FileReference reference) {
        fileRecordApi.retain(fileId, reference);
    }

    @Override
    public void release(Long fileId, FileReference reference) {
        fileRecordApi.release(fileId, reference);
    }

    @Override
    public void requestDelete(Long fileId) {
        fileRecordApi.requestDelete(fileId);
    }

    private StoredFile registerCurrentOrCompensate(FileVO physical) {
        try {
            return fileRecordApi.registerCurrent(
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
            return fileRecordApi.register(
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
