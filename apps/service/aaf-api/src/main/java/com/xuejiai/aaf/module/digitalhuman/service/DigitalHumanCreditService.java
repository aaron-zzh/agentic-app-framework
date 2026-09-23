package com.xuejiai.aaf.module.digitalhuman.service;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.credit.CreditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数字人积分计算服务
 *
 * <p>根据Blueprint定义的公式计算视频生成消耗的积分，
 * 并集成现有的CreditService进行实际扣减。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanCreditService {

    private final CreditService creditService;

    /**
     * 计算简单视频生成的积分消耗
     *
     * <p>公式（来自Blueprint）:
     * <pre>
     * baseCost = 50
     * durationCost = duration * 2
     * totalCost = baseCost + durationCost
     * </pre>
     *
     * @param duration 视频时长（秒）
     * @return 消耗的积分数
     */
    public int calculateSimpleVideoCost(int duration) {
        int baseCost = 50;
        int durationCost = duration * 2;
        int totalCost = baseCost + durationCost;

        log.debug("Simple video cost: duration={}s, base={}, duration_cost={}, total={}",
                  duration, baseCost, durationCost, totalCost);

        return totalCost;
    }

    /**
     * 计算复杂视频生成的积分消耗
     *
     * <p>公式（来自Blueprint）:
     * <pre>
     * baseCost = 100
     * durationCost = duration * 3
     * sceneCost = sceneCount * 10
     * totalCost = baseCost + durationCost + sceneCost
     * </pre>
     *
     * @param duration 视频时长（秒）
     * @param sceneCount 场景数量
     * @return 消耗的积分数
     */
    public int calculateComplexVideoCost(int duration, int sceneCount) {
        int baseCost = 100;
        int durationCost = duration * 3;
        int sceneCost = sceneCount * 10;
        int totalCost = baseCost + durationCost + sceneCost;

        log.debug("Complex video cost: duration={}s, scenes={}, base={}, duration_cost={}, scene_cost={}, total={}",
                  duration, sceneCount, baseCost, durationCost, sceneCost, totalCost);

        return totalCost;
    }

    /**
     * 智能计算视频生成的积分消耗
     *
     * <p>根据时长自动选择简单或复杂视频的计算公式。
     *
     * @param duration 视频时长（秒）
     * @param sceneCount 场景数量（可选，复杂视频需要）
     * @return 消耗的积分数
     */
    public int calculateVideoCost(int duration, Integer sceneCount) {
        // 15秒及以下使用简单视频公式
        if (duration <= 15) {
            return calculateSimpleVideoCost(duration);
        }

        // 超过15秒使用复杂视频公式
        int actualSceneCount = sceneCount != null ? sceneCount : estimateSceneCount(duration);
        return calculateComplexVideoCost(duration, actualSceneCount);
    }

    /**
     * 估算场景数量
     *
     * <p>当场景数量未提供时，根据时长估算。
     * 假设每个场景平均10秒。
     *
     * @param duration 视频时长（秒）
     * @return 估算的场景数量
     */
    private int estimateSceneCount(int duration) {
        // 每个场景平均10秒
        int sceneCount = (duration + 9) / 10; // 向上取整

        // 至少1个场景
        return Math.max(1, sceneCount);
    }

    /**
     * 计算图片生成的积分消耗
     *
     * <p>用于数字人形象生成。
     *
     * @return 消耗的积分数
     */
    public int calculateImageGenerationCost() {
        // 固定积分消耗
        return 30;
    }

    /**
     * 计算语音合成的积分消耗
     *
     * <p>用于音色预览等。
     *
     * @param duration 音频时长（秒）
     * @return 消耗的积分数
     */
    public int calculateVoiceSynthesisCost(int duration) {
        int baseCost = 10;
        int durationCost = duration * 1;
        return baseCost + durationCost;
    }

    /**
     * 检查用户积分余额是否足够
     *
     * @param userId 用户ID
     * @param requiredCredits 需要的积分数
     * @return true如果余额足够
     */
    public boolean hasEnoughCredits(Long userId, int requiredCredits) {
        return creditService.hasBudget(userId, requiredCredits);
    }

    /**
     * 扣除用户积分
     *
     * <p>用于视频生成等消费场景。
     *
     * @param userId 用户ID
     * @param credits 扣除的积分数
     * @param source 来源描述（如"数字人视频生成"）
     * @param category 分类（如"video_generation"）
     * @param bizId 业务ID（如projectId）
     * @param bizType 业务类型（如"DIGITAL_HUMAN"）
     * @return 交易ID
     */
    public Long deductCredits(Long userId, int credits, String source, String category,
                              String bizId, String bizType) {
        // 使用CreditService.spend扣除积分
        // overdraftLimit=0 表示不允许透支
        Long txId = creditService.spend(
            userId,
            credits,
            source,
            category,
            bizId,
            0L, // 不允许透支
            null, // remark
            bizType
        );

        log.info("Credits deducted: userId={}, credits={}, source={}, bizId={}, txId={}",
                 userId, credits, source, bizId, txId);

        return txId;
    }

    /**
     * 退还用户积分
     *
     * <p>当视频生成失败时退还积分。
     *
     * @param creditTxId 原扣款交易ID
     * @param reason 退还原因
     * @return 退还交易ID，如果退还失败返回null
     */
    public Long refundCredits(Long creditTxId, String reason) {
        Long refundTxId = creditService.refund(creditTxId, reason);

        if (refundTxId != null) {
            log.info("Credits refunded: originalTxId={}, reason={}, refundTxId={}",
                     creditTxId, reason, refundTxId);
        } else {
            log.warn("Credits refund failed: originalTxId={}, reason={}", creditTxId, reason);
        }

        return refundTxId;
    }

    /**
     * 获取用户积分余额
     *
     * @param userId 用户ID
     * @return 可用余额（不含冻结）
     */
    public long getBalance(Long userId) {
        return creditService.getBalance(userId);
    }
}
