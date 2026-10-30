package ma.enset.adria.account.web.rest;

import ma.enset.adria.account.exception.AccountNotActiveException;
import ma.enset.adria.account.exception.AccountNotFoundException;
import ma.enset.adria.account.exception.InsufficientBalanceException;
import ma.enset.adria.account.exception.InvalidAmountException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", ex.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ProblemDetail handleNotActive(AccountNotActiveException ex) {
        return problem(HttpStatus.CONFLICT, "Account not active", ex.getMessage());
    }

    @ExceptionHandler({InsufficientBalanceException.class, InvalidAmountException.class})
    public ProblemDetail handleBusinessRule(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
