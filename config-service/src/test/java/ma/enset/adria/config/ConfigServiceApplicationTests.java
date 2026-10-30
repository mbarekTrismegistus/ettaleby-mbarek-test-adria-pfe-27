package ma.enset.adria.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ConfigServiceApplicationTests {

    @Autowired
    private EnvironmentRepository repository;

    @Test
    void servesTransactionCeilingFromNativeRepository() {
        var environment = repository.findOne("transaction-service", "default", null);
        assertThat(environment.getPropertySources())
                .anySatisfy(source -> assertThat(source.getSource().get("bank.transaction.max-amount"))
                        .isEqualTo("1000"));
    }

    @Test
    void servesSharedDiscoveryAndGatewaySpecificConfiguration() {
        var environment = repository.findOne("gateway-service", "default", null);
        assertThat(environment.getPropertySources())
                .anySatisfy(source -> assertThat(source.getSource().get("eureka.client.service-url.defaultZone"))
                        .isNotNull());
        assertThat(environment.getPropertySources())
                .anySatisfy(source -> assertThat(source.getSource().get("management.endpoint.gateway.access"))
                        .isEqualTo("read-only"));
    }

}
