package ma.enset.adria.transaction.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.transaction.exception.*;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type = switch (ex) {
            case TransactionNotFoundException e -> ErrorType.NOT_FOUND;
            case AccountNotFoundException e -> ErrorType.NOT_FOUND;
            case AccountNotActiveException e -> ErrorType.FORBIDDEN;
            case BusinessRuleException e -> ErrorType.BAD_REQUEST;
            case ConstraintViolationException e -> ErrorType.BAD_REQUEST;
            case AccountServiceUnavailableException e -> ErrorType.INTERNAL_ERROR;
            case MaxAmountNotConfiguredException e -> ErrorType.INTERNAL_ERROR;
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
