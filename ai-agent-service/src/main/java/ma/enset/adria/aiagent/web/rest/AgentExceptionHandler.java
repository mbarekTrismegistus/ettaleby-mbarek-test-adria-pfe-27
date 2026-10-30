package ma.enset.adria.aiagent.web.rest;

import ma.enset.adria.aiagent.service.AgentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class AgentExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExceptionHandler.class);

    @ExceptionHandler(AgentUnavailableException.class)
    public ProblemDetail unavailable(AgentUnavailableException exception) {
        LOG.warn("Agent request failed for conversation {} ({})", exception.getConversationId(),
                exception.getCause().getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The assistant or a banking tool is unavailable. An operation may already have completed; "
                        + "check transaction history before retrying.");
        problem.setTitle("Banking assistant unavailable");
        if (exception.getConversationId() != null) {
            problem.setProperty("conversationId", exception.getConversationId());
        }
        return problem;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalidRequest(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Provide a non-blank message of at most 4000 characters and, optionally, a valid conversation UUID.");
        problem.setTitle("Invalid agent request");
        return problem;
    }
}
