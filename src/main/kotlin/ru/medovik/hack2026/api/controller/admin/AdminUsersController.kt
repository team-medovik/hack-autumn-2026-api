package ru.medovik.hack2026.api.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.request.admin.CreateUserRequest
import ru.medovik.hack2026.api.dto.response.admin.AdminUserResponse
import ru.medovik.hack2026.api.dto.response.admin.CreatedUserResponse
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.service.user.AccountProvisioningService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Пользователи", description = "Управление сотрудниками с ролью ADMIN")
@SecurityRequirement(name = "bearerAuth")
class AdminUsersController(
    private val provisioning: AccountProvisioningService,
) {

    @GetMapping
    @Operation(operationId = "listAdminUsers", summary = "Список сотрудников")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сотрудники получены",
            content = [Content(array = ArraySchema(schema = Schema(implementation = AdminUserResponse::class)))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Нужна роль ADMIN",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun list(): List<AdminUserResponse> = provisioning.listUsers().map { it.toResponse() }

    @PostMapping
    @Operation(
        operationId = "createAdminUser",
        summary = "Создать сотрудника",
        description = "Генерирует временный пароль. Он возвращается только в ответе на создание; " +
            "ответ запрещено кэшировать.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Сотрудник создан",
            content = [Content(schema = Schema(implementation = CreatedUserResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Некорректный логин или запрос",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Нужна роль ADMIN",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Логин уже занят",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun create(
        @RequestBody request: CreateUserRequest,
        authentication: Authentication,
    ): ResponseEntity<CreatedUserResponse> {
        val created = provisioning.createByAdmin(request = request, actorLogin = authentication.name)
        val account = created.account.toResponse()
        return ResponseEntity.status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(
                CreatedUserResponse(
                    id = account.id,
                    login = account.login,
                    role = account.role,
                    status = account.status,
                    createdAt = account.createdAt,
                    temporaryPassword = created.temporaryPassword,
                )
            )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteAdminUser",
        summary = "Удалить сотрудника",
        description = "Нельзя удалить себя, последнего активного администратора " +
            "и сотрудника, управляемого файлом окружения.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Сотрудник удалён"),
        ApiResponse(
            responseCode = "400",
            description = "Сотрудник управляется файлом окружения",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Нужна роль ADMIN",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Сотрудник не найден",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Удаление себя или последнего администратора запрещено",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun delete(
        @Parameter(description = "UUID сотрудника") @PathVariable id: UUID,
        authentication: Authentication,
    ) {
        provisioning.deleteByAdmin(id = id, actorLogin = authentication.name)
    }

    private fun UserAccounts.toResponse() = AdminUserResponse(
        id = id,
        login = login,
        role = requireNotNull(role),
        status = requireNotNull(status),
        createdAt = requireNotNull(createdAt),
    )
}
