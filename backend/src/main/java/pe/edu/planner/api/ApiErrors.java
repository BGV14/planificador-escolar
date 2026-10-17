package pe.edu.planner.api;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.legacy.ImportValidationException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ImportValidationException.class)
  ResponseEntity<?> invalidImport(ImportValidationException error) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_IMPORT", "violations", error.violations()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .body(
            Map.of(
                "code",
                error.getStatusCode().value(),
                "message",
                error.getReason() == null ? "Request rejected" : error.getReason()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> constraint(DataIntegrityViolationException error) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "code",
                "RESOURCE_CONFLICT",
                "message",
                "Duplicate resource or referenced resource cannot be deleted"));
  }
}
