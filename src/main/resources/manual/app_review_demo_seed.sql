-- BirChat App Store Review demo seed
-- Purpose:
--   1) Create service demo employees for company BirChat Demo.
--   2) Fill general chat with realistic employee messages for Apple review.
--   3) Hide old demo messages from service demo users.
--   4) Remove obvious test file t.txt from file list.
--   5) Clear cached AI summary so the next summary call is rebuilt from fresh messages.
--
-- IMPORTANT:
--   This file is a MANUAL script. Do not put it under db/migration.
--   Recommended path in repository:
--     src/main/resources/manual/app_review_demo_seed.sql
--
-- Run manually against prod database when preparing App Store review demo data.

BEGIN;

DO $$
DECLARE
    v_company_id UUID := '41768d00-320b-4b8e-82f7-47b8ee7dbeb1';
    v_chat_id UUID := 'a065615e-b273-47d0-8607-187528a31d2f';

    v_director_user_id UUID := '60c1e6c3-3368-44c0-b3f2-9c747c815a62';

    v_aigerim_user_id UUID := '10000000-0000-0000-0000-000000000001';
    v_erzhan_user_id  UUID := '10000000-0000-0000-0000-000000000002';
    v_dmitry_user_id  UUID := '10000000-0000-0000-0000-000000000003';
    v_madina_user_id  UUID := '10000000-0000-0000-0000-000000000004';

    v_director_role_id UUID := 'f6c74f77-fe08-4557-9ef9-77ddeeb801cb';
    v_accountant_role_id UUID := '1a16abf5-1cd1-4eef-941a-88e346ae40cc';
    v_buyer_role_id UUID := 'ef0ad2e0-d88e-46ad-8977-709d048b99fb';
    v_warehouse_role_id UUID := '9d3df510-2b8b-4da8-8d78-3b5c247c9ed7';
    v_employee_role_id UUID := 'b42b8ea5-2ae1-4fed-8096-a46646058073';

    v_now_utc TIMESTAMP := (NOW() AT TIME ZONE 'UTC');
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM birchat.companies c
        WHERE c.id = v_company_id
    ) THEN
        RAISE EXCEPTION 'Demo company % not found', v_company_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM birchat.chats ch
        WHERE ch.id = v_chat_id
          AND ch.company_id = v_company_id
          AND ch.type = 'GENERAL'
    ) THEN
        RAISE EXCEPTION 'Demo GENERAL chat % for company % not found', v_chat_id, v_company_id;
    END IF;

    UPDATE birchat.users
    SET full_name = 'Демо Директор',
        display_name = 'Демо',
        initials = 'ДД',
        is_active = TRUE,
        updated_at = v_now_utc
    WHERE id = v_director_user_id
       OR phone = '+77000000000';

    INSERT INTO birchat.users (
        id,
        phone,
        full_name,
        display_name,
        initials,
        avatar_url,
        is_active,
        created_at,
        updated_at
    )
    VALUES
        (v_aigerim_user_id, '+77000000001', 'Айгерим Сатпаева', 'Айгерим', 'АС', NULL, TRUE, v_now_utc, v_now_utc),
        (v_erzhan_user_id,  '+77000000002', 'Ержан Касымов',    'Ержан',   'ЕК', NULL, TRUE, v_now_utc, v_now_utc),
        (v_dmitry_user_id,  '+77000000003', 'Дмитрий Ли',       'Дмитрий', 'ДЛ', NULL, TRUE, v_now_utc, v_now_utc),
        (v_madina_user_id,  '+77000000004', 'Мадина Нурланова', 'Мадина',  'МН', NULL, TRUE, v_now_utc, v_now_utc)
    ON CONFLICT (phone) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        display_name = EXCLUDED.display_name,
        initials = EXCLUDED.initials,
        avatar_url = EXCLUDED.avatar_url,
        is_active = TRUE,
        updated_at = EXCLUDED.updated_at;

    SELECT id INTO v_aigerim_user_id FROM birchat.users WHERE phone = '+77000000001';
    SELECT id INTO v_erzhan_user_id  FROM birchat.users WHERE phone = '+77000000002';
    SELECT id INTO v_dmitry_user_id  FROM birchat.users WHERE phone = '+77000000003';
    SELECT id INTO v_madina_user_id  FROM birchat.users WHERE phone = '+77000000004';

    UPDATE birchat.company_members
    SET role_id = v_director_role_id,
        position = 'Директор',
        status = 'ACTIVE',
        joined_at = COALESCE(joined_at, v_now_utc)
    WHERE company_id = v_company_id
      AND user_id = v_director_user_id;

    INSERT INTO birchat.company_members (id, company_id, user_id, role_id, position, status, joined_at)
    SELECT gen_random_uuid(), v_company_id, v_aigerim_user_id, v_accountant_role_id, 'Главный бухгалтер', 'ACTIVE', v_now_utc
    WHERE NOT EXISTS (
        SELECT 1 FROM birchat.company_members
        WHERE company_id = v_company_id AND user_id = v_aigerim_user_id
    );

    INSERT INTO birchat.company_members (id, company_id, user_id, role_id, position, status, joined_at)
    SELECT gen_random_uuid(), v_company_id, v_erzhan_user_id, v_buyer_role_id, 'Закупщик', 'ACTIVE', v_now_utc
    WHERE NOT EXISTS (
        SELECT 1 FROM birchat.company_members
        WHERE company_id = v_company_id AND user_id = v_erzhan_user_id
    );

    INSERT INTO birchat.company_members (id, company_id, user_id, role_id, position, status, joined_at)
    SELECT gen_random_uuid(), v_company_id, v_dmitry_user_id, v_warehouse_role_id, 'Заведующий складом', 'ACTIVE', v_now_utc
    WHERE NOT EXISTS (
        SELECT 1 FROM birchat.company_members
        WHERE company_id = v_company_id AND user_id = v_dmitry_user_id
    );

    INSERT INTO birchat.company_members (id, company_id, user_id, role_id, position, status, joined_at)
    SELECT gen_random_uuid(), v_company_id, v_madina_user_id, v_employee_role_id, 'Менеджер по продажам', 'ACTIVE', v_now_utc
    WHERE NOT EXISTS (
        SELECT 1 FROM birchat.company_members
        WHERE company_id = v_company_id AND user_id = v_madina_user_id
    );

    UPDATE birchat.company_members
    SET role_id = v_accountant_role_id,
        position = 'Главный бухгалтер',
        status = 'ACTIVE'
    WHERE company_id = v_company_id
      AND user_id = v_aigerim_user_id;

    UPDATE birchat.company_members
    SET role_id = v_buyer_role_id,
        position = 'Закупщик',
        status = 'ACTIVE'
    WHERE company_id = v_company_id
      AND user_id = v_erzhan_user_id;

    UPDATE birchat.company_members
    SET role_id = v_warehouse_role_id,
        position = 'Заведующий складом',
        status = 'ACTIVE'
    WHERE company_id = v_company_id
      AND user_id = v_dmitry_user_id;

    UPDATE birchat.company_members
    SET role_id = v_employee_role_id,
        position = 'Менеджер по продажам',
        status = 'ACTIVE'
    WHERE company_id = v_company_id
      AND user_id = v_madina_user_id;

    UPDATE birchat.chat_messages
    SET is_deleted = TRUE,
        updated_at = v_now_utc
    WHERE company_id = v_company_id
      AND chat_id = v_chat_id
      AND user_id IN (
          v_director_user_id,
          v_aigerim_user_id,
          v_erzhan_user_id,
          v_dmitry_user_id,
          v_madina_user_id
      );

    INSERT INTO birchat.chat_messages (
        id,
        company_id,
        chat_id,
        user_id,
        type,
        content,
        reply_to_message_id,
        is_deleted,
        created_at,
        updated_at
    )
    VALUES
        ('20000000-0000-0000-0000-000000000001', v_company_id, v_chat_id, v_aigerim_user_id, 'TEXT',
         'Доброе утро! Сверка с поставщиком за сентябрь готова, расхождение 12 400 ₸.',
         NULL, FALSE, v_now_utc - INTERVAL '2 days 9 hours 5 minutes', v_now_utc - INTERVAL '2 days 9 hours 5 minutes'),

        ('20000000-0000-0000-0000-000000000002', v_company_id, v_chat_id, v_erzhan_user_id, 'TEXT',
         'Это по накладной от 26-го, там лишняя позиция. Напишу поставщику.',
         NULL, FALSE, v_now_utc - INTERVAL '2 days 8 hours 50 minutes', v_now_utc - INTERVAL '2 days 8 hours 50 minutes'),

        ('20000000-0000-0000-0000-000000000003', v_company_id, v_chat_id, v_dmitry_user_id, 'TEXT',
         'Приняли 40 коробок, 2 с повреждённой упаковкой. Фото сохранил, при необходимости приложу к акту.',
         NULL, FALSE, v_now_utc - INTERVAL '2 days 6 hours 30 minutes', v_now_utc - INTERVAL '2 days 6 hours 30 minutes'),

        ('20000000-0000-0000-0000-000000000004', v_company_id, v_chat_id, v_director_user_id, 'TEXT',
         'Ержан, повреждённые коробки возвращаем или берём со скидкой?',
         NULL, FALSE, v_now_utc - INTERVAL '2 days 3 hours', v_now_utc - INTERVAL '2 days 3 hours'),

        ('20000000-0000-0000-0000-000000000005', v_company_id, v_chat_id, v_erzhan_user_id, 'TEXT',
         'Договорился на скидку 15%, акт пришлю завтра.',
         NULL, FALSE, v_now_utc - INTERVAL '2 days 2 hours 45 minutes', v_now_utc - INTERVAL '2 days 2 hours 45 minutes'),

        ('20000000-0000-0000-0000-000000000006', v_company_id, v_chat_id, v_madina_user_id, 'TEXT',
         'Клиент из Шымкента просит отгрузку до пятницы, 120 единиц.',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 9 hours', v_now_utc - INTERVAL '1 day 9 hours'),

        ('20000000-0000-0000-0000-000000000007', v_company_id, v_chat_id, v_dmitry_user_id, 'TEXT',
         'На складе сейчас 96 единиц, остальное приедет в четверг.',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 8 hours 45 minutes', v_now_utc - INTERVAL '1 day 8 hours 45 minutes'),

        ('20000000-0000-0000-0000-000000000008', v_company_id, v_chat_id, v_madina_user_id, 'TEXT',
         'Тогда отгружаем двумя партиями, клиент согласен.',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 8 hours 25 minutes', v_now_utc - INTERVAL '1 day 8 hours 25 minutes'),

        ('20000000-0000-0000-0000-000000000009', v_company_id, v_chat_id, v_aigerim_user_id, 'TEXT',
         'Счёт клиенту выставила, договор добавила в раздел «Файлы».',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 5 hours 30 minutes', v_now_utc - INTERVAL '1 day 5 hours 30 minutes'),

        ('20000000-0000-0000-0000-000000000010', v_company_id, v_chat_id, v_erzhan_user_id, 'TEXT',
         'Акт по скидке загрузил, проверьте перед отправкой.',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 2 hours 10 minutes', v_now_utc - INTERVAL '1 day 2 hours 10 minutes'),

        ('20000000-0000-0000-0000-000000000011', v_company_id, v_chat_id, v_director_user_id, 'TEXT',
         'Спасибо. Завтра в 11:00 проведём короткую планёрку по поставкам.',
         NULL, FALSE, v_now_utc - INTERVAL '1 day 1 hour 55 minutes', v_now_utc - INTERVAL '1 day 1 hour 55 minutes'),

        ('20000000-0000-0000-0000-000000000012', v_company_id, v_chat_id, v_aigerim_user_id, 'TEXT',
         'Оплата от клиента поступила, назначение платежа совпадает со счётом.',
         NULL, FALSE, v_now_utc - INTERVAL '4 hours', v_now_utc - INTERVAL '4 hours'),

        ('20000000-0000-0000-0000-000000000013', v_company_id, v_chat_id, v_dmitry_user_id, 'TEXT',
         'Первая партия собрана, машина будет в 14:00.',
         NULL, FALSE, v_now_utc - INTERVAL '3 hours', v_now_utc - INTERVAL '3 hours'),

        ('20000000-0000-0000-0000-000000000014', v_company_id, v_chat_id, v_madina_user_id, 'TEXT',
         'Клиент подтвердил получение первой партии.',
         NULL, FALSE, v_now_utc - INTERVAL '2 hours', v_now_utc - INTERVAL '2 hours'),

        ('20000000-0000-0000-0000-000000000015', v_company_id, v_chat_id, v_erzhan_user_id, 'TEXT',
         'Вторая партия у поставщика на складе, забираем в четверг.',
         NULL, FALSE, v_now_utc - INTERVAL '1 hour', v_now_utc - INTERVAL '1 hour'),

        ('20000000-0000-0000-0000-000000000016', v_company_id, v_chat_id, v_aigerim_user_id, 'TEXT',
         'Напоминаю: до 15-го сдаём отчёт по НДС.',
         NULL, FALSE, v_now_utc - INTERVAL '30 minutes', v_now_utc - INTERVAL '30 minutes'),

        ('20000000-0000-0000-0000-000000000017', v_company_id, v_chat_id, v_director_user_id, 'TEXT',
         'Принято. После отгрузки обновите статус в общем чате.',
         NULL, FALSE, v_now_utc - INTERVAL '20 minutes', v_now_utc - INTERVAL '20 minutes')
    ON CONFLICT (id) DO UPDATE
    SET company_id = EXCLUDED.company_id,
        chat_id = EXCLUDED.chat_id,
        user_id = EXCLUDED.user_id,
        type = EXCLUDED.type,
        content = EXCLUDED.content,
        reply_to_message_id = EXCLUDED.reply_to_message_id,
        is_deleted = FALSE,
        created_at = EXCLUDED.created_at,
        updated_at = EXCLUDED.updated_at;

    IF to_regclass('birchat.chat_attachments') IS NOT NULL THEN
        EXECUTE
            'DELETE FROM birchat.chat_attachments
              WHERE file_id IN (
                    SELECT id
                    FROM birchat.company_files
                    WHERE company_id = $1
                      AND original_file_name = $2
              )'
        USING v_company_id, 't.txt';
    END IF;

    DELETE FROM birchat.company_files
    WHERE company_id = v_company_id
      AND original_file_name = 't.txt';

    IF to_regclass('birchat.ai_company_summaries') IS NOT NULL THEN
        DELETE FROM birchat.ai_company_summaries
        WHERE company_id = v_company_id;
    END IF;
END $$;

COMMIT;

-- Verification queries
-- 1) Employees: expected 5 active members.
-- SELECT
--     cm.company_id,
--     u.id AS user_id,
--     u.phone,
--     u.full_name,
--     u.display_name,
--     u.initials,
--     r.code AS role_code,
--     cm.position,
--     cm.status
-- FROM birchat.company_members cm
-- JOIN birchat.users u ON u.id = cm.user_id
-- JOIN birchat.roles r ON r.id = cm.role_id
-- WHERE cm.company_id = '41768d00-320b-4b8e-82f7-47b8ee7dbeb1'
-- ORDER BY
--     CASE r.code
--         WHEN 'DIRECTOR' THEN 1
--         WHEN 'ACCOUNTANT' THEN 2
--         WHEN 'BUYER' THEN 3
--         WHEN 'WAREHOUSE' THEN 4
--         WHEN 'EMPLOYEE' THEN 5
--         ELSE 99
--     END;
--
-- 2) Messages: expected 17 active demo messages, most from employees.
-- SELECT
--     m.id,
--     u.phone,
--     u.full_name,
--     u.initials,
--     m.type,
--     m.content,
--     m.is_deleted,
--     m.created_at
-- FROM birchat.chat_messages m
-- JOIN birchat.users u ON u.id = m.user_id
-- WHERE m.company_id = '41768d00-320b-4b8e-82f7-47b8ee7dbeb1'
--   AND m.chat_id = 'a065615e-b273-47d0-8607-187528a31d2f'
--   AND m.is_deleted = FALSE
-- ORDER BY m.created_at ASC;
--
-- 3) Files: t.txt should be gone from metadata.
-- SELECT id, original_file_name, content_type, file_size, created_at
-- FROM birchat.company_files
-- WHERE company_id = '41768d00-320b-4b8e-82f7-47b8ee7dbeb1'
-- ORDER BY created_at DESC;
