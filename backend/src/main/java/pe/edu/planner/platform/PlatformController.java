package pe.edu.planner.platform;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.platform.PlatformService.*;

@RestController
@RequestMapping("/api/platform/schools")
public class PlatformController {
  private final PlatformService platform;

  public PlatformController(PlatformService platform) {
    this.platform = platform;
  }

  @GetMapping
  List<SchoolView> schools(@AuthenticationPrincipal PlannerPrincipal user) {
    return platform.schools(user);
  }

  @PostMapping
  SchoolView createSchool(@AuthenticationPrincipal PlannerPrincipal user, @RequestBody SchoolInput input) {
    return platform.createSchool(user, input);
  }

  @PostMapping("/{schoolId}/administrators")
  AdministratorView createAdministrator(@AuthenticationPrincipal PlannerPrincipal user,
      @PathVariable String schoolId, @RequestBody AdministratorInput input) {
    return platform.createAdministrator(user, schoolId, input);
  }
}
