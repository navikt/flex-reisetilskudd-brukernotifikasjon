package no.nav.helse.flex.reisetilskudd.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository.TilUtfyllingRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import sharedKafkaContainer
import sharedPostgreSQLContainer
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.collections.ArrayList

@SpringBootTest
@DirtiesContext
@Testcontainers
class BrukernotifikasjonIntegrationTest {

    companion object {
        @Container
        val postgreSQLContainer = sharedPostgreSQLContainer

        @Container
        val kafkaContainer = sharedKafkaContainer
    }

    @Autowired
    private lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    private lateinit var kafkaConsumer: Consumer<Nokkel, Beskjed>

    @Autowired
    private lateinit var tilUtfyllingRepository: TilUtfyllingRepository

    @Test
    fun `APEN søknad prosesseres og lagres i databasen`() {
        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.ÅPEN,
            fnr = "12345600000",
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        kafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()
        kafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get() // Håndterer duplikater

        kafkaConsumer.subscribe(listOf("aapen-brukernotifikasjon-nyBeskjed-v1"))

        val records = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        await().atMost(6, SECONDS).until {
            records.addAll(kafkaConsumer.poll(Duration.ofSeconds(1)))
            records.size == 1
        }

        records[0].value().getTekst() shouldBeEqualTo "Du har en søknad om reisetilskudd til utfylling"

        val alleTilUtfylling = tilUtfyllingRepository.findAll().iterator().asSequence().toList()
        alleTilUtfylling.size shouldBeEqualTo 1

        val tilUtfylling = tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(soknad.reisetilskuddId)!!
        tilUtfylling.eksterntVarsel shouldBeEqualTo false
        tilUtfylling.doneSendt.shouldBeNull()
        tilUtfylling.beskjedSendt.shouldNotBeNull()
    }
}

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
