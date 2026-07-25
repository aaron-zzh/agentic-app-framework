-- 旧 ChatTask 队列已由 DelegatedTask + AgentTaskRuntime 取代。
-- 仅重命名归档，保留全部历史数据，不执行 DROP。

ALTER TABLE ai_chat_task RENAME TO ai_chat_task_archive_v1;
ALTER TABLE ai_chat_task_archive_v1
    RENAME CONSTRAINT ai_chat_task_pkey TO ai_chat_task_archive_v1_pkey;
ALTER INDEX idx_ai_chat_task_conversation RENAME TO idx_ai_chat_task_archive_v1_conversation;
ALTER INDEX idx_ai_chat_task_status RENAME TO idx_ai_chat_task_archive_v1_status;
ALTER SEQUENCE ai_chat_task_id_seq RENAME TO ai_chat_task_archive_v1_id_seq;

ALTER TABLE ai_task_execution RENAME TO ai_task_execution_archive_v1;
ALTER TABLE ai_task_execution_archive_v1
    RENAME CONSTRAINT ai_task_execution_pkey TO ai_task_execution_archive_v1_pkey;
ALTER TABLE ai_task_execution_archive_v1
    RENAME CONSTRAINT ai_task_execution_agent_id_fkey
    TO ai_task_execution_archive_v1_agent_id_fkey;
ALTER INDEX idx_ai_task_execution_task RENAME TO idx_ai_task_execution_archive_v1_task;
ALTER INDEX idx_ai_task_execution_parent RENAME TO idx_ai_task_execution_archive_v1_parent;
ALTER INDEX idx_ai_task_execution_workflow RENAME TO idx_ai_task_execution_archive_v1_workflow;
ALTER SEQUENCE ai_task_execution_id_seq RENAME TO ai_task_execution_archive_v1_id_seq;

ALTER TABLE ai_task_checkpoint RENAME TO ai_task_checkpoint_archive_v1;
ALTER TABLE ai_task_checkpoint_archive_v1
    RENAME CONSTRAINT ai_task_checkpoint_pkey TO ai_task_checkpoint_archive_v1_pkey;
ALTER INDEX idx_ai_task_checkpoint_exec RENAME TO idx_ai_task_checkpoint_archive_v1_exec;
ALTER SEQUENCE ai_task_checkpoint_id_seq RENAME TO ai_task_checkpoint_archive_v1_id_seq;
