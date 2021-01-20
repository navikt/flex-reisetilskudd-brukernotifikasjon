package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.innsending

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.tilReisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.log
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.net.URL
import java.time.LocalDateTime
import java.util.*

@Component
class ReisetilskuddBrukernotifikasjonService(
    val beskjedKafkaTemplate: KafkaTemplate<Nokkel, Beskjed>,
    @Value("\${serviceuser.username}") val serviceuserUsername: String,
    @Value("\${flex.reisetilskudd.frontend.url}") val flexReisetilskuddFrontendUrl: String
) {

    private val log = log()

    fun behandleReisetilskuddSoknad(soknadString: String) {
        val reisetilskudd = soknadString.tilReisetilskudd()

        val nokkel = NokkelBuilder()
            .withSystembruker(serviceuserUsername)
            .build()

        val beskjed = BeskjedBuilder()
            .withGrupperingsId("df")
            .withFodselsnummer(reisetilskudd.fnr)
            .withGrupperingsId(UUID.randomUUID().toString())
            .withLink(URL(flexReisetilskuddFrontendUrl)) // TODO hvilken side skal vi til?
            .withSikkerhetsnivaa(4)
            .withSynligFremTil(LocalDateTime.now().plusDays(4))
            .withTekst("Du har en søknad om reisetilskudd til utfylling")
            .withTidspunkt(LocalDateTime.now())
            .build()

        beskjedKafkaTemplate.send("aapen-brukernotifikasjon-nyBeskjed-v1", nokkel, beskjed).get()
        log.info("Mottok reisetilskuddsøknad ${reisetilskudd.reisetilskuddId} med status ${reisetilskudd.status}")
    }
}
