package ru.medovik.hack2026.api.service.user

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

@Service
class ExternalUsersEnvFile {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var initialized = false
    private var loadedPath: Path? = null
    private var lastModified: FileTime? = null

    @Synchronized
    fun processIfChanged(action: (List<ExternalUserDefinition>) -> Unit) {
        val path = USERS_FILE_PATHS.firstOrNull(Files::isRegularFile)
        if (path == null) {
            if (initialized && loadedPath == null) return
            action(emptyList())
            if (loadedPath != null) logger.info("External users env file removed: path={}", loadedPath)
            initialized = true
            loadedPath = null
            lastModified = null
            return
        }

        val modified = Files.getLastModifiedTime(path)
        if (path == loadedPath && modified == lastModified) return

        val users = parse(path)
        require(users.map { AccountLogin.normalize(it.login) }.distinct().size == users.size) {
            "В файле пользователей повторяется логин"
        }
        action(users)
        initialized = true
        loadedPath = path
        lastModified = modified
        logger.info("External users env file loaded: path={}, users={}", path, users.size)
    }

    private fun parse(path: Path): List<ExternalUserDefinition> {
        val values = Files.readAllLines(path)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "Некорректная строка в $path: $line" }
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
            }

        return values.keys
            .mapNotNull { USER_KEY.matchEntire(it)?.groupValues?.get(1)?.toInt() }
            .distinct()
            .sorted()
            .map { index ->
                val prefix = "USERS_${index}_"
                val login = values["${prefix}LOGIN"] ?: requireNotNull(values["${prefix}EMAIL"]) {
                    "Не задан ${prefix}LOGIN"
                }
                val password = values["${prefix}PASSWORD"]
                require(password == null || password.isNotBlank()) { "Пустой ${prefix}PASSWORD" }
                val oidcIssuer = values["${prefix}OIDC_ISSUER"]
                val oidcSubject = values["${prefix}OIDC_SUBJECT"]
                require((oidcIssuer == null) == (oidcSubject == null)) {
                    "${prefix}OIDC_ISSUER и ${prefix}OIDC_SUBJECT нужно указывать вместе"
                }
                require((oidcIssuer == null || oidcIssuer.isNotBlank()) &&
                    (oidcSubject == null || oidcSubject.isNotBlank())) {
                    "OIDC issuer и subject не могут быть пустыми"
                }
                require(password != null || oidcSubject != null || '@' in login) {
                    "Для SSO без пароля укажите OIDC issuer + subject либо email как login"
                }
                ExternalUserDefinition(
                    login = login,
                    password = password,
                    role = values["${prefix}ROLE"]?.uppercase()?.let(AccountRole::valueOf)
                        ?: AccountRole.EMPLOYEE,
                    status = values["${prefix}STATUS"]?.uppercase()?.let(AccountStatus::valueOf)
                        ?: AccountStatus.ACTIVE,
                    oidcIssuer = oidcIssuer,
                    oidcSubject = oidcSubject,
                )
            }
    }

    private companion object {
        val USER_KEY = Regex("USERS_(\\d+)_(LOGIN|EMAIL|PASSWORD|ROLE|STATUS|OIDC_ISSUER|OIDC_SUBJECT)")
        val USERS_FILE_PATHS = listOf(
            Path.of("/config/users.env"),
            Path.of("src/main/resources/users.env"),
        )
    }
}
