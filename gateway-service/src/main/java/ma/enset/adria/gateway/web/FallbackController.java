package ma.enset.adria.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Target of the gateway circuit breakers: answers a clean 503 when a downstream service is down.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ProblemDetail fallback(@PathVariable String service) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                service + " is unavailable, please retry later");
        problem.setTitle("Service unavailable");
        return problem;
    }
}
