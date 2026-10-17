package com.xuejiai.aaf.module.elder.profile.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 长者中心跨子域只读契约；guard、mall 与 agent 只能通过此接口读取 profile。 */
public interface ElderQueryApi {

    ElderOverview requireOverview(Long elderId);

    /** 校验当前数据归属用户对长者具备指定关系授权，并返回可信范围。 */
    AuthorizedElder requireAccess(Long elderId, String authorizationScope);

    /** 查询当前数据归属用户在指定关系授权下可访问的长者，供无模型 ID 的工具上下文解析。 */
    List<AuthorizedElder> listAccessibleElders(String authorizationScope);

    List<ContactSnapshot> listContacts(Long elderId);

    RobotBrief requireRobotBrief(Long elderId);

    record ElderOverview(
            Long id,
            Long userId,
            String fullName,
            String preferredName,
            String gender,
            LocalDate birthDate,
            String livingArrangement,
            String status) {}

    record AuthorizedElder(
            ElderOverview overview, Long orgId, Long workspaceId, Long requestingOwnerId) {}

    record ContactSnapshot(
            Long id,
            Long elderId,
            Long userId,
            String name,
            String mobile,
            String relationship,
            Integer priority,
            Boolean emergencyContact,
            Set<String> authorizationScopes) {}

    record RobotBrief(
            Long elderId,
            String preferredName,
            String livingArrangement,
            String preferences,
            String taboos) {}
}
