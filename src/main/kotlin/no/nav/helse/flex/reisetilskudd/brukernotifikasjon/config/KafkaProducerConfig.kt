package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("default")
class KafkaProducerConfig(
    @Value("\${on-prem-kafka.schema-registry.url}") val kafkaSchemaRegistryUrl: String,
    @Value("\${on-prem-kafka.bootstrap-servers}") val kafkaBootstrapServers: String,
    @Value("\${on-prem-kafka.security-protocol}") val kafkaSecurityProtocol: String,
    @Value("\${serviceuser.username}") val serviceuserUsername: String,
    @Value("\${serviceuser.password}") val serviceuserPassword: String
) {

    private fun commonConfig(): Map<String, String> {
        return mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${serviceuserUsername}\" password=\"${serviceuserPassword}\";",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        )
    }

    private fun commonProducerConfig(
        keySerializer: Class<*>,
        valueSerializer: Class<*>
    ): Map<String, Any> {
        return mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer
        ) + commonConfig()
    }

    fun <K, V> skapBrukernotifikasjonKafkaProducer(): KafkaProducer<K, V> =
        KafkaProducer(
            commonProducerConfig(
                keySerializer = KafkaAvroSerializer::class.java,
                valueSerializer = KafkaAvroSerializer::class.java
            ) + mapOf(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl
            )
        )

    @Bean
    fun beskjedKafkaProducer(): KafkaProducer<Nokkel, Beskjed> {
        return skapBrukernotifikasjonKafkaProducer()
    }

    @Bean
    fun oppgaveKafkaProducer(): KafkaProducer<Nokkel, Oppgave> {
        return skapBrukernotifikasjonKafkaProducer()
    }

    @Bean
    fun doneKafkaProducer(): KafkaProducer<Nokkel, Done> {
        return skapBrukernotifikasjonKafkaProducer()
    }
}
