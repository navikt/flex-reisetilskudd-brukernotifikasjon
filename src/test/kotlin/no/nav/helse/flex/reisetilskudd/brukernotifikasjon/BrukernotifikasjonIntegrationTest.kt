package no.nav.helse.flex.reisetilskudd.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config.FLEX_APEN_REISETILSKUDD_TOPIC
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.ReisetilskuddStatus
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.collections.ArrayList

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = [FLEX_APEN_REISETILSKUDD_TOPIC]
)
class BrukernotifikasjonIntegrationTest() {

    @Autowired
    private lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    private lateinit var kafkaConsumer: Consumer<Nokkel, Beskjed>

    @Test
    fun `SENDT søknad prosesseres og lagres i databasen`() {
        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.SENDT,
            fnr = "12345600000",
            reisetilskuddId = UUID.randomUUID().toString(),
            kvitteringer = emptyList()
        )
        kafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()

        kafkaConsumer.subscribe(listOf("aapen-brukernotifikasjon-nyBeskjed-v1"))
        val records = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        await().atMost(6, SECONDS).until {
            records.addAll(kafkaConsumer.poll(Duration.ofSeconds(1)))
            records.size == 1
        }

        MatcherAssert.assertThat(records[0].value().getTekst(), CoreMatchers.`is`("Du har en søknad om reisetilskudd til utfylling"))
    }
}

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
