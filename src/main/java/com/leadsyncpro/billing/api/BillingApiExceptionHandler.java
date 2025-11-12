package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.api.idempotency.IdempotencyConflictException;
import com.leadsyncpro.billing.api.idempotency.IdempotencyKeyMissingException;
import com.leadsyncpro.billing.facade.SubscriptionNotFoundException;
import com.leadsyncpro.billing.facade.SubscriptionOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BillingController.class)
public class BillingApiExceptionHandler {

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(SubscriptionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Subscription resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(SubscriptionOperationException.class)
    public ResponseEntity<ProblemDetail> handleOperation(SubscriptionOperationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Subscription operation failed");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ResponseEntity<ProblemDetail> handleMissingIdempotencyKey(IdempotencyKeyMissingException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Idempotency key missing");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Idempotency conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }
}
