package pe.edu.planner.progress;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.planner.auth.PlannerPrincipal;

/**
 * Controlador REST para consultar el avance anual de temas por área y estado.
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
    return service.getAnnualProgress(user, contextId, areaId, status);
  }
}

