package com.careermatcher;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL and one SeaweedFS container per test JVM, shared by every Spring test context.
 * (Per-context containers multiplied with each distinct context and starved Docker of memory.)
 * Tests must therefore not assume an empty database: they use unique data per test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  /** Keep in sync with the images used in docker-compose.yml. */
  public static final String POSTGRES_IMAGE = "postgres:18-alpine";

  public static final String SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.54";

  private static final String S3_CONFIG =
      "{\"identities\":[{\"name\":\"backend\",\"credentials\":[{\"accessKey\":\"test-access-key\","
          + "\"secretKey\":\"test-secret-key-0123456789\"}],\"actions\":[\"Read\",\"Write\",\"List\",\"Admin\"]}]}";

  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));

  static final GenericContainer<?> SEAWEEDFS =
      new GenericContainer<>(DockerImageName.parse(SEAWEEDFS_IMAGE))
          .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
          .withCommand(
              "-c",
              "printf '%s' '" + S3_CONFIG + "' > /tmp/s3.json && exec weed mini -dir=/data"
                  + " -s3.config=/tmp/s3.json -ip.bind=0.0.0.0")
          .withExposedPorts(8333)
          // Anonymous access is denied (403) once the S3 gateway is serving.
          .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403));

  static {
    POSTGRES.start();
    SEAWEEDFS.start();
  }

  /**
   * Connection details as properties rather than container beans: a container bean would be
   * stopped when any one context closes (e.g. after failing to start), breaking all the others.
   */
  @Bean
  DynamicPropertyRegistrar containerProperties() {
    return registry -> {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
      registry.add(
          "app.storage.endpoint",
          () -> "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333));
    };
  }
}
