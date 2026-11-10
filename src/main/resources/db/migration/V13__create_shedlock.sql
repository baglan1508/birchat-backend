-- Блокировки фоновых задач (ShedLock): задача выполняется только на одном экземпляре приложения,
-- даже если серверов несколько.
CREATE TABLE IF NOT EXISTS birchat.shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,

    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
