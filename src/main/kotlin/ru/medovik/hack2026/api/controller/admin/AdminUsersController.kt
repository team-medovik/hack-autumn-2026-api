package ru.medovik.hack2026.api.controller.admin

import org.springframework.http.HttpStatus
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
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.service.user.AccountProvisioningService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUsersController(
    private val provisioning: AccountProvisioningService,
) {

    @GetMapping
    fun list(): List<AdminUserResponse> = provisioning.listUsers().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateUserRequest
    ): AdminUserResponse = provisioning.createByAdmin(request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        provisioning.deleteByAdmin(id)
    }

    private fun UserAccounts.toResponse() = AdminUserResponse(
        id = id,
        email = email,
        role = requireNotNull(role),
        status = requireNotNull(status),
        createdAt = requireNotNull(createdAt),
    )
}
