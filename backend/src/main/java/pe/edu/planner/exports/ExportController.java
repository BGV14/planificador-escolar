package pe.edu.planner.exports;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;

@RestController
@RequestMapping("/api/exports")
public class ExportController {
  private final ExportService exports;

  public ExportController(ExportService exports) {
    this.exports = exports;
  }

  @GetMapping("/monthly")
  ResponseEntity<byte[]> monthly(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestParam(required = false) String areaId,
      @RequestParam int month,
      @RequestParam String format)
      throws IOException {
    if (!format.equals("xlsx") && !format.equals("pdf"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format must be xlsx or pdf");
    var plan =
        exports.monthly(
            user, contextId == null ? exports.legacyContextId(user) : contextId, areaId, month);
    byte[] body =
        format.equals("xlsx") ? new MonthlyWorkbook().write(plan) : new MonthlyPdf().write(plan);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"plan-" + plan.year() + "-" + month + "." + format + "\"")
        .contentType(
            MediaType.parseMediaType(
                format.equals("xlsx")
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "application/pdf"))
        .body(body);
  }

  @GetMapping("/backup")
  ResponseEntity<Map<String, Object>> backup(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestParam(required = false) String areaId,
      @RequestParam(required = false) Integer month,
      @RequestParam(defaultValue = "json") String format) {
    if (!format.equals("json") && !format.equals("full") && !format.equals("full-v4"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Format must be json, full (deprecated v3) or full-v4");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"planner-backup-v"
                + (format.equals("full-v4") ? 4 : format.equals("full") ? 3 : 2)
                + ".json\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            exports.backup(
                user,
                contextId == null ? exports.legacyContextId(user) : contextId,
                areaId,
                month,
                format));
  }
}
