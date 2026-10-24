package ru.medovik.hack2026.api.controller.admin

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.request.admin.CreateUserRequest
import ru.medovik.hack2026.api.dto.response.auth.CurrentUserResponse
import ru.medovik.hack2026.api.service.user.AccountProvisioningService
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUsersController(
    private val provisioning: AccountProvisioningService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateUserRequest
    ): CurrentUserResponse =
        provisioning.createByAdmin(request).let { account ->
            CurrentUserResponse(
                id = account.id,
                email = account.email,
                role = requireNotNull(account.role),
                createdAt = account.createdAt ?: OffsetDateTime.now(),
            )
        }
}
