package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.config

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Serializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaProducerConfig(
    @Value("\${on-prem-kafka.schema-registry.url}") val kafkaSchemaRegistryUrl: String,
    @Value("\${on-prem-kafka.bootstrap-servers}") val kafkaBootstrapServers: String,
    @Value("\${on-prem-kafka.security-protocol}") val kafkaSecurityProtocol: String,
    @Value("\${serviceuser.username}") val serviceuserUsername: String,
    @Value("\${serviceuser.password}") val serviceuserPassword: String
) {

    @Bean
    @Profile("default")
    fun producerFactory(): ProducerFactory<Nokkel, Beskjed> {
        val client = CachedSchemaRegistryClient(kafkaSchemaRegistryUrl, 10)
        val kafkaAvroSerializer = KafkaAvroSerializer(client)
        val config = mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl,
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${serviceuserUsername}\" password=\"${serviceuserPassword}\";",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        )

        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(config, kafkaAvroSerializer as Serializer<Nokkel>, kafkaAvroSerializer as Serializer<Beskjed>)
    }

    @Bean
    fun beskjedKafkaTemplate(producerFactory: ProducerFactory<Nokkel, Beskjed>): KafkaTemplate<Nokkel, Beskjed> {
        return KafkaTemplate(producerFactory)
    }
}
