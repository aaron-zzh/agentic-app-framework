package com.xuejiai.aaf.module.brokerage.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageInviteCodeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 邀请码服务。
 *
 * <p>短码格式：AAF-XXXXX（5位大写字母+数字），碰撞概率约 1/60M，生产够用。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageInviteCodeService {

    private static final String PREFIX = "AAF-";
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆字符 0/O/1/I
    private static final int CODE_LEN = 5;
    private static final int MAX_RETRY = 10;

    private final BrokerageInviteCodeRepository inviteCodeRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * 获取或生成邀请码（同一 contactId + channel 只生成一次）。
     *
     * @param contactId 分销员 contact_id
     * @param channel 推广来源，null=默认
     */
    @Transactional
    public BrokerageInviteCode getOrCreate(Long contactId, String channel) {
        Optional<BrokerageInviteCode> existing =
                channel == null
                        ? inviteCodeRepository.findByContactId(contactId).stream()
                                .filter(c -> c.getChannel() == null)
                                .findFirst()
                        : inviteCodeRepository.findByContactIdAndChannel(contactId, channel);
        if (existing.isPresent()) {
            return existing.get();
        }
        var invite = new BrokerageInviteCode();
        invite.setContactId(contactId);
        invite.setChannel(channel);
        invite.setCode(generateUniqueCode());
        return inviteCodeRepository.save(invite);
    }

    /** 按短码查询，返回 null=不存在 */
    public BrokerageInviteCode findByCode(String code) {
        return inviteCodeRepository.findByCode(code).orElse(null);
    }

    /** 查询某个联系人的所有邀请码 */
    public List<BrokerageInviteCode> listByContactId(Long contactId) {
        return inviteCodeRepository.findByContactId(contactId);
    }

    /** 标记使用次数+1 */
    @Transactional
    public void incrementUsed(String code) {
        inviteCodeRepository
                .findByCode(code)
                .ifPresent(
                        c -> {
                            c.setUsedCount(c.getUsedCount() + 1);
                            inviteCodeRepository.save(c);
                        });
    }

    // ========== 私有方法 ==========

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = PREFIX + randomSuffix();
            if (inviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        // 极小概率碰撞，加时间戳兜底
        return PREFIX + Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(3, 8);
    }

    private String randomSuffix() {
        var sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
