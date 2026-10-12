package dev.factweek.briefing

import dev.factweek.economy.EconomyMeasurement
import dev.factweek.provenance.SourceReference
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class WeeklyMarkdownRenderer {
    fun render(
        from: LocalDate,
        to: LocalDate,
        generatedAt: Instant,
        maximumPerSection: Int,
        economy: List<BriefingEconomyFact>,
        technology: List<BriefingTechnologyFact>,
    ): String = buildString {
        appendLine("# Wochenrückblick: Wirtschaft und Technologie")
        appendLine()
        appendLine("**Berichtszeitraum:** ${date(from)} bis ${date(to)} (einschließlich)")
        appendLine("**Erzeugt:** $generatedAt (UTC)")
        appendLine("**Ausgegebene Facts:** Wirtschaft ${economy.size}, Technologie ${technology.size}; Limit je Ressort: $maximumPerSection. Keine vollständige Nachrichtenabdeckung.")
        appendLine()
        appendLine("## Wirtschaft")
        appendLine()
        if (economy.isEmpty()) appendLine("Keine veröffentlichten Facts für dieses Zeitfenster gefunden.")
        economy.forEachIndexed { index, fact ->
            appendLine("### Fact ${index + 1}")
            appendLine()
            appendLine("**Aussage:** ${escape(fact.statement)}")
            appendLine()
            appendLine("- **Kategorie:** ${label(fact.category)}")
            appendLine("- **Ereignistyp:** ${label(fact.eventType)}")
            appendLine("- **Evidenzstufe:** ${label(fact.evidenceLevel)}")
            fact.occurredOn?.let { appendLine("- **Ereignisdatum:** ${date(it)}") }
            fact.referencePeriod?.let {
                appendLine("- **Bezugszeitraum:** ${date(it.from)} bis ${date(it.to)} (${label(it.granularity)})")
            }
            fact.geography?.let {
                val code = it.code?.let { code -> ", ${escape(code)}" }.orEmpty()
                appendLine("- **Geografie:** ${escape(it.name)} (${label(it.kind)}$code)")
            }
            fact.measurement?.let { appendMeasurement(it) }
            fact.entities.sortedWith(compareBy({ it.type.name }, { it.name })).forEach {
                appendLine("- **Entität:** ${escape(it.name)} (${label(it.type)})")
            }
            appendSources(fact.sources)
            appendLine()
        }
        appendLine()
        appendLine("## Technologie")
        appendLine()
        if (technology.isEmpty()) appendLine("Keine veröffentlichten Facts für dieses Zeitfenster gefunden.")
        technology.forEachIndexed { index, fact ->
            appendLine("### Fact ${index + 1}")
            appendLine()
            appendLine("**Aussage:** ${escape(fact.statement)}")
            appendLine()
            appendLine("- **Kategorie:** ${label(fact.category)}")
            appendLine("- **Ereignistyp:** ${label(fact.eventType)}")
            appendLine("- **Entwicklungsstand:** ${label(fact.readiness)}")
            appendLine("- **Evidenzstufe:** ${label(fact.evidenceLevel)}")
            fact.occurredOn?.let { appendLine("- **Ereignisdatum:** ${date(it)}") }
            fact.entities.sortedWith(compareBy({ it.type.name }, { it.name })).forEach {
                appendLine("- **Entität:** ${escape(it.name)} (${label(it.type)})")
            }
            appendSources(fact.sources)
            appendLine()
        }
        appendLine()
        appendLine("## Zeit und Evidenz")
        appendLine()
        appendLine("Das Fenster bezieht sich auf die Referenzdaten der Facts: Vorrang hat das Ereignisdatum; fehlt es, zählt die früheste Quellenpublikation als UTC-Kalendertag. Ein Wirtschafts-Bezugszeitraum kann außerhalb der Berichtswoche liegen.")
        appendLine()
        appendLine("Der Export zeigt den derzeit veröffentlichten Fact-Bestand für dieses Referenzfenster, keinen historischen Datenbank-Snapshot. Später angenommene Facts können enthalten sein. „Anhand einer Primärquelle geprüft“ (PRIMARY_CONFIRMED) bedeutet keine unabhängige Replikation.")
        appendLine()
        appendLine("## Nachweis")
        appendLine()
        appendLine("| Ressort | Fact-ID | Referenzdatum | Referenzdatumsbasis |")
        appendLine("| --- | --- | --- | --- |")
        economy.forEach { appendLine("| Wirtschaft | ${it.id} | ${it.referenceDate} | ${label(it.referenceDateBasis)} |") }
        technology.forEach { appendLine("| Technologie | ${it.id} | ${it.referenceDate} | ${label(it.referenceDateBasis)} |") }
    }

    private fun StringBuilder.appendMeasurement(measurement: EconomyMeasurement) {
        appendLine("- **Messwert:** ${measurement.value.toPlainString()} ${label(measurement.unit)}")
        measurement.releaseStatus?.let { appendLine("- **Datenstand:** ${label(it)}") }
        measurement.seasonalAdjustment?.let { appendLine("- **Saisonbereinigung:** ${label(it)}") }
        measurement.valueBasis?.let { appendLine("- **Wertbasis:** ${label(it)}") }
    }

    private fun StringBuilder.appendSources(sources: List<SourceReference>) {
        sources.sortedWith(compareBy({ it.publisher }, { it.url }, { it.publishedAt }, { it.sourceType.name })).forEach { source ->
            val name = escape(source.publisher)
            val url = safeUrl(source.url)
            val publisher = if (url == null) "$name (Original-URL, nicht verlinkt: ${code(source.url)})" else "[$name](<$url>)"
            val published = source.publishedAt?.let { ", veröffentlicht am ${date(it.atZone(ZoneOffset.UTC).toLocalDate())}" }.orEmpty()
            appendLine("- **Quelle:** $publisher (${label(source.sourceType)}$published)")
        }
    }

    private fun safeUrl(value: String): String? = try {
        if (value.any { it.isWhitespace() || it.isISOControl() || it == '<' || it == '>' || it == '\\' }) null
        else URI(value).takeIf {
            it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !it.host.isNullOrBlank() && it.userInfo == null
        }?.toASCIIString()
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun code(value: String): String {
        val clean = value.replace(Regex("[\\p{Cntrl}\\u2028\\u2029]"), " ")
        val ticks = "`".repeat((Regex("`+").findAll(clean).maxOfOrNull { it.value.length } ?: 0) + 1)
        return "$ticks $clean $ticks"
    }

    private fun escape(value: String): String = value.split(Regex("\\R"))
        .joinToString("  \n") { line -> buildString {
            line.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~', '=' -> append('\\').append(char)
                    else -> if (!char.isISOControl()) append(char)
                }
            }
        } }

    private fun date(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))

    private fun label(value: Enum<*>): String = when (value.name) {
        "CORPORATE_AND_FINANCIAL_STABILITY" -> "Unternehmen und Finanzstabilität"
        "LABOUR_MARKET" -> "Arbeitsmarkt"
        "MACROECONOMIC_ACTIVITY" -> "Gesamtwirtschaftliche Entwicklung"
        "MONETARY_POLICY" -> "Geldpolitik"
        "PRICES_AND_INFLATION" -> "Preise und Inflation"
        "PUBLIC_FINANCE_AND_TAXATION" -> "Öffentliche Finanzen und Steuern"
        "TRADE_AND_PRODUCTION" -> "Handel und Produktion"
        "AI_AND_SOFTWARE" -> "KI und Software"
        "ENERGY_AND_CLIMATE" -> "Energie und Klima"
        "MATERIALS_AND_MANUFACTURING" -> "Materialien und Fertigung"
        "MEDICINE_AND_BIOTECH" -> "Medizin und Biotechnologie"
        "ROBOTICS_AND_AUTOMATION" -> "Robotik und Automatisierung"
        "MONETARY_POLICY_DECIDED" -> "Geldpolitische Entscheidung"
        "INDICATOR_VALUE_REPORTED" -> "Indikatorwert veröffentlicht"
        "FISCAL_MEASURE_ADOPTED" -> "Fiskalmaßnahme beschlossen"
        "TARIFF_OR_SANCTION_CHANGED" -> "Zoll oder Sanktion geändert"
        "CORPORATE_INSOLVENCY_FILED" -> "Unternehmensinsolvenz angemeldet"
        "RESEARCH_RESULT_PUBLISHED" -> "Forschungsergebnis veröffentlicht"
        "PROTOTYPE_DEMONSTRATED" -> "Prototyp vorgeführt"
        "CLINICAL_RESULT_PUBLISHED" -> "Klinisches Ergebnis veröffentlicht"
        "TECHNOLOGY_DEPLOYED" -> "Technologie eingesetzt"
        "REGULATORY_APPROVAL_GRANTED" -> "Behördliche Zulassung erteilt"
        "PERFORMANCE_RECORD_VERIFIED" -> "Leistungsrekord geprüft"
        "OPEN_SOURCE_RELEASED" -> "Quelloffene Software veröffentlicht"
        "PRODUCTION_STARTED" -> "Produktion aufgenommen"
        "REPORTED" -> "Berichtet"
        "DOCUMENTED" -> "Dokumentiert"
        "PRIMARY_CONFIRMED" -> "Anhand einer Primärquelle geprüft"
        "INDEPENDENTLY_CONFIRMED" -> "Unabhängig bestätigt"
        "PROVEN_IN_USE" -> "Im Einsatz belegt"
        "CONCEPT" -> "Konzept"
        "LAB_RESULT" -> "Laborergebnis"
        "PROTOTYPE" -> "Prototyp"
        "CONTROLLED_PILOT" -> "Kontrollierter Pilotversuch"
        "REAL_WORLD_PILOT" -> "Pilotversuch im Einsatzfeld"
        "LIMITED_DEPLOYMENT" -> "Begrenzter Einsatz"
        "PRODUCTION_USE" -> "Produktiver Einsatz"
        "MONTH" -> "Monat"
        "QUARTER" -> "Quartal"
        "YEAR" -> "Jahr"
        "PERCENT" -> "%"
        "PERCENTAGE_POINTS" -> "Prozentpunkte"
        "COUNT" -> "Anzahl"
        "PROVISIONAL" -> "Vorläufig"
        "FINAL" -> "Endgültig"
        "REVISED" -> "Revidiert"
        "ADJUSTED" -> "Saisonbereinigt"
        "NOT_ADJUSTED" -> "Nicht saisonbereinigt"
        "NOMINAL" -> "Nominal"
        "REAL" -> "Real"
        "COUNTRY" -> "Land"
        "REGION" -> "Region"
        "GLOBAL" -> "Weltweit"
        "ORGANIZATION" -> "Organisation"
        "COMPANY" -> "Unternehmen"
        "GOVERNMENT" -> "Regierung"
        "CENTRAL_BANK" -> "Zentralbank"
        "STATISTICAL_AUTHORITY" -> "Statistikbehörde"
        "PLACE" -> "Ort"
        "INDICATOR" -> "Indikator"
        "TECHNOLOGY" -> "Technologie"
        "PRODUCT" -> "Produkt"
        "RESEARCH_GROUP" -> "Forschungsgruppe"
        "NEWS_REPORT" -> "Nachrichtenbericht"
        "PRIMARY_DOCUMENT" -> "Primärdokument"
        "PAPER" -> "Fachveröffentlichung"
        "DATASET" -> "Datensatz"
        "REPOSITORY" -> "Repository"
        "REGULATOR" -> "Regulierungsbehörde"
        "OCCURRED_ON" -> "Ereignisdatum"
        "SOURCE_PUBLISHED_AT" -> "Quellenpublikation (UTC)"
        else -> error("Missing German label for ${value.javaClass.simpleName}.${value.name}")
    }
}
