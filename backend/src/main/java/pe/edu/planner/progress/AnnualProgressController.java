package pe.edu.planner.progress;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;

/**
 * Controlador REST para consultar el avance anual de temas por área y estado.
 * Demostración de catálogo sintético exclusiva para administración de la escuela activa.
 * El servicio aplica la autorización; los datos simulados no representan asignaciones docentes.
 */
@RestController
@RequestMapping("/api/progress")
@Profile("annual-progress-demo")
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
