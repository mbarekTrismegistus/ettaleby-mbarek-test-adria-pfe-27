package ma.enset.adria.transaction.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import ma.enset.adria.transaction.exception.AccountNotActiveException;
import ma.enset.adria.transaction.exception.AccountNotFoundException;
import ma.enset.adria.transaction.exception.BusinessRuleException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

/**
 * Translates account-service error responses (ProblemDetail) back into business exceptions,
 * so they are not counted as technical failures by the circuit breaker.
 */
@RequiredArgsConstructor
public class AccountErrorDecoder implements ErrorDecoder {

    private final JsonMapper jsonMapper;
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String detail = readDetail(response);
        return switch (response.status()) {
            case 404 -> new AccountNotFoundException(detail);
            case 409 -> new AccountNotActiveException(detail);
            case 400, 422 -> new BusinessRuleException(detail);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    private String readDetail(Response response) {
        if (response.body() == null) {
            return "account-service returned HTTP " + response.status();
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode node = jsonMapper.readTree(body);
            return node.path("detail").asString("account-service returned HTTP " + response.status());
        } catch (Exception e) {
            return "account-service returned HTTP " + response.status();
        }
    }
}
