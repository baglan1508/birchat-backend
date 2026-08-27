CREATE SCHEMA IF NOT EXISTS birchat;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS birchat.users (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(30) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    display_name VARCHAR(100),
    initials VARCHAR(10),
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS birchat.roles (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
    );

CREATE TABLE IF NOT EXISTS birchat.companies (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    field VARCHAR(255),
    logo_url TEXT,
    initial VARCHAR(10),
    color_hex VARCHAR(20),
    owner_user_id UUID NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_companies_owner
    FOREIGN KEY (owner_user_id) REFERENCES birchat.users(id)
    );

CREATE TABLE IF NOT EXISTS birchat.company_members (
                                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    position VARCHAR(255),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    joined_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_company_members_company
    FOREIGN KEY (company_id) REFERENCES birchat.companies(id),

    CONSTRAINT fk_company_members_user
    FOREIGN KEY (user_id) REFERENCES birchat.users(id),

    CONSTRAINT fk_company_members_role
    FOREIGN KEY (role_id) REFERENCES birchat.roles(id),

    CONSTRAINT uq_company_member
    UNIQUE (company_id, user_id)
    );

CREATE TABLE IF NOT EXISTS birchat.chats (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    created_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_chats_company
    FOREIGN KEY (company_id) REFERENCES birchat.companies(id),

    CONSTRAINT uq_company_chat_type
    UNIQUE (company_id, type)
    );

CREATE TABLE IF NOT EXISTS birchat.chat_messages (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    chat_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT,
    reply_to_message_id UUID,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_chat_messages_company
    FOREIGN KEY (company_id) REFERENCES birchat.companies(id),

    CONSTRAINT fk_chat_messages_chat
    FOREIGN KEY (chat_id) REFERENCES birchat.chats(id),

    CONSTRAINT fk_chat_messages_user
    FOREIGN KEY (user_id) REFERENCES birchat.users(id),

    CONSTRAINT fk_chat_messages_reply
    FOREIGN KEY (reply_to_message_id) REFERENCES birchat.chat_messages(id)
    );

INSERT INTO birchat.roles (code, name) VALUES
                                           ('DIRECTOR', 'Директор'),
                                           ('ACCOUNTANT', 'Бухгалтер'),
                                           ('BUYER', 'Закупщик'),
                                           ('WAREHOUSE', 'Склад'),
                                           ('EMPLOYEE', 'Сотрудник'),
                                           ('ADMIN', 'Администратор')
    ON CONFLICT (code) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_company_members_company_id
    ON birchat.company_members(company_id);

CREATE INDEX IF NOT EXISTS idx_company_members_user_id
    ON birchat.company_members(user_id);

CREATE INDEX IF NOT EXISTS idx_chats_company_id
    ON birchat.chats(company_id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_company_chat_created
    ON birchat.chat_messages(company_id, chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user_id
    ON birchat.chat_messages(user_id);