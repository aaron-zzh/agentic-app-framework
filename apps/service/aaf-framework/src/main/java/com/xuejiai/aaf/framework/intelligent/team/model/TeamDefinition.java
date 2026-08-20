package com.xuejiai.aaf.framework.intelligent.team.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 基于统一定义生命周期持久化的最小 L4 Team 定义。 */
public record TeamDefinition(
        String teamId, long version, Strategy strategy, Member leader, List<Member> workers) {

    public TeamDefinition {
        teamId = requireText(teamId, "teamId");
        if (version < 1) {
            throw new IllegalArgumentException("Team version 必须大于 0");
        }
        Objects.requireNonNull(strategy, "strategy 不能为空");
        if (strategy != Strategy.LEADER_COORDINATED) {
            throw new IllegalArgumentException("MVP 仅支持 LEADER_COORDINATED Team");
        }
        Objects.requireNonNull(leader, "leader 不能为空");
        workers = List.copyOf(Objects.requireNonNull(workers, "workers 不能为空"));
        if (workers.isEmpty() || workers.size() > 8) {
            throw new IllegalArgumentException("Team 必须包含 1..8 个静态 Worker");
        }
        if (leader.role() != MemberRole.LEADER
                || workers.stream().anyMatch(member -> member.role() != MemberRole.WORKER)) {
            throw new IllegalArgumentException("Team 必须包含一个 Leader 和静态 Worker");
        }
        var keys = workers.stream().map(Member::memberKey).toList();
        if (keys.size() != Set.copyOf(keys).size() || keys.contains(leader.memberKey())) {
            throw new IllegalArgumentException("Team memberKey 必须唯一");
        }
    }

    public record Member(
            String memberKey,
            MemberRole role,
            String assistantId,
            long assistantRevision,
            String roleKey,
            String skillKey,
            Set<String> allowedToolKeys) {
        public Member {
            memberKey = requireText(memberKey, "memberKey");
            Objects.requireNonNull(role, "member role 不能为空");
            assistantId = requireText(assistantId, "assistantId");
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
            roleKey = requireText(roleKey, "roleKey");
            skillKey = requireText(skillKey, "skillKey");
            allowedToolKeys =
                    Set.copyOf(Objects.requireNonNull(allowedToolKeys, "allowedToolKeys 不能为空"));
            if (allowedToolKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("allowedToolKeys 不能包含空值");
            }
        }
    }

    public enum Strategy {
        LEADER_COORDINATED
    }

    public enum MemberRole {
        LEADER,
        WORKER
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
