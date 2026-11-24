package pk.pm.pasir_pecek_maksymilian.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Runs the full API integration test suite against a real MySQL 8.4 container
 * whenever Docker is available (e.g., locally with Docker Desktop or in GitHub Actions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "JWT_SECRET=testcontainers-mysql-jwt-secret-key-at-least-64-bytes-long-0123456789"
})
class MysqlContainerIntegrationTests extends AbstractApiIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");
}
