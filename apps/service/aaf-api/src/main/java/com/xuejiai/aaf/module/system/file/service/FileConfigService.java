package com.xuejiai.aaf.module.system.file.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;
import com.xuejiai.aaf.module.system.file.vo.FileConfigCreateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigUpdateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigVO;

import lombok.RequiredArgsConstructor;

/**
 * 文件存储配置业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class FileConfigService {

    private final FileConfigRepository fileConfigRepository;

    /**
     * 查询所有文件存储配置。
     *
     * @return 配置列表
     */
    public List<FileConfigVO> list() {
        return fileConfigRepository.findAll().stream().map(this::toVO).toList();
    }

    /**
     * 获取配置详情。
     *
     * @param id 配置 ID
     * @return 配置详情
     */
    public FileConfigVO getById(Long id) {
        return toVO(requireConfig(id));
    }

    /**
     * 创建文件存储配置。
     *
     * @param req 创建请求
     * @return 创建后的配置
     */
    @Transactional
    public FileConfigVO create(FileConfigCreateDTO req) {
        var config = new FileConfig();
        config.setName(req.name());
        config.setStorageType(req.storageType());
        config.setConfig(req.config());
        fileConfigRepository.save(config);
        return toVO(config);
    }

    /**
     * 更新文件存储配置。
     *
     * @param id 配置 ID
     * @param req 更新请求
     * @return 更新后的配置
     */
    @Transactional
    public FileConfigVO update(Long id, FileConfigUpdateDTO req) {
        var config = requireConfig(id);
        if (req.name() != null) config.setName(req.name());
        if (req.storageType() != null) config.setStorageType(req.storageType());
        if (req.config() != null) config.setConfig(req.config());
        if (req.status() != null) config.setStatus(req.status());
        fileConfigRepository.save(config);
        return toVO(config);
    }

    /**
     * 删除文件存储配置。
     *
     * @param id 配置 ID
     */
    @Transactional
    public void delete(Long id) {
        fileConfigRepository.deleteById(id);
    }

    /**
     * 设为主配置。
     *
     * @param id 配置 ID
     */
    @Transactional
    public void setMaster(Long id) {
        requireConfig(id);
        fileConfigRepository.clearMaster();
        var config = requireConfig(id);
        config.setMaster(true);
        fileConfigRepository.save(config);
    }

    private FileConfig requireConfig(Long id) {
        return fileConfigRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文件存储配置不存在"));
    }

    private FileConfigVO toVO(FileConfig entity) {
        return new FileConfigVO(
                entity.getId(),
                entity.getName(),
                entity.getStorageType(),
                entity.getConfig(),
                entity.getMaster(),
                entity.getStatus(),
                entity.getCreateTime());
    }
}
