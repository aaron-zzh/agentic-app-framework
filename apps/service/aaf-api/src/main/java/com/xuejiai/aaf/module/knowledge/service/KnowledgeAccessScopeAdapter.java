package com.xuejiai.aaf.module.knowledge.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAccessScopePort;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedKnowledgeBase;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedScope;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Visibility;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;

import lombok.RequiredArgsConstructor;

/** 知识库授权范围适配器；候选解析完成后才允许启动任何检索分支。 */
@Component
@RequiredArgsConstructor
public class KnowledgeAccessScopeAdapter implements KnowledgeAccessScopePort {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    @Override
    public AuthorizedScope resolve(AuthorizedQuery query) {
        if (!query.includePublic() && query.requestedKnowledgeBaseIds().isEmpty()) {
            return new AuthorizedScope(Map.of(), "empty");
        }
        var candidates =
                jdbcTemplate.query(
                        """
                        SELECT stable_id, visibility, owner_id, org_id, workspace_id, scope_code
                        FROM ai_knowledge_base
                        WHERE deleted = false AND status = 0
                          AND ((? AND visibility = 'SYSTEM_PUBLIC')
                               OR stable_id = ANY(CAST(? AS uuid[])))
                        ORDER BY stable_id
                        """,
                        (rs, rowNum) ->
                                new Candidate(
                                        rs.getObject(1, UUID.class),
                                        Visibility.valueOf(rs.getString(2)),
                                        rs.getObject(3, Long.class),
                                        rs.getObject(4, Long.class),
                                        rs.getObject(5, Long.class),
                                        rs.getString(6)),
                        query.includePublic(),
                        uuidArray(query.requestedKnowledgeBaseIds()));
        var allowed = new LinkedHashMap<UUID, AuthorizedKnowledgeBase>();
        var snapshot = new StringBuilder();
        for (var candidate : candidates) {
            var decision = authorizationService.authorize(request(query, candidate));
            snapshot.append(candidate.id())
                    .append(':')
                    .append(decision.policyVersion())
                    .append(';');
            if (decision.allowed()) {
                allowed.put(
                        candidate.id(),
                        new AuthorizedKnowledgeBase(candidate.id(), candidate.visibility(), 1.0));
            }
        }
        return new AuthorizedScope(Map.copyOf(allowed), snapshot.toString());
    }

    private AuthorizationRequest request(AuthorizedQuery query, Candidate candidate) {
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        null,
                        new AuthorizationPlan.PolicyPlan());
        var facts = new LinkedHashMap<String, Object>();
        facts.put("visibility", candidate.visibility().name());
        facts.put("ownerId", Objects.toString(candidate.ownerId(), ""));
        facts.put("orgId", Objects.toString(candidate.orgId(), ""));
        facts.put("workspaceId", Objects.toString(candidate.workspaceId(), ""));
        facts.put("scopeCode", Objects.toString(candidate.scopeCode(), ""));
        facts.put("sourceFilters", query.sourceFilters());
        return new AuthorizationRequest(
                query.subject(),
                new AuthorizationTarget(
                        "knowledge.knowledge-base", "search", candidate.id().toString()),
                plan,
                facts,
                Duration.ofMinutes(10));
    }

    private String uuidArray(Iterable<UUID> ids) {
        var values = new java.util.ArrayList<String>();
        ids.forEach(id -> values.add(id.toString()));
        return "{" + String.join(",", values) + "}";
    }

    private record Candidate(
            UUID id,
            Visibility visibility,
            Long ownerId,
            Long orgId,
            Long workspaceId,
            String scopeCode) {}
}
