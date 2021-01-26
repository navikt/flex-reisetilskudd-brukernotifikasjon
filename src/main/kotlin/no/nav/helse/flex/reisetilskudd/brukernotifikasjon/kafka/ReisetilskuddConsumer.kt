package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.kafka

import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config.FLEX_APEN_REISETILSKUDD_TOPIC
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.innsending.ReisetilskuddBrukernotifikasjonService
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.log
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.selvtest.ApplicationState
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ReisetilskuddConsumer(
    private val reisetilskuddBrukernotifikasjonService: ReisetilskuddBrukernotifikasjonService,
    private val applicationState: ApplicationState
) {

    private val log = log()

    @KafkaListener(topics = [FLEX_APEN_REISETILSKUDD_TOPIC])
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {

        log.info("Behandler reisetilskuddsøknad ${cr.key()}")
        try {
            reisetilskuddBrukernotifikasjonService.behandleReisetilskuddSoknad(cr.value())
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}", e)
            throw e
        }
    }

    @EventListener
    fun eventHandler(event: ConsumerStoppedEvent) {
        log.error("Consumer stoppet grunnet ${event.reason}, restarter app")
        applicationState.iAmDead()
    }
}
