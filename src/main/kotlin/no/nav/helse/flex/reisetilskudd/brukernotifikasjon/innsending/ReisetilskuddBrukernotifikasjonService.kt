package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.innsending

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.TilUtfylling
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.tilReisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.log
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository.TilUtfyllingRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Component
class ReisetilskuddBrukernotifikasjonService(
    val beskjedKafkaTemplate: KafkaTemplate<Nokkel, Beskjed>,
    val doneKafkaTemplate: KafkaTemplate<Nokkel, Done>,
    val tilUtfyllingRepository: TilUtfyllingRepository,
    @Value("\${serviceuser.username}") val serviceuserUsername: String,
    @Value("\${flex.reisetilskudd.frontend.url}") val flexReisetilskuddFrontendUrl: String
) {

    private val log = log()

    fun behandleReisetilskuddSoknad(soknadString: String) {
        val reisetilskudd = soknadString.tilReisetilskudd()

        return when (reisetilskudd.status) {
            ReisetilskuddStatus.FREMTIDIG -> Unit
            ReisetilskuddStatus.ÅPEN -> handterApen(reisetilskudd)
            ReisetilskuddStatus.SENDBAR -> TODO()
            ReisetilskuddStatus.SENDT -> handterSendt(reisetilskudd)
            ReisetilskuddStatus.AVBRUTT -> handterAvbrutt(reisetilskudd)
        }
    }

    private fun handterSendt(reisetilskudd: Reisetilskudd) {
        reisetilskudd.toString()
    }

    private fun handterApen(reisetilskudd: Reisetilskudd) {
        if (tilUtfyllingRepository.existsByReisetilskuddId(reisetilskudd.reisetilskuddId)) {
            log.info("Mottok duplikat reisetilskuddsøknad med id ${reisetilskudd.reisetilskuddId}")
            return
        }
        val nokkel = NokkelBuilder()
            .withEventId(UUID.randomUUID().toString())
            .withSystembruker(serviceuserUsername)
            .build()

        val synligFremTil = reisetilskudd.tom.plusDays(1).atStartOfDay()
        val beskjed = BeskjedBuilder()
            .withGrupperingsId(reisetilskudd.sykmeldingId)
            .withFodselsnummer(reisetilskudd.fnr)
            .withLink(URL(flexReisetilskuddFrontendUrl)) // TODO hvilken side skal vi til?
            .withSikkerhetsnivaa(4)
            .withSynligFremTil(synligFremTil)
            .withTekst("Du har en søknad om reisetilskudd til utfylling")
            .withEksternVarsling(false)
            .withTidspunkt(LocalDateTime.now())
            .build()

        beskjedKafkaTemplate.sendDefault(nokkel, beskjed).get()

        tilUtfyllingRepository.save(
            TilUtfylling(
                reisetilskuddId = reisetilskudd.reisetilskuddId,
                grupperingsid = beskjed.getGrupperingsId(),
                fnr = beskjed.getFodselsnummer(),
                eksterntVarsel = beskjed.getEksternVarsling(),
                nokkel = nokkel.getEventId(),
                doneSendt = null,
                beskjedSendt = Instant.now(),
                synligFremTil = synligFremTil.atOffset(ZoneOffset.UTC).toInstant(),
            )
        )
        log.info("Mottok reisetilskuddsøknad ${reisetilskudd.reisetilskuddId} med status ${reisetilskudd.status}")
    }

    private fun handterAvbrutt(reisetilskudd: Reisetilskudd) {
        tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(reisetilskuddId = reisetilskudd.reisetilskuddId)?.let {
            if (it.doneSendt == null) {
                val nokkel = NokkelBuilder()
                    .withEventId(it.nokkel)
                    .withSystembruker(serviceuserUsername)
                    .build()

                val done = DoneBuilder()
                    .withGrupperingsId(reisetilskudd.sykmeldingId)
                    .withFodselsnummer(reisetilskudd.fnr)
                    .withTidspunkt(LocalDateTime.now())
                    .build()

                doneKafkaTemplate.sendDefault(nokkel, done)
            }
        }
    }
}
