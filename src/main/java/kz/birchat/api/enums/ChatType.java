package kz.birchat.api.enums;

/**
 * GENERAL — общий чат компании, в нём все активные сотрудники.
 * GROUP — групповой чат с явным списком участников, создаёт только директор.
 * DIRECT — личный чат двух сотрудников; директор чужие личные чаты не видит.
 */
public enum ChatType {
    GENERAL,
    GROUP,
    DIRECT
}
