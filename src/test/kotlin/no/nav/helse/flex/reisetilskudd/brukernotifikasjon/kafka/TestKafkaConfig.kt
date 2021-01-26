package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.kafka

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.*
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import java.io.Serializable
import java.util.*

@Configuration
@Profile("test")
class TestKafkaConfig(
    @Value("\${on-prem-kafka.bootstrap-servers}") val kafkaBootstrapServers: String,
) {

    private fun config(): Map<String, Serializable> {
        return mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://whatever.nav",
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "plaintext",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        )
    }

    @Bean
    fun kafkaProducer(properties: KafkaProperties): KafkaProducer<String, String> {
        return KafkaProducer(properties.buildProducerProperties())
    }

    @Bean
    fun mockSchemaRegistryClient(): MockSchemaRegistryClient {
        val mockSchemaRegistryClient = MockSchemaRegistryClient()
        mockSchemaRegistryClient.register("aapen-brukernotifikasjon-nyBeskjed-v1" + "-value", AvroSchema(Beskjed.`SCHEMA$`))
        mockSchemaRegistryClient.register("aapen-brukernotifikasjon-nyBeskjed-v1" + "-value", AvroSchema(Nokkel.`SCHEMA$`))
        return mockSchemaRegistryClient
    }

    fun kafkaAvroDeserializer(): KafkaAvroDeserializer {
        val config = HashMap<String, Any>()
        config[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
        config[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        config[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = "http://ikke.i.bruk.nav"
        return KafkaAvroDeserializer(mockSchemaRegistryClient(), config)
    }

    @Bean
    fun consumerFactoryBeskjed(properties: KafkaProperties): ConsumerFactory<Nokkel, Beskjed> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            properties.buildConsumerProperties(),
            kafkaAvroDeserializer() as Deserializer<Nokkel>,
            kafkaAvroDeserializer() as Deserializer<Beskjed>
        )
    }

    @Bean
    fun consumerFactoryOppgave(properties: KafkaProperties): ConsumerFactory<Nokkel, Oppgave> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            properties.buildConsumerProperties(),
            kafkaAvroDeserializer() as Deserializer<Nokkel>,
            kafkaAvroDeserializer() as Deserializer<Oppgave>
        )
    }

    @Bean
    fun kafkaConsumer(consumerFactoryBeskjed: ConsumerFactory<Nokkel, Beskjed>): Consumer<Nokkel, Beskjed> {
        return consumerFactoryBeskjed.createConsumer()
    }

    @Bean
    fun beskjedKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<Nokkel, Beskjed> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(config(), kafkaAvroSerializer as Serializer<Nokkel>, kafkaAvroSerializer as Serializer<Beskjed>).createProducer()
    }

    @Bean
    fun oppgaveKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<Nokkel, Oppgave> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(config(), kafkaAvroSerializer as Serializer<Nokkel>, kafkaAvroSerializer as Serializer<Oppgave>).createProducer()
    }

    @Bean
    fun doneKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<Nokkel, Done> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(config(), kafkaAvroSerializer as Serializer<Nokkel>, kafkaAvroSerializer as Serializer<Done>).createProducer()
    }
}
