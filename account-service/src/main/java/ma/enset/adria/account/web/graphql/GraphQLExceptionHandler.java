package ma.enset.adria.account.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.account.exception.AccountNotActiveException;
import ma.enset.adria.account.exception.AccountNotFoundException;
import ma.enset.adria.account.exception.InsufficientBalanceException;
import ma.enset.adria.account.exception.InvalidAmountException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type = switch (ex) {
            case AccountNotFoundException e -> ErrorType.NOT_FOUND;
            case AccountNotActiveException e -> ErrorType.FORBIDDEN;
            case InsufficientBalanceException e -> ErrorType.BAD_REQUEST;
            case InvalidAmountException e -> ErrorType.BAD_REQUEST;
            case ConstraintViolationException e -> ErrorType.BAD_REQUEST;
            default -> null;
        };
        if (type == null) {
            return null; // let Spring GraphQL handle unexpected errors
        }
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage())
                .build();
    }
}
