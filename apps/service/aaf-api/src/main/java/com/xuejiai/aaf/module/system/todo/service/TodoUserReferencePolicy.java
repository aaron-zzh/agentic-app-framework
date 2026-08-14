package com.xuejiai.aaf.module.system.todo.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.reference.ReferenceContext;
import com.xuejiai.aaf.framework.crud.reference.ReferencePolicy;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;

import lombok.RequiredArgsConstructor;

/** Todo 执行人和参与人的组织、工作区附加收紧策略。 */
@Component("todoUserReferencePolicy")
@RequiredArgsConstructor
public final class TodoUserReferencePolicy implements ReferencePolicy {

    private final OrgMemberRepository orgMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

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
        var orgMembers =
                orgMemberRepository
                        .findByOrgIdAndUserIdInAndDeletedFalse(first.orgId(), userIds)
                        .stream()
                        .map(OrgMember::getUserId)
                        .collect(Collectors.toSet());
        Set<Long> workspaceMembers =
                first.workspaceId() == null
                        ? orgMembers
                        : workspaceMemberRepository
                                .findByWorkspaceIdAndUserIdInAndDeletedFalse(
                                        first.workspaceId(), userIds)
                                .stream()
                                .map(WorkspaceMember::getUserId)
                                .collect(Collectors.toSet());
        return contexts.stream()
                .filter(context -> orgMembers.contains(context.target().id()))
                .filter(context -> workspaceMembers.contains(context.target().id()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
