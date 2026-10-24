package ru.medovik.hack2026.api.admin

import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import ru.medovik.hack2026.api.dto.request.admin.CreateUserRequest
import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountSource
import ru.medovik.hack2026.api.enumerable.account.AccountStatus
import ru.medovik.hack2026.api.exception.UserAlreadyExistsException
import ru.medovik.hack2026.api.exception.UserDeletionForbiddenException
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.jooq.tables.references.USER_ACCOUNTS
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import ru.medovik.hack2026.api.service.auth.JwtService
import ru.medovik.hack2026.api.service.user.AccountProvisioningService
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:admin_users;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/init-tables.sql",
        "management.server.port=0",
    ],
)
class AdminUsersIntegrationTest {
    @Autowired private lateinit var dsl: DSLContext
    @Autowired private lateinit var repository: UserAccountRepository
    @Autowired private lateinit var provisioning: AccountProvisioningService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jwtService: JwtService
    @Autowired private lateinit var jsonMapper: JsonMapper
    @Value("\${local.server.port}") private var port: Int = 0

    private val http = HttpClient.newHttpClient()
    private lateinit var envAdmin: UserAccounts

    @BeforeEach
    fun seedAdmin() {
        dsl.deleteFrom(USER_ACCOUNTS).execute()
        envAdmin = insertAccount("env-admin@example.com", AccountRole.ADMIN, AccountSource.ENV_FILE)
    }

    @Test
    fun `admin creates user with a one-time password and lists users without credentials`() {
        val created = request("POST", "{\"email\":\"  Employee@Example.com  \"}")
        assertEquals(201, created.statusCode())
        assertTrue(created.headers().firstValue("Cache-Control").orElse("").contains("no-store"))

        val result = jsonMapper.readTree(created.body())
        val password = result["temporaryPassword"].asText()
        assertTrue(password.length >= 32)
        val account = assertNotNull(repository.findByEmail("employee@example.com"))
        assertEquals("ADMIN_PANEL", account.provisioningSource)
        assertNotEquals(password, account.passwordHash)
        assertTrue(passwordEncoder.matches(password, account.passwordHash))

        val listed = request("GET")
        assertEquals(200, listed.statusCode())
        assertTrue(listed.body().contains("employee@example.com"))
        assertFalse(listed.body().contains("temporaryPassword"))
        assertFalse(listed.body().contains(password))
        assertFalse(listed.body().contains("passwordHash"))

        val duplicate = request("POST", "{\"email\":\"EMPLOYEE@example.com\"}")
        assertEquals(409, duplicate.statusCode())
        assertEquals("USER_ALREADY_EXISTS", jsonMapper.readTree(duplicate.body())["error"].asText())
    }

    @Test
    fun `only admin can use API and cannot delete self or env user`() {
        val unauthenticated = request("GET", authenticated = false)
        assertEquals(401, unauthenticated.statusCode())

        val employee = insertAccount("employee@example.com", AccountRole.EMPLOYEE)
        val forbidden = request("GET", actor = employee)
        assertEquals(403, forbidden.statusCode())

        val selfDeletion = request("DELETE", path = "/${envAdmin.id}")
        assertEquals(409, selfDeletion.statusCode())
        assertEquals("SELF_DELETION_FORBIDDEN", jsonMapper.readTree(selfDeletion.body())["error"].asText())

        val anotherEnvAdmin = insertAccount("env-two@example.com", AccountRole.ADMIN, AccountSource.ENV_FILE)
        val envDeletion = request("DELETE", path = "/${anotherEnvAdmin.id}")
        assertEquals(400, envDeletion.statusCode())
        assertNotNull(repository.findById(anotherEnvAdmin.id))
    }

    @Test
    fun `admin deletes database user and missing user gives 404`() {
        val employee = insertAccount("employee@example.com", AccountRole.EMPLOYEE)
        assertEquals(204, request("DELETE", path = "/${employee.id}").statusCode())
        assertEquals(null, repository.findById(employee.id))
        assertEquals(404, request("DELETE", path = "/${employee.id}").statusCode())
    }

    @Test
    fun `service protects final active admin and permits deleting another admin`() {
        dsl.deleteFrom(USER_ACCOUNTS).execute()
        val onlyAdmin = insertAccount("only-admin@example.com", AccountRole.ADMIN)
        val lastAdminError = assertFailsWith<UserDeletionForbiddenException> {
            provisioning.deleteByAdmin(onlyAdmin.id, "operator@example.com")
        }
        assertEquals("LAST_ACTIVE_ADMIN", lastAdminError.code)
        assertNotNull(repository.findById(onlyAdmin.id))

        val otherAdmin = insertAccount("other-admin@example.com", AccountRole.ADMIN)
        provisioning.deleteByAdmin(otherAdmin.id, onlyAdmin.email)
        assertEquals(null, repository.findById(otherAdmin.id))
        assertNotNull(repository.findById(onlyAdmin.id))

        val selfError = assertFailsWith<UserDeletionForbiddenException> {
            provisioning.deleteByAdmin(onlyAdmin.id, onlyAdmin.email)
        }
        assertEquals("SELF_DELETION_FORBIDDEN", selfError.code)
    }

    @Test
    fun `service returns a different temporary password for each new account`() {
        val first = provisioning.createByAdmin(CreateUserRequest("first@example.com", AccountRole.EMPLOYEE, AccountStatus.ACTIVE))
        val second = provisioning.createByAdmin(CreateUserRequest("second@example.com", AccountRole.EMPLOYEE, AccountStatus.ACTIVE))
        assertNotEquals(first.temporaryPassword, second.temporaryPassword)
        assertTrue(passwordEncoder.matches(first.temporaryPassword, repository.findById(first.account.id)?.passwordHash))
        assertTrue(passwordEncoder.matches(second.temporaryPassword, repository.findById(second.account.id)?.passwordHash))
    }

    @Test
    fun `concurrent creation of the same email returns one duplicate error`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val requests = (1..2).map {
                pool.submit<Result<*>> {
                    start.await()
                    runCatching { provisioning.createByAdmin(CreateUserRequest("same@example.com")) }
                }
            }
            start.countDown()
            val results = requests.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is UserAlreadyExistsException })
            assertNotNull(repository.findByEmail("same@example.com"))
        } finally {
            pool.shutdownNow()
        }
    }

    private fun insertAccount(
        email: String,
        role: AccountRole,
        source: AccountSource = AccountSource.ADMIN_PANEL,
    ): UserAccounts = repository.insert(UserAccounts(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = requireNotNull(passwordEncoder.encode("test-password")),
        role = role.name,
        status = AccountStatus.ACTIVE.name,
        provisioningSource = source.name,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    ))

    private fun request(
        method: String,
        body: String = "",
        path: String = "",
        actor: UserAccounts = envAdmin,
        authenticated: Boolean = true,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/admin/users$path"))
        if (authenticated) builder.header("Authorization", "Bearer ${jwtService.createAccessToken(actor)}")
        if (body.isNotEmpty()) builder.header("Content-Type", "application/json")
        builder.method(method, if (body.isEmpty()) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
