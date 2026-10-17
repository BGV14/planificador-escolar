package pe.edu.planner.api;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.planning.PlannerService;
import pe.edu.planner.planning.PlannerService.*;

@RestController
@RequestMapping("/api")
public class PlannerController {
  private final PlannerService planner;

  public PlannerController(PlannerService planner) {
    this.planner = planner;
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  Map<String, Object> me(@AuthenticationPrincipal PlannerPrincipal user) {
    return planner.me(user);
  }

  @GetMapping("/users")
  List<Map<String, Object>> users(@AuthenticationPrincipal PlannerPrincipal user) {
    return planner.users(user);
  }

  @PostMapping("/users")
  Map<String, Object> user(
      @AuthenticationPrincipal PlannerPrincipal user, @RequestBody UserInput input) {
    return planner.createUser(user, input);
  }

  @GetMapping("/areas")
  List<Map<String, Object>> areas(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "areas");
  }

  @PostMapping("/areas")
  Map<String, Object> area(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody AreaInput input) {
    return planner.area(user, selected(user, contextId), null, input);
  }

  @PutMapping("/areas/{id}")
  Map<String, Object> area(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestBody AreaInput input) {
    return planner.area(user, selected(user, contextId), id, input);
  }

  @DeleteMapping("/areas/{id}")
  void deleteArea(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id) {
    planner.delete(user, selected(user, contextId), "areas", id, null);
  }

  @GetMapping("/topics")
  List<Map<String, Object>> topics(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "topics");
  }

  @PostMapping("/topics")
  Map<String, Object> topic(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody TopicInput input) {
    return planner.topic(user, selected(user, contextId), null, input);
  }

  @PutMapping("/topics/{id}")
  Map<String, Object> topic(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestBody TopicInput input) {
    return planner.topic(user, selected(user, contextId), id, input);
  }

  @DeleteMapping("/topics/{id}")
  void deleteTopic(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id) {
    planner.delete(user, selected(user, contextId), "topics", id, null);
  }

  @GetMapping("/schedules")
  List<Map<String, Object>> schedules(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "schedules");
  }

  @PostMapping("/schedules")
  Map<String, Object> schedule(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody ScheduleInput input) {
    return planner.schedule(user, selected(user, contextId), null, input);
  }

  @PutMapping("/schedules/{id}")
  Map<String, Object> schedule(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestBody ScheduleInput input) {
    return planner.schedule(user, selected(user, contextId), id, input);
  }

  @DeleteMapping("/schedules/{id}")
  void deleteSchedule(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id) {
    planner.delete(user, selected(user, contextId), "schedules", id, null);
  }

  @GetMapping("/sessions")
  List<Map<String, Object>> sessions(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "planned_sessions");
  }

  @PostMapping("/sessions")
  Map<String, Object> session(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody SessionInput input) {
    return planner.session(user, selected(user, contextId), null, input);
  }

  @PutMapping("/sessions/{id}")
  Map<String, Object> session(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestBody SessionInput input) {
    return planner.session(user, selected(user, contextId), id, input);
  }

  @DeleteMapping("/sessions/{id}")
  void deleteSession(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestParam Long version) {
    planner.delete(user, selected(user, contextId), "planned_sessions", id, version);
  }

  @GetMapping("/blocks")
  List<Map<String, Object>> blocks(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "calendar_blocks");
  }

  @PostMapping("/blocks")
  Map<String, Object> block(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody BlockInput input) {
    return planner.block(user, selected(user, contextId), null, input);
  }

  @PutMapping("/blocks/{id}")
  Map<String, Object> block(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id,
      @RequestBody BlockInput input) {
    return planner.block(user, selected(user, contextId), id, input);
  }

  @DeleteMapping("/blocks/{id}")
  void deleteBlock(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @PathVariable String id) {
    planner.delete(user, selected(user, contextId), "calendar_blocks", id, null);
  }

  @GetMapping("/plan")
  Map<String, Object> plan(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.plan(user, selected(user, contextId));
  }

  @GetMapping("/imports")
  List<Map<String, Object>> imports(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId) {
    return planner.list(user, selected(user, contextId), "import_history");
  }

  @PostMapping("/imports/preview")
  Map<String, Object> preview(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody ImportInput input) {
    return planner.preview(user, selected(user, contextId), input);
  }

  @PostMapping("/imports/confirm")
  Map<String, Object> confirm(
      @AuthenticationPrincipal PlannerPrincipal user,
      @RequestParam(required = false) String contextId,
      @RequestBody ImportInput input) {
    return planner.confirm(user, selected(user, contextId), input);
  }

  @GetMapping("/contexts")
  List<Map<String, Object>> contexts(@AuthenticationPrincipal PlannerPrincipal user) {
    return planner.contexts(user);
  }

  @PostMapping("/contexts")
  Map<String, Object> createContext(
      @AuthenticationPrincipal PlannerPrincipal user, @RequestBody ContextInput input) {
    return planner.createContext(user, input);
  }

  // Temporary old-client adapter. Explicit empty/invalid IDs never fall back.
  private String selected(PlannerPrincipal user, String contextId) {
    return contextId == null ? planner.legacyContextId(user) : contextId;
  }
}
