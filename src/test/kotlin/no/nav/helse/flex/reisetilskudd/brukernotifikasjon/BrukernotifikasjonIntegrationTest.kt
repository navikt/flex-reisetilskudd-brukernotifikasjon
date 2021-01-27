package no.nav.helse.flex.reisetilskudd.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config.BESKJED_TOPIC
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config.DONE_TOPIC
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config.OPPGAVE_TOPIC
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.TilInnsending
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.TilUtfylling
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository.TilInnsendingRepository
import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository.TilUtfyllingRepository
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import sharedKafkaContainer
import sharedPostgreSQLContainer
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BrukernotifikasjonIntegrationTest {

    companion object {
        @Container
        val postgreSQLContainer = sharedPostgreSQLContainer

        @Container
        val kafkaContainer = sharedKafkaContainer
    }

    private val log = log()

    @Autowired
    private lateinit var reistilskuddKafkaProducer: KafkaProducer<String, String>

    @Autowired
    private lateinit var beskjedKafkaConsumer: Consumer<Nokkel, Beskjed>

    @Autowired
    private lateinit var oppgaveKafkaConsumer: Consumer<Nokkel, Oppgave>

    @Autowired
    private lateinit var doneKafkaConsumer: Consumer<Nokkel, Done>

    @Autowired
    private lateinit var tilUtfyllingRepository: TilUtfyllingRepository

    @Autowired
    private lateinit var tilInnsendingRepository: TilInnsendingRepository

    @Test
    @Order(0)
    fun `vi subscriber på topics`() {
        oppgaveKafkaConsumer.subscribe(listOf(OPPGAVE_TOPIC))
        doneKafkaConsumer.subscribe(listOf(DONE_TOPIC))
        beskjedKafkaConsumer.subscribe(listOf(BESKJED_TOPIC))
    }

    private val fnr = "12345600000"

    @Test
    @Order(1)
    fun `Fremtidig søknad ignoreres`() {
        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.FREMTIDIG,
            fnr = fnr,
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()

        val beskjedRecords = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        await().during(1, SECONDS).until {
            beskjedRecords.addAll(beskjedKafkaConsumer.poll(Duration.ofSeconds(1)))
            beskjedRecords.size == 0
        }

        tilUtfyllingRepository.findAll().`should be empty`()
        tilInnsendingRepository.findAll().`should be empty`()
    }

    @Test
    @Order(2)
    fun `Åpen søknad skaper en beskjed`() {
        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.ÅPEN,
            fnr = "12345600000",
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get() // Håndterer duplikater

        val records = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        await().atMost(6, SECONDS).until {
            records.addAll(beskjedKafkaConsumer.poll(Duration.ofSeconds(1)))
            records.size == 1
        }
        beskjedKafkaConsumer.commitSync()

        records[0].value().getTekst() shouldBeEqualTo "Du har en søknad om reisetilskudd til utfylling"

        val alleTilUtfylling = HashSet<TilUtfylling>()
        await().atMost(6, SECONDS).until {
            alleTilUtfylling.addAll(tilUtfyllingRepository.findAll().iterator().asSequence().toList())
            alleTilUtfylling.size == 1
        }

        val tilUtfylling = tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(soknad.reisetilskuddId)!!
        tilUtfylling.eksterntVarsel shouldBeEqualTo false
        tilUtfylling.doneSendt.shouldBeNull()
        tilUtfylling.beskjedSendt.shouldNotBeNull()

        tilInnsendingRepository.findAll().`should be empty`()

        records[0].value().getTekst() shouldBeEqualTo "Du har en søknad om reisetilskudd til utfylling"
    }

    @Test
    @Order(3)
    fun `Åpen søknad skaper enda en beskjed`() {
        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.ÅPEN,
            fnr = "12345600000",
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get() // Håndterer duplikater

        val records = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        await().atMost(6, SECONDS).until {
            records.addAll(beskjedKafkaConsumer.poll(Duration.ofSeconds(1)))
            beskjedKafkaConsumer.commitSync()
            records.size == 1
        }

        records[0].value().getTekst() shouldBeEqualTo "Du har en søknad om reisetilskudd til utfylling"
        records[0].value().getGrupperingsId() shouldBeEqualTo soknad.sykmeldingId

        val alleTilUtfylling = HashSet<TilUtfylling>()
        await().atMost(6, SECONDS).until {
            alleTilUtfylling.addAll(tilUtfyllingRepository.findAll().iterator().asSequence().toList())
            alleTilUtfylling.size == 2
        }

        val tilUtfylling = tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(soknad.reisetilskuddId)!!
        tilUtfylling.eksterntVarsel shouldBeEqualTo false
        tilUtfylling.doneSendt.shouldBeNull()
        tilUtfylling.beskjedSendt.shouldNotBeNull()

        tilInnsendingRepository.findAll().`should be empty`()
    }

    @Test
    @Order(4)
    fun `Søknaden blir sendbar skaper en oppgave og donner beskjeden`() {

        val tilUtfyllingFørSendbar = tilUtfyllingRepository.findAll().first()

        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.SENDBAR,
            fnr = fnr,
            reisetilskuddId = tilUtfyllingFørSendbar.reisetilskuddId,
            sykmeldingId = tilUtfyllingFørSendbar.grupperingsid,
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get() // Håndterer duplikater

        val oppgaveRecords = ArrayList<ConsumerRecord<Nokkel, Oppgave>>()
        val beskjedRecords = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        val doneRecords = ArrayList<ConsumerRecord<Nokkel, Done>>()

        await().atMost(12, SECONDS).until {
            oppgaveRecords.addAll(oppgaveKafkaConsumer.poll(Duration.ofSeconds(1)))
            beskjedRecords.addAll(beskjedKafkaConsumer.poll(Duration.ofSeconds(1)))
            doneRecords.addAll(doneKafkaConsumer.poll(Duration.ofSeconds(1)))
            oppgaveKafkaConsumer.commitSync()
            beskjedKafkaConsumer.commitSync()
            doneKafkaConsumer.commitSync()
            beskjedRecords.size == 0 && oppgaveRecords.size == 1 && doneRecords.size == 1
        }

        oppgaveRecords[0].value().getTekst() shouldBeEqualTo "Nå kan du sende inn søknaden om reisetilskudd"
        doneRecords[0].key().getEventId() shouldBeEqualTo tilUtfyllingFørSendbar.nokkel

        val alleTilUtfylling = HashSet<TilUtfylling>()
        await().atMost(6, SECONDS).until {
            alleTilUtfylling.addAll(tilUtfyllingRepository.findAll().iterator().asSequence().toList())
            alleTilUtfylling.size == 2
        }

        val tilUtfylling = tilUtfyllingRepository.findTilUtfyllingByReisetilskuddId(soknad.reisetilskuddId)!!
        tilUtfylling.eksterntVarsel shouldBeEqualTo false
        tilUtfylling.doneSendt.shouldNotBeNull()
        tilUtfylling.beskjedSendt.shouldNotBeNull()

        val alleTilInnsending = HashSet<TilInnsending>()
        await().atMost(6, SECONDS).until {
            alleTilInnsending.addAll(tilInnsendingRepository.findAll().iterator().asSequence().toList())
            alleTilInnsending.size == 1
        }

        alleTilInnsending.first().eksterntVarsel.shouldBeTrue()
        alleTilInnsending.first().doneSendt.shouldBeNull()
        alleTilInnsending.first().oppgaveSendt.shouldNotBeNull()
    }

    @Test
    @Order(5)
    fun `Søknaden blir sendt, da donnes oppgaven`() {

        val skalSendes = tilInnsendingRepository.findAll().first()

        val soknad = Reisetilskudd(
            status = ReisetilskuddStatus.SENDT,
            fnr = fnr,
            reisetilskuddId = skalSendes.reisetilskuddId,
            sykmeldingId = skalSendes.grupperingsid,
            fom = LocalDate.now(),
            tom = LocalDate.now(),
        )
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get()
        reistilskuddKafkaProducer.send(ProducerRecord("flex.aapen-reisetilskudd", soknad.reisetilskuddId, soknad.serialisertTilString())).get() // Håndterer duplikater

        val oppgaveRecords = ArrayList<ConsumerRecord<Nokkel, Oppgave>>()
        val beskjedRecords = ArrayList<ConsumerRecord<Nokkel, Beskjed>>()
        val doneRecords = ArrayList<ConsumerRecord<Nokkel, Done>>()

        await().atMost(12, SECONDS).until {
            oppgaveRecords.addAll(oppgaveKafkaConsumer.poll(Duration.ofSeconds(1)))
            beskjedRecords.addAll(beskjedKafkaConsumer.poll(Duration.ofSeconds(1)))
            doneRecords.addAll(doneKafkaConsumer.poll(Duration.ofSeconds(1)))
            oppgaveKafkaConsumer.commitSync()
            beskjedKafkaConsumer.commitSync()
            doneKafkaConsumer.commitSync()
            beskjedRecords.size == 0 && oppgaveRecords.size == 0 && doneRecords.size == 1
        }

        doneRecords[0].key().getEventId() shouldBeEqualTo skalSendes.nokkel

        val alleTilInnsending = HashSet<TilInnsending>()
        await().atMost(6, SECONDS).until {
            alleTilInnsending.addAll(tilInnsendingRepository.findAll().iterator().asSequence().toList())
            alleTilInnsending.size == 1
        }

        alleTilInnsending.first().eksterntVarsel.shouldBeTrue()
        alleTilInnsending.first().doneSendt.shouldNotBeNull()
        alleTilInnsending.first().oppgaveSendt.shouldNotBeNull()
    }
}

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
