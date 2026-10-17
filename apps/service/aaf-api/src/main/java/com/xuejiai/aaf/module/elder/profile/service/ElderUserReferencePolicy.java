package com.xuejiai.aaf.module.elder.profile.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.reference.ReferenceContext;
import com.xuejiai.aaf.framework.crud.reference.ReferencePolicy;
import com.xuejiai.aaf.module.system.org.service.MemberQueryService;

import lombok.RequiredArgsConstructor;

/** 长者与联系人所引用 AAF 用户的组织、工作区附加收紧策略。 */
@Component("elderUserReferencePolicy")
@RequiredArgsConstructor
public final class ElderUserReferencePolicy implements ReferencePolicy {

    private final MemberQueryService memberQueryService;

    @Override
    public Set<ReferenceContext> filterReadable(Set<ReferenceContext> contexts) {
        return filterMembers(contexts);
    }

    @Override
    public Set<ReferenceContext> filterReferenceable(Set<ReferenceContext> contexts) {
        return filterMembers(contexts);
    }

    private Set<ReferenceContext> filterMembers(Set<ReferenceContext> contexts) {
        if (contexts.isEmpty()) {
            return Set.of();
        }
        var first = contexts.iterator().next();
        if (first.orgId() == null) {
            return Set.of();
        }
        var userIds =
                contexts.stream().map(context -> context.target().id()).collect(Collectors.toSet());
        var orgMembers = memberQueryService.findOrgMemberUserIds(first.orgId(), userIds);
        Set<Long> workspaceMembers =
                first.workspaceId() == null
                        ? orgMembers
                        : memberQueryService.findWorkspaceMemberUserIds(
                                first.workspaceId(), userIds);
        return contexts.stream()
                .filter(context -> orgMembers.contains(context.target().id()))
                .filter(context -> workspaceMembers.contains(context.target().id()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
