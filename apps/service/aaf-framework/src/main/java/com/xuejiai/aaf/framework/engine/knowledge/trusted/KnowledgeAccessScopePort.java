package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedScope;

/** 在任何检索分支执行前解析当前主体可读的知识库集合。 */
public interface KnowledgeAccessScopePort {

    AuthorizedScope resolve(AuthorizedQuery query);
}
