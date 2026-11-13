-- ai-service: bảng lõi của trợ lý AI. user_id ở mọi bảng là Keycloak `sub` (giống work-service).

CREATE TABLE ai_conversations (
    id            UUID PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    workspace_id  UUID,
    project_id    UUID,
    title         VARCHAR(255),
    summary       TEXT,                              -- tóm tắt dồn phần lịch sử đã bị cắt khỏi cửa sổ
    summary_seq   INT,                               -- seq lớn nhất của ai_messages đã được gộp vào summary
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_conv_user ON ai_conversations (user_id, updated_at DESC);

CREATE TABLE ai_messages (
    id               UUID PRIMARY KEY,
    conversation_id  UUID        NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    seq              INT         NOT NULL,
    role             VARCHAR(16) NOT NULL,          -- USER | MODEL
    content          JSONB       NOT NULL,          -- Gemini Content.toJson(), giữ nguyên thoughtSignature
    input_tokens     INT,
    output_tokens    INT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, seq)
);

CREATE TABLE ai_pending_interactions (
    id               UUID PRIMARY KEY,
    conversation_id  UUID         NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    message_id       UUID         NOT NULL REFERENCES ai_messages (id) ON DELETE CASCADE,
    call_id          VARCHAR(64)  NOT NULL,         -- id do ai-service gán cho lời gọi hàm
    call_index       INT          NOT NULL,         -- thứ tự trong lượt, để gửi FunctionResponse đúng thứ tự
    tool_name        VARCHAR(64)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,         -- APPROVE_ACTION | ASK_USER | READ_RESULT
    payload          JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    response         JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    UNIQUE (conversation_id, call_id)
);
CREATE INDEX idx_ai_pending_conv ON ai_pending_interactions (conversation_id, status);

CREATE TABLE ai_user_memories (
    id                      UUID PRIMARY KEY,
    user_id                 VARCHAR(64)  NOT NULL,
    scope                   VARCHAR(16)  NOT NULL,  -- GLOBAL | PROJECT
    project_id              UUID,
    category                VARCHAR(16)  NOT NULL,  -- PREFERENCE | CONTEXT | HABIT
    content                 TEXT         NOT NULL,
    source_conversation_id  UUID REFERENCES ai_conversations (id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ
);
CREATE INDEX idx_ai_mem_user ON ai_user_memories (user_id) WHERE deleted_at IS NULL;

CREATE TABLE ai_user_settings (
    user_id               VARCHAR(64) PRIMARY KEY,
    memory_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    daily_request_limit   INT,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_usage_logs (
    id                  UUID PRIMARY KEY,
    user_id             VARCHAR(64)   NOT NULL,
    conversation_id     UUID,
    model               VARCHAR(64)   NOT NULL,
    input_tokens        INT           NOT NULL,
    output_tokens       INT           NOT NULL,
    thinking_tokens     INT           NOT NULL DEFAULT 0,
    cached_tokens       INT           NOT NULL DEFAULT 0,
    cost_usd            NUMERIC(10,6) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_usage_user_time ON ai_usage_logs (user_id, created_at);
