package ru.medovik.hack2026.api.service.ml

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest

@Repository
class MlAssessmentAudit(private val dsl: DSLContext, private val json: JsonMapper) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(snapshotId: String, base: RiskResult, final: RiskResult, batch: MlPredictionBatch, fallback: String?) {
        val hash = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(base.settings))
            .joinToString("") { "%02x".format(it) }
        val byId = final.services.associateBy { it.serviceId }
        base.services.forEach { service ->
            val result = byId.getValue(service.serviceId)
            val probability = batch.outcomes[service.serviceId]?.probability
            val delta = if (result.score != null && service.score != null) result.score - service.score else 0.0
            dsl.execute("""INSERT INTO ml_assessment_audit(snapshot_id,prediction_at,service_id,settings_hash,model_version,
                algorithm_score,ml_probability,ml_adjustment,final_score,mode,fallback_reason)
                VALUES (?,CAST(? AS timestamptz),?,?,?,?,?,?,?,?,?)
                ON CONFLICT(snapshot_id,prediction_at,service_id,settings_hash,model_version) DO UPDATE SET
                algorithm_score=EXCLUDED.algorithm_score,ml_probability=EXCLUDED.ml_probability,
                ml_adjustment=EXCLUDED.ml_adjustment,final_score=EXCLUDED.final_score,mode=EXCLUDED.mode,
                fallback_reason=EXCLUDED.fallback_reason,assessed_at=CURRENT_TIMESTAMP""",
                snapshotId, base.evaluationTime.toString(), service.serviceId, hash, batch.modelVersion ?: "",
                service.score, probability, delta, result.score, if (probability != null) "HYBRID" else "ALGORITHM",
                if (probability != null) null else batch.outcomes[service.serviceId]?.reason ?: fallback ?: "NOT_APPLICABLE")
        }
    }
}
