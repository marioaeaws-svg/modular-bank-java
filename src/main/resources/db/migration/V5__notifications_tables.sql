-- Note: user_id columns reference auth.users but no FK constraint is added (cross-schema module isolation).

CREATE TYPE notifications.notification_type AS ENUM (
    'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'ACCOUNT_CREATED', 'LOGIN'
);

CREATE TABLE notifications.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type notifications.notification_type NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id ON notifications.notifications(user_id);
