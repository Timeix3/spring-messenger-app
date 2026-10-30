CREATE TABLE users (
                       id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       username      VARCHAR(32)  NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       display_name  VARCHAR(64),
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_username ON users (lower(username));

CREATE TABLE conversations (
                               id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               user1_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               user2_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,

                               user1_last_read_message_id BIGINT,
                               user2_last_read_message_id BIGINT,

                               last_message_id BIGINT,
                               last_message_at TIMESTAMPTZ,
                               created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                               CONSTRAINT ck_conv_order CHECK (user1_id < user2_id),
                               CONSTRAINT uq_conv_pair  UNIQUE (user1_id, user2_id)
);

CREATE INDEX idx_conv_user1 ON conversations (user1_id, last_message_at DESC);
CREATE INDEX idx_conv_user2 ON conversations (user2_id, last_message_at DESC);

CREATE TABLE messages (
                          id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          conversation_id BIGINT      NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                          sender_id       BIGINT      NOT NULL REFERENCES users(id),
                          content         TEXT        NOT NULL,
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                          CONSTRAINT ck_msg_content CHECK (char_length(content) BETWEEN 1 AND 4000)
);

CREATE INDEX idx_messages_conv_id ON messages (conversation_id, id DESC);

ALTER TABLE conversations
    ADD CONSTRAINT fk_conv_last_msg  FOREIGN KEY (last_message_id)           REFERENCES messages(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_conv_u1_read   FOREIGN KEY (user1_last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_conv_u2_read   FOREIGN KEY (user2_last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL;