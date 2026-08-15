-- ADR-COMMS-001 (message_attachments) and ADR-COMMS-002 (conversation_participants).
-- ADR-NOTIF-001 (notification_deliveries).

CREATE TABLE conversation_participants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    participant_user_id uuid REFERENCES users (id),
    participant_client_id uuid REFERENCES clients (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    CONSTRAINT chk_conversation_participants_single_participant CHECK (
        (participant_user_id IS NOT NULL AND participant_client_id IS NULL)
        OR (participant_user_id IS NULL AND participant_client_id IS NOT NULL)
    )
);
CREATE INDEX idx_conversation_participants_conversation_id ON conversation_participants (conversation_id);

CREATE TABLE message_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    message_id uuid NOT NULL REFERENCES messages (id),
    storage_key varchar(1024) NOT NULL,
    original_filename varchar(255) NOT NULL,
    mime_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_attachments_message_id ON message_attachments (message_id);

CREATE TABLE notification_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id uuid NOT NULL REFERENCES notifications (id),
    channel varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    provider_reference varchar(255),
    sent_at timestamptz,
    failed_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_deliveries_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH', 'SMS'))
);
CREATE INDEX idx_notification_deliveries_notification_id ON notification_deliveries (notification_id);
