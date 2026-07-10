package com.executionos.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Pure unit tests -- no Spring context. Each handler is called directly;
 * this only proves the mapping logic (exception type -> status + body),
 * not the wiring of @RestControllerAdvice into the dispatcher, which needs
 * a running application to verify (see PRODUCTION_AUDIT.md re: this
 * sandbox's inability to run a live server).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentExceptionBecomesBadRequestWithItsMessage() {
        ResponseEntity<java.util.Map<String, String>> response = handler.badRequest(new IllegalArgumentException("Invalid email or password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid email or password");
    }

    @Test
    void malformedRequestParameterNamesTheOffendingParameterAndExpectedType() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("organizationId");
        doReturn(java.util.UUID.class).when(ex).getRequiredType();

        ResponseEntity<java.util.Map<String, String>> response = handler.badParameter(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).contains("organizationId").contains("UUID");
    }

    @Test
    void malformedRequestParameterFallsBackGracefullyWhenTypeIsUnknown() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("difficulty");
        doReturn(null).when(ex).getRequiredType();

        ResponseEntity<java.util.Map<String, String>> response = handler.badParameter(ex);

        assertThat(response.getBody().get("error")).contains("difficulty").contains("the expected type");
    }

    @Test
    void validationErrorSurfacesTheFirstFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("loginRequest", "email", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<java.util.Map<String, String>> response = handler.validationError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "email: must not be blank");
    }

    @Test
    void noSuchElementBecomesNotFoundWithoutLeakingInternalDetails() {
        ResponseEntity<java.util.Map<String, String>> response = handler.notFound(new NoSuchElementException("sf_users row 1234 missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Deliberately generic -- must not echo the exception's own message,
        // which could leak internal identifiers/table names to the client.
        assertThat(response.getBody()).containsEntry("error", "Resource not found");
    }

    @Test
    void authenticationExceptionBecomesUnauthorized() {
        AuthenticationException ex = mock(AuthenticationException.class);
        ResponseEntity<java.util.Map<String, String>> response = handler.unauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Unauthorized");
    }

    @Test
    void accessDeniedExceptionBecomesForbidden() {
        ResponseEntity<java.util.Map<String, String>> response = handler.forbidden(new AccessDeniedException("Cannot view another organization"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Access denied");
    }

    @Test
    void unexpectedExceptionsBecomeGeneric500sWithoutLeakingTheirMessage() {
        ResponseEntity<java.util.Map<String, String>> response = handler.serverError(new RuntimeException("NullPointerException at line 42 in SkillForgeService"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // This is the important assertion: the real exception message
        // (which could contain stack-trace-adjacent internals) must never
        // reach the client.
        assertThat(response.getBody()).containsEntry("error", "An internal error occurred");
    }

}
