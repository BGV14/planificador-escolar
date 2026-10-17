package pe.edu.planner.exports;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.planning.PlannerService;

@Service
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class ExportService {
  private final PlannerService planner;

  public ExportService(PlannerService planner) {
    this.planner = planner;
  }

  public String legacyContextId(PlannerPrincipal user) {
    return planner.legacyContextId(user);
  }

  public MonthlyPlan monthly(PlannerPrincipal user, String areaId, int month) {
    return monthly(user, planner.legacyContextId(user), areaId, month);
  }

  public MonthlyPlan monthly(PlannerPrincipal user, String contextId, String areaId, int month) {
    checkMonth(month);
    var context = planner.plan(user, contextId);
    var me = planner.me(user);
    var school = school(me);
    var allAreas = planner.list(user, contextId, "areas");
    var areas = selectAreas(allAreas, areaId);
    var names = new HashMap<String, String>();
    areas.forEach(a -> names.put(s(a, "id"), s(a, "name")));
    var topics =
        planner.list(user, contextId, "topics").stream()
            .filter(t -> names.containsKey(s(t, "areaId")))
            .toList();
    var sessions = new HashMap<String, Map<String, Object>>();
    planner
        .list(user, contextId, "planned_sessions")
        .forEach(p -> sessions.put(s(p, "topicId"), p));
    int year = ((Number) context.get("year")).intValue();
    String prefix = "%04d-%02d".formatted(year, month);
    String label = MonthlyPlan.MONTHS.get(month - 1);
    var entries = new ArrayList<MonthlyPlan.Entry>();
    for (var t : topics) {
      var session = sessions.getOrDefault(s(t, "id"), Map.of());
      String date = s(session, "exactDate");
      String assignment = s(session, "assignment");
      if (!date.startsWith(prefix)
          && !assignment.equals(label)
          && (!date.isEmpty() || !assignment.isEmpty())) continue;
      entries.add(
          new MonthlyPlan.Entry(
              s(t, "id"),
              names.get(s(t, "areaId")),
              ((Number) t.get("unit")).intValue(),
              s(t, "name"),
              date,
              assignment,
              s(session, "status"),
              s(session, "notes")));
    }
    entries.sort(
        Comparator.comparing(MonthlyPlan.Entry::date)
            .thenComparing(MonthlyPlan.Entry::area)
            .thenComparingInt(MonthlyPlan.Entry::unit)
            .thenComparing(MonthlyPlan.Entry::topic));
    String first = prefix + "-01",
        last = java.time.YearMonth.of(year, month).atEndOfMonth().toString();
    var events = new ArrayList<MonthlyPlan.Event>();
    for (var b : planner.list(user, contextId, "calendar_blocks")) {
      String a = s(b, "areaId");
      if (!a.isEmpty() && !names.containsKey(a)) continue;
      if (s(b, "startDate").compareTo(last) > 0 || s(b, "endDate").compareTo(first) < 0) continue;
      events.add(
          new MonthlyPlan.Event(
              s(b, "kind"),
              s(b, "label"),
              s(b, "startDate"),
              s(b, "endDate"),
              names.getOrDefault(a, "")));
    }
    var schedules =
        planner.list(user, contextId, "schedules").stream()
            .filter(s -> names.containsKey(s(s, "areaId")))
            .sorted(Comparator.comparingInt(s -> ((Number) s.get("weekday")).intValue()))
            .map(
                s ->
                    MonthlyPlan.DAYS.get(((Number) s.get("weekday")).intValue() - 1)
                        + " "
                        + s(s, "startTime")
                        + "-"
                        + s(s, "endTime")
                        + " · "
                        + names.get(s(s, "areaId")))
            .toList();
    return new MonthlyPlan(
        s(school, "name"),
        year,
        (Integer) context.get("grade"),
        month,
        areaId == null || areaId.isBlank() ? "Todas las áreas" : names.get(areaId),
        entries,
        events,
        schedules,
        s(context, "displayLabel"));
  }

  public Map<String, Object> backup(
      PlannerPrincipal user, String areaId, Integer month, boolean full) {
    return backup(user, planner.legacyContextId(user), areaId, month, full ? "full" : "json");
  }

  public Map<String, Object> backup(
      PlannerPrincipal user, String contextId, String areaId, Integer month, String format) {
    if (month != null) checkMonth(month);
    var context = planner.plan(user, contextId);
    var school = school(planner.me(user));
    var areas = planner.list(user, contextId, "areas");
    selectAreas(areas, areaId);
    if (format.equals("full-v4")) {
      var snapshots = new ArrayList<Map<String, Object>>();
      for (var item : planner.contexts(user)) {
        String id = s(item, "id");
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("context", item);
        snapshot.put("areas", planner.list(user, id, "areas"));
        snapshot.put("topics", planner.list(user, id, "topics"));
        snapshot.put("sessions", planner.list(user, id, "planned_sessions"));
        snapshot.put("schedules", planner.list(user, id, "schedules"));
        snapshot.put("blocks", planner.list(user, id, "calendar_blocks"));
        snapshot.put("history", planner.list(user, id, "import_history"));
        snapshots.add(snapshot);
      }
      return Map.of(
          "format",
          "shared-school-planner",
          "version",
          4,
          "scope",
          "complete-school-all-contexts",
          "schoolName",
          school.get("name"),
          "restoreSupported",
          false,
          "contexts",
          snapshots);
    }
    if (format.equals("full")) {
      if (!contextId.equals(planner.legacyContextId(user)))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Deprecated v3 exports only the original default context; use full-v4 for all"
                + " contexts");
      var result = new LinkedHashMap<String, Object>();
      result.put("format", "shared-school-planner");
      result.put("version", 3);
      result.put("scope", "complete-school");
      result.put("year", school.get("school_year"));
      result.put("grade", school.get("grade"));
      result.put("revision", school.get("revision"));
      result.put("schoolName", school.get("name"));
      result.put("areas", areas);
      result.put("topics", planner.list(user, contextId, "topics"));
      result.put("sessions", planner.list(user, contextId, "planned_sessions"));
      result.put("schedules", planner.list(user, contextId, "schedules"));
      result.put("blocks", planner.list(user, contextId, "calendar_blocks"));
      return result;
    }
    if (!Objects.equals(context.get("level"), "PRIMARY")
        || ((Number) context.get("stageValue")).intValue() != 6)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Legacy v2 catalog is only compatible with PRIMARY grade 6; use full-v4");
    var ids = new HashMap<String, String>();
    for (var t : planner.list(user, contextId, "topics"))
      if (t.get("legacyId") != null
          && (areaId == null || areaId.isBlank() || areaId.equals(s(t, "areaId"))))
        ids.put(s(t, "id"), s(t, "legacyId"));
    var state = new LinkedHashMap<String, Map<String, String>>();
    for (String key : List.of("assignments", "status", "dates", "notes"))
      state.put(key, new TreeMap<>());
    String prefix =
        month == null
            ? ""
            : "%04d-%02d".formatted(((Number) context.get("year")).intValue(), month);
    for (var p : planner.list(user, contextId, "planned_sessions")) {
      String legacy = ids.get(s(p, "topicId"));
      if (legacy == null) continue;
      if (month != null
          && !s(p, "assignment").equals(MonthlyPlan.MONTHS.get(month - 1))
          && !s(p, "exactDate").startsWith(prefix)) continue;
      for (var mapping :
          Map.of(
                  "assignments",
                  "assignment",
                  "status",
                  "status",
                  "dates",
                  "exactDate",
                  "notes",
                  "notes")
              .entrySet())
        if (p.get(mapping.getValue()) != null)
          state.get(mapping.getKey()).put(legacy, s(p, mapping.getValue()));
    }
    return Map.of(
        "version", 2, "year", context.get("year"), "grade", context.get("grade"), "state", state);
  }

  private static List<Map<String, Object>> selectAreas(List<Map<String, Object>> areas, String id) {
    if (id == null || id.isBlank()) return areas;
    var result = areas.stream().filter(a -> id.equals(s(a, "id"))).toList();
    if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Area not found");
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> school(Map<String, Object> me) {
    return (Map<String, Object>) me.get("school");
  }

  private static String s(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }

  private static void checkMonth(int month) {
    if (month < 1 || month > 12)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be 1-12");
  }
}
