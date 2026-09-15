package dev.factweek.economy

import dev.factweek.provenance.SourceReference
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class EconomyFact(
    val id: UUID,
    val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val evidenceLevel: EconomyEvidenceLevel,
    val occurredOn: LocalDate?,
    val referencePeriod: EconomyReferencePeriod?,
    val geography: EconomyGeography?,
    val measurement: EconomyMeasurement?,
    val entities: List<EconomyEntityReference>,
    val sources: List<SourceReference>,
)

enum class EconomyCategory {
    MONETARY_POLICY, PRICES_AND_INFLATION, LABOUR_MARKET, MACROECONOMIC_ACTIVITY,
    PUBLIC_FINANCE_AND_TAXATION, TRADE_AND_PRODUCTION, CORPORATE_AND_FINANCIAL_STABILITY,
}

enum class EconomyEventType {
    MONETARY_POLICY_DECIDED, INDICATOR_VALUE_REPORTED, FISCAL_MEASURE_ADOPTED,
    TARIFF_OR_SANCTION_CHANGED, CORPORATE_INSOLVENCY_FILED,
}

enum class EconomyEvidenceLevel { REPORTED, DOCUMENTED, PRIMARY_CONFIRMED, INDEPENDENTLY_CONFIRMED }

data class EconomyReferencePeriod(val from: LocalDate, val to: LocalDate, val granularity: EconomyReferencePeriodGranularity)
enum class EconomyReferencePeriodGranularity { MONTH, QUARTER, YEAR }

data class EconomyMeasurement(
    val value: BigDecimal,
    val unit: EconomyMeasurementUnit,
    val releaseStatus: EconomyReleaseStatus? = null,
    val seasonalAdjustment: EconomySeasonalAdjustment? = null,
    val valueBasis: EconomyValueBasis? = null,
)

enum class EconomyMeasurementUnit { PERCENT, PERCENTAGE_POINTS, COUNT }
enum class EconomyReleaseStatus { PROVISIONAL, FINAL, REVISED }
enum class EconomySeasonalAdjustment { ADJUSTED, NOT_ADJUSTED }
enum class EconomyValueBasis { NOMINAL, REAL }

data class EconomyGeography(val kind: EconomyGeographyKind, val name: String, val code: String? = null)
enum class EconomyGeographyKind { COUNTRY, REGION, GLOBAL }

data class EconomyEntityReference(val name: String, val type: EconomyEntityType)
enum class EconomyEntityType { ORGANIZATION, COMPANY, GOVERNMENT, CENTRAL_BANK, STATISTICAL_AUTHORITY, PLACE, INDICATOR }
