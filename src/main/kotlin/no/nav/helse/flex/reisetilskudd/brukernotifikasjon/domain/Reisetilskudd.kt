package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.objectMapper
import java.time.LocalDate

data class Reisetilskudd(
    val status: ReisetilskuddStatus,
    val reisetilskuddId: String,
    val fnr: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class ReisetilskuddStatus {
    FREMTIDIG, ÅPEN, SENDBAR, SENDT, AVBRUTT
}

fun String.tilReisetilskudd(): Reisetilskudd = objectMapper.readValue(this)
