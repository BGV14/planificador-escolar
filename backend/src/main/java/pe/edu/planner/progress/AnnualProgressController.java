package pe.edu.planner.progress;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;

/**
 * Controlador REST para consultar el avance anual de temas por área y estado.
 * Accesible para usuarios autenticados con rol ADMIN o TEACHER pertenecientes a la escuela activa.
 * No exige titularidad/responsabilidad del área ya que la consulta es de solo lectura para todo el catálogo.
 */
@RestController
@RequestMapping("/api/progress")
public class AnnualProgressController {

  private final AnnualProgressService service;

  public AnnualProgressController(AnnualProgressService service) {
    this.service = service;
  }

  @GetMapping("/annual")
  public AnnualProgressDto annual(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam String contextId,
      @RequestParam(required = false) String areaId,
      @RequestParam(required = false) TopicAnnualStatus status) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return service.getAnnualProgress(user, contextId, areaId, status);
  }
}
