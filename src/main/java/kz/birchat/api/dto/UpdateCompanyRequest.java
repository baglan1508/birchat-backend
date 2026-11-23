package kz.birchat.api.dto;

/**
 * Настройки компании (PUT /api/companies/{companyId}). Все поля необязательные:
 * меняется только то, что передано.
 *
 * aiEnabled — согласие компании на обработку сообщений и файлов сторонним AI.
 * membersCanCreateGroups — группы могут создавать все сотрудники (по умолчанию только директор).
 */
public record UpdateCompanyRequest(
        Boolean aiEnabled,
        Boolean membersCanCreateGroups
) {
}
