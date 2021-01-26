import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.KafkaContainer as LibKafkaContainer

val sharedKafkaContainer = KafkaContainer()

class KafkaContainer : LibKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3")) {

    override fun start() {
        super.start()
        System.setProperty("on-prem-kafka.bootstrap-servers", sharedKafkaContainer.bootstrapServers)
        System.setProperty("spring.kafka.bootstrap-servers", sharedKafkaContainer.bootstrapServers)
    }
}
