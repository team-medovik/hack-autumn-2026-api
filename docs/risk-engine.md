# Risk engine

## Specification and scope

The implementation follows the detailed [F1–F5 specification](source-risk-model-spec.md),
copied from the team's `risk-model-spec.md`. The original JSON fixture is preserved
byte-for-byte in `src/test/resources/risk/data.json`.

The detailed specification differs from `aeza-product-summary.md`:

- F2 is a shared account shortage ratio, applied to every live service due in the horizon,
  including services without automatic renewal.
- F4 is an additive amplifier for `prolong_wait` with insufficient funds.
- F5 gives `suspended` a configurable weight of 15 as a team hypothesis.
- There are no critical score overrides in this version.

These choices intentionally reproduce the detailed specification's formulas and reference
table. The score is an expert index, not a calibrated churn probability.

## Calculation

Live statuses: `active`, `suspended`, `prolong_wait`. `blocked` and `deleted` have null
scores and level `NONE`; zero is a valid score for a live service.

For a client, with a default horizon H = 7 days:

```text
days(s)   = (expiresAt - evaluationTime) / 24 hours
available = balance + (bonusState == unlocked ? bonusBalance : 0)
required  = sum(price of live services where days(s) <= H)
shortage  = required > 0 ? max(0, 1 - available / required) : 0
```

All money is stored as `Long` integer eurocents. `withdrawBalance` and
`totalReplenished` do not participate. Sums use checked arithmetic; unsupported
Long overflow raises an error instead of producing a negative or misleading amount.

| Code | Applies when | Strength | Default weight |
|---|---|---|---|
| F1 | active/suspended, autoProlong off, days <= H | 1 - max(0, days) / H | 50 |
| F2 | live and days <= H | account shortage | 40 |
| F3 | prolong_wait | 1 | 70 |
| F4 | prolong_wait | account shortage | 30 |
| F5 | suspended | 1 | 15 |

```text
contribution = strength * weight
rawScore     = sum(contributions)
score        = min(100, rawScore)
attentionScore(client) = max(score of live services), or null if none
loyaltyScore = 100 - score
```

No intermediate rounding is performed. Levels use the unrounded score:
LOW < 40, MEDIUM >= 40, HIGH >= 70. Each feature records applicability, strength,
weight, contribution, explanation and evidence, including zero contributions for inspection.
Customer explanations contain positive contributions, ordered by contribution descending
with stable service-id/feature-code tie breakers. They explain all live services; their sum
is not the client's maximum score. `rawScore` shows contributions before the 100-point cap.

`BalanceSimulator` also provides a chronological coverage illustration, ordered by expiry
and then service ID. Failed payments leave the balance unchanged. This illustrates a
single renewal per service; it does not drive the F2/F4 allocation and is not an actual
billing schedule. Hourly prices are never multiplied into monthly revenue.

`servicesAtRisk` counts live services whose score meets the medium threshold.
`revenueExposure` is the sum of their one-period renewal prices when their expiry lies
within the horizon (including overdue services). It is exposure, not expected loss, and
does not attempt to split hypothetical future cash and bonus payments.
`nearestRiskAt` is the earliest critical date among services meeting the threshold:
expiry for active/suspended, expiry + 5 days for prolong_wait. Past dates stay past.

## Invocation from application code

```kotlin
val result = RiskEngine().calculate(
    snapshot = snapshot,              // all accounts, services and transactions
    settings = RiskSettings(),        // immutable calculation configuration
    evaluationTime = snapshotTime,    // explicit java.time.Instant
)
```

The engine has no Spring context, database access, wall-clock dependency or mutable
calculation state. Supply an explicit time; for a live source, the application layer can
obtain it from `Clock`. Controllers, persistence, a Spring calculation service,
recalculation scheduling and settings storage are subsequent integration work.

Feature implementations live under `risk/feature`; weighting belongs to `RiskEngine`.
To add a feature, implement `RiskFeature`, register it in the engine constructor and
provide its weight in `RiskSettings.weights`. Strength must be finite and in [0,1].

Invalid amounts, date order, transaction status/time pairs, duplicate IDs, broken
references or invalid settings fail explicitly. Business inconsistencies that exist in
the supplied fixture are retained as warnings alongside the calculated score:
expired active/suspended, prolong_wait past the five-day grace period, premature
prolong_wait and future creation timestamps. Warnings contribute no risk points.
Transactions are accepted and validated, but never affect the score.

## Tests and complete fixture report

Use Java 25 as required by the existing project's toolchain:

```bash
bash gradlew test riskReport --console=plain
```

The report is written to `build/reports/risk/fixture-risk.json`; the JUnit HTML report
is at `build/reports/tests/test/index.html`. Both tasks run without starting Spring
or connecting to PostgreSQL. The fixture report uses `2026-10-19T15:00:00Z`, default
settings and all 30 services / 10 clients. It records the settings and model version.

Expected client scores (rounded here for display only):

| Client | Score | Level |
|---|---:|---|
| 1 | 49.8512 | MEDIUM |
| 2 | null | NONE |
| 3 | null | NONE |
| 4 | 15 | LOW |
| 5 | 70 | HIGH |
| 6 | 0.1488 | LOW |
| 7 | null | NONE |
| 8 | null | NONE |
| 9 | 65 | MEDIUM |
| 10 | 70 | HIGH |

The fixture has no funding shortage. Separate synthetic tests cover zero/partial
funds, account-wide competition, locked/unlocked bonuses, expired services, exact
boundaries, settings changes and mixed client portfolios. They leave the original
fixture unchanged.
