package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.innsending


import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.tilReisetilskudd
import no.nav.helse.flex.reisetilskudd.gsak.domain.*
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.log
import org.springframework.stereotype.Component


@Component
class ReisetilskuddBrukernotifikasjonService() {

    private val log = log()

    fun behandleReisetilskuddSoknad(soknadString: String) {
        val reisetilskudd = soknadString.tilReisetilskudd()

        log.info("Mottok reisetilskuddsøknad ${reisetilskudd.reisetilskuddId} med status ${reisetilskudd.status}")
    }
}
