package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain

import org.springframework.data.annotation.Id
import java.time.Instant

data class TilInnsending(
    @Id
    val id: String? = null,
    val reisetilskuddId: String,
    val nokkel: String,
    val grupperingsid: String,
    val fnr: String,
    val eksterntVarsel: Boolean,
    val oppgaveSendt: Instant,
    val doneSendt: Instant?,
)
