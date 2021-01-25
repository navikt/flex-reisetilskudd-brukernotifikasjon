import org.testcontainers.containers.PostgreSQLContainer as LibPostgreSQLContainer

val sharedPostgreSQLContainer = PostgreSQLContainer()

class PostgreSQLContainer : LibPostgreSQLContainer<PostgreSQLContainer>("postgres:11.4-alpine") {

    override fun start() {
        super.start()
        System.setProperty("spring.datasource.url", sharedPostgreSQLContainer.jdbcUrl)
        System.setProperty("spring.datasource.username", sharedPostgreSQLContainer.username)
        System.setProperty("spring.datasource.password", sharedPostgreSQLContainer.password)
    }
}
