package ma.enset.adria.transaction.client;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

public class AccountClientConfig {

    @Bean
    ErrorDecoder accountErrorDecoder(JsonMapper jsonMapper) {
        return new AccountErrorDecoder(jsonMapper);
    }
}
