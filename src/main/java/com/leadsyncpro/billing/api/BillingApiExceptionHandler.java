package com.leadsyncpro.billing.api;

import com.leadsyncpro.billing.api.idempotency.IdempotencyConflictException;
import com.leadsyncpro.billing.api.idempotency.IdempotencyKeyMissingException;
import com.leadsyncpro.billing.integration.iyzico.IyzicoException;
import com.leadsyncpro.billing.facade.SubscriptionNotFoundException;
import com.leadsyncpro.billing.facade.SubscriptionOperationException;
import com.leadsyncpro.billing.service.PlanConflictException;
import com.leadsyncpro.billing.service.PlanValidationException;
import com.leadsyncpro.billing.service.PublicSignupException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.leadsyncpro.billing.api")
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

    @ExceptionHandler(IyzicoException.class)
    public ResponseEntity<ProblemDetail> handleIyzicoException(IyzicoException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        detail.setTitle("Payment gateway error");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(detail);
    }

    @ExceptionHandler(PlanConflictException.class)
    public ResponseEntity<ProblemDetail> handlePlanConflict(PlanConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Plan conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(PlanValidationException.class)
    public ResponseEntity<ProblemDetail> handlePlanValidation(PlanValidationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Plan validation failed");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(PublicSignupException.class)
    public ResponseEntity<ProblemDetail> handlePublicSignup(PublicSignupException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        detail.setTitle("Public signup failed");
        return ResponseEntity.status(ex.getStatus()).body(detail);
    }
}
