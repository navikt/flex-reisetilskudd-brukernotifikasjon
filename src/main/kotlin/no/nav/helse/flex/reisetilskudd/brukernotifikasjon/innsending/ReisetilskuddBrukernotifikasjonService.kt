package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.innsending

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveBuilder
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.*
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.log
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository.TilInnsendingRepository
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
    val oppgaveKafkaTemplate: KafkaTemplate<Nokkel, Oppgave>,
    val doneKafkaTemplate: KafkaTemplate<Nokkel, Done>,
    val tilUtfyllingRepository: TilUtfyllingRepository,
    val tilInnsendingRepository: TilInnsendingRepository,
    @Value("\${serviceuser.username}") val serviceuserUsername: String,
    @Value("\${dittsykefravaer.url}") val dittSykefravaerUrl: String
) {

    private val log = log()

    fun behandleReisetilskuddSoknad(soknadString: String) {
        val reisetilskudd = soknadString.tilReisetilskudd()

        return when (reisetilskudd.status) {
            ReisetilskuddStatus.FREMTIDIG -> Unit
            ReisetilskuddStatus.ÅPEN -> handterApen(reisetilskudd)
            ReisetilskuddStatus.SENDBAR -> handterSendbar(reisetilskudd)
            ReisetilskuddStatus.SENDT -> handterAvbruttOgSendt(reisetilskudd)
            ReisetilskuddStatus.AVBRUTT -> handterAvbruttOgSendt(reisetilskudd)
        }
    }

    private fun handterSendbar(reisetilskudd: Reisetilskudd) {
        tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(reisetilskuddId = reisetilskudd.reisetilskuddId).sendDone()

        if (tilInnsendingRepository.existsByReisetilskuddId(reisetilskudd.reisetilskuddId)) {
            log.info("Mottok duplikat reisetilskuddsøknad med id ${reisetilskudd.reisetilskuddId}")
            return
        }
        val nokkel = NokkelBuilder()
            .withEventId(UUID.randomUUID().toString())
            .withSystembruker(serviceuserUsername)
            .build()

        val oppgave = OppgaveBuilder()
            .withGrupperingsId(reisetilskudd.sykmeldingId)
            .withFodselsnummer(reisetilskudd.fnr)
            .withLink(URL(dittSykefravaerUrl))
            .withSikkerhetsnivaa(4)
            .withTekst("Nå kan du sende inn søknaden om reisetilskudd")
            .withEksternVarsling(true)
            .withTidspunkt(LocalDateTime.now())
            .build()

        oppgaveKafkaTemplate.sendDefault(nokkel, oppgave).get()

        tilInnsendingRepository.save(
            TilInnsending(
                reisetilskuddId = reisetilskudd.reisetilskuddId,
                grupperingsid = oppgave.getGrupperingsId(),
                fnr = oppgave.getFodselsnummer(),
                eksterntVarsel = oppgave.getEksternVarsling(),
                nokkel = nokkel.getEventId(),
                doneSendt = null,
                oppgaveSendt = Instant.now(),
            )
        )
        log.info("Opprettet oppgave på reisetilskuddsøknad ${reisetilskudd.reisetilskuddId} med status ${reisetilskudd.status}")
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
            .withLink(URL(dittSykefravaerUrl))
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
        log.info("Opprettet beskjed på reisetilskuddsøknad ${reisetilskudd.reisetilskuddId} med status ${reisetilskudd.status}")
    }

    private fun handterAvbruttOgSendt(reisetilskudd: Reisetilskudd) {
        tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(reisetilskuddId = reisetilskudd.reisetilskuddId).sendDone()
        tilInnsendingRepository.findTilInnsendingByReisetilskuddId(reisetilskuddId = reisetilskudd.reisetilskuddId).sendDone()
    }

    private fun TilUtfylling?.sendDone() {
        this?.let {
            if (it.doneSendt == null) {
                val nokkel = NokkelBuilder()
                    .withEventId(it.nokkel)
                    .withSystembruker(serviceuserUsername)
                    .build()

                val done = DoneBuilder()
                    .withGrupperingsId(it.grupperingsid)
                    .withFodselsnummer(it.fnr)
                    .withTidspunkt(LocalDateTime.now())
                    .build()

                doneKafkaTemplate.sendDefault(nokkel, done)

                tilUtfyllingRepository.save(it.copy(doneSendt = Instant.now()))
            }
        }
    }

    private fun TilInnsending?.sendDone() {
        this?.let {
            if (it.doneSendt == null) {
                val nokkel = NokkelBuilder()
                    .withEventId(it.nokkel)
                    .withSystembruker(serviceuserUsername)
                    .build()

                val done = DoneBuilder()
                    .withGrupperingsId(it.grupperingsid)
                    .withFodselsnummer(it.fnr)
                    .withTidspunkt(LocalDateTime.now())
                    .build()

                doneKafkaTemplate.sendDefault(nokkel, done)

                tilInnsendingRepository.save(it.copy(doneSendt = Instant.now()))
            }
        }
    }
}
