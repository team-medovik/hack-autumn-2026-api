package ru.medovik.hack2026.api.service.snapshot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.exception.InvalidSnapshotException
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.service.risk.RiskEngine
import ru.medovik.hack2026.api.service.settings.CalculationDateService
import ru.medovik.hack2026.api.service.settings.ModelSettingsService
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class SnapshotService(
    private val repository: BillingSnapshotRepository,
    private val dates: CalculationDateService,
    private val modelSettings: ModelSettingsService,
    private val jsonMapper: JsonMapper,
) {
    private val riskEngine = RiskEngine()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): List<SnapshotRecord> = repository.listRecords().also { records ->
        logger.debug("Snapshot history loaded: count={}", records.size)
    }

    @Transactional(readOnly = true)
    fun file(id: String): Pair<String, ByteArray> = repository.file(id)
        ?.also { (_, bytes) -> logger.debug("Snapshot file loaded: snapshotId={}, bytes={}", id, bytes.size) }
        ?: throw NoSuchElementException("Снимок не найден")

    @Transactional
    fun upload(fileName: String, rawFile: ByteArray, uploadedBy: String): SnapshotRecord {
        // Never log file contents: the source contains customer identifiers and billing data.
        logger.info("Snapshot upload started: fileName={}, bytes={}, uploadedBy={}", fileName, rawFile.size, uploadedBy)
        if (rawFile.size > MAX_FILE_BYTES) throw SnapshotTooLargeException()
        require(fileName.isNotBlank() && fileName.length <= 255 &&
            !fileName.contains('/') && !fileName.contains('\\') &&
            !fileName.contains('\r') && !fileName.contains('\n')) { "Некорректное имя файла" }
        val source = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(rawFile))
                .toString()
        } catch (_: Exception) {
            throw InvalidSnapshotException("Файл должен быть JSON в кодировке UTF-8")
        }
        val root = try {
            @Suppress("UNCHECKED_CAST")
            jsonMapper.readValue(source, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            throw InvalidSnapshotException("Не удалось извлечь данные из JSON: файл не является корректным JSON")
        }
        // SourceSnapshot is oneOf(RiskSnapshot, NestedRiskSnapshot). A payload matching
        // both branches must be rejected rather than silently choosing one.
        val canonicalForm = root["accounts"] is List<*> && root["services"] is List<*>
        val nestedForm = (root["clients"] as? List<*>)?.isNotEmpty() == true
        if (canonicalForm && nestedForm) {
            throw InvalidSnapshotException("Снимок не может одновременно содержать accounts/services и clients")
        }
        val canonical = try {
            when {
                root["accounts"] is List<*> && root["services"] is List<*> -> source
                root["clients"] is List<*> -> {
                    val clients = (root["clients"] as List<*>).map { it as Map<*, *> }
                    val accounts = clients.map { it.filterKeys { key -> key != "services" } }
                    val services = clients.flatMap { client ->
                        (client["services"] as List<*>).map { item ->
                            val service = item as Map<*, *>
                            if (service.containsKey("ownerId")) service else service + ("ownerId" to client["id"])
                        }
                    }
                    jsonMapper.writeValueAsString(mapOf(
                        "accounts" to accounts,
                        "services" to services,
                        "transactions" to (root["transactions"] ?: emptyList<Any>()),
                    ))
                }
                else -> throw InvalidSnapshotException("Ожидаются массивы accounts и services или clients с services")
            }
        } catch (exception: InvalidSnapshotException) {
            throw exception
        } catch (_: Exception) {
            throw InvalidSnapshotException("Некорректная структура клиентов и услуг")
        }
        val snapshot = try {
            val parsed = jsonMapper.readValue(canonical, RiskSnapshot::class.java)
            require(parsed.accounts.isNotEmpty()) { "Снимок не содержит клиентов" }
            riskEngine.calculate(
                snapshot = parsed,
                settings = modelSettings.riskSettings(),
                evaluationTime = dates.evaluationTime(),
            )
            parsed
        } catch (exception: Exception) {
            throw InvalidSnapshotException("Не удалось извлечь данные из JSON: ${exception.message ?: "неверный формат"}")
        }
        logger.info(
            "Snapshot validated and risk calculation completed: fileName={}, clients={}, services={}, transactions={}",
            fileName, snapshot.accounts.size, snapshot.services.size, snapshot.transactions.size,
        )
        val record = SnapshotRecord(
            id = "snapshot-${UUID.randomUUID()}",
            fileName = fileName,
            uploadedAt = OffsetDateTime.now(ZoneOffset.UTC),
            uploadedBy = uploadedBy,
            clients = snapshot.accounts.size,
            services = snapshot.services.size,
        )
        logger.info("Snapshot database insertion started: snapshotId={}, bytes={}", record.id, rawFile.size)
        repository.insert(record = record, payload = canonical, rawFile = rawFile)
        logger.info("Snapshot database insertion executed: snapshotId={}; awaiting transaction commit", record.id)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                logger.info(
                    "Snapshot uploaded: snapshotId={}, uploadedBy={}, clients={}, services={}, bytes={}",
                    record.id,
                    uploadedBy,
                    record.clients,
                    record.services,
                    rawFile.size,
                )
            }
        })
        return record
    }

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
