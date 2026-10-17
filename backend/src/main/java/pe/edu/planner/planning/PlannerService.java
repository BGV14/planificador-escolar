package pe.edu.planner.planning;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.legacy.*;

@Service
@Transactional
public class PlannerService {
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private static final Set<String> TABLES =
      Set.of(
          "areas", "topics", "schedules", "planned_sessions", "calendar_blocks", "import_history");

  public PlannerService(JdbcTemplate db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  public record AreaInput(String name, String ownerId, boolean sharedEdit) {}

  public record TopicInput(String areaId, String name, int unit, String legacyId) {}

  public record ScheduleInput(String areaId, int weekday, String startTime, String endTime) {}

  public record SessionInput(
      String topicId, Long version, String assignment, String status, String date, String notes) {}

  public record BlockInput(
      String areaId, String label, String kind, LocalDate startDate, LocalDate endDate) {}

  public record UserInput(String username, String password, String role) {}

  public record ImportInput(String json, Long revision, Boolean acknowledgeIncomingWins) {}

  public Map<String, Object> me(PlannerPrincipal user) {
    return Map.of(
        "id",
        user.id(),
        "schoolId",
        user.schoolId(),
        "username",
        user.getUsername(),
        "role",
        user.role(),
        "school",
        school(user, false));
  }

  public List<Map<String, Object>> list(PlannerPrincipal user, String contextId, String table) {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("Unknown resource");
    context(user, contextId, false);
    return db
        .queryForList(
            "select * from " + table + " where school_id=? and context_id=? order by id",
            user.schoolId(),
            contextId)
        .stream()
        .map(this::view)
        .toList();
  }

  public List<Map<String, Object>> users(PlannerPrincipal user) {
    admin(user);
    return db.queryForList(
        "select id,username,role from app_users where school_id=? order by username",
        user.schoolId());
  }

  public Map<String, Object> createUser(PlannerPrincipal user, UserInput input) {
    admin(user);
    school(user, true);
    String username = text(input.username(), 100);
    if (!username.matches("[A-Za-z0-9@._+-]{3,100}")
        || input.password() == null
        || input.password().length() < 12
        || input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !Set.of("ADMIN", "TEACHER").contains(Objects.toString(input.role(), "")))
      bad("Invalid user or password (12+ characters, at most 72 UTF-8 bytes)");
    String id = id();
    db.update(
        "insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)",
        id,
        user.schoolId(),
        username,
        passwords.encode(input.password()),
        input.role());
    return Map.of("id", id, "username", username, "role", input.role());
  }

  public Map<String, Object> area(
      PlannerPrincipal user, String contextId, String id, AreaInput input) {
    admin(user);
    context(user, contextId, true);
    if (id != null) row(user, contextId, "areas", id);
    if (db.queryForObject(
            "select count(*) from app_users where school_id=? and id=?",
            Integer.class,
            user.schoolId(),
            input.ownerId())
        != 1) missing();
    String name = text(input.name(), 200);
    if (id == null) {
      id = id();
      db.update(
          "insert into areas(id,school_id,context_id,name,owner_id,shared_edit)"
              + " values(?,?,?,?,?,?)",
          id,
          user.schoolId(),
          contextId,
          name,
          input.ownerId(),
          input.sharedEdit());
    } else
      db.update(
          "update areas set name=?,owner_id=?,shared_edit=? where school_id=? and context_id=? and"
              + " id=?",
          name,
          input.ownerId(),
          input.sharedEdit(),
          user.schoolId(),
          contextId,
          id);
    bump(user, contextId);
    return view(row(user, contextId, "areas", id));
  }

  public Map<String, Object> topic(
      PlannerPrincipal user, String contextId, String id, TopicInput input) {
    context(user, contextId, true);
    if (id != null)
      editArea(user, contextId, row(user, contextId, "topics", id).get("area_id").toString());
    editArea(user, contextId, input.areaId());
    String name = text(input.name(), 500);
    if (input.unit() < 1 || input.unit() > 99) bad("Unit must be 1–99");
    if (input.legacyId() != null
        && (!Pattern.matches("[^|]+\\|[1-9][0-9]*\\|(?:0|[1-9][0-9]*)", input.legacyId())
            || input.legacyId().length() > 300)) bad("Invalid legacy ID");
    if (id == null) {
      id = id();
      db.update(
          "insert into topics(id,school_id,context_id,area_id,name,unit,legacy_id)"
              + " values(?,?,?,?,?,?,?)",
          id,
          user.schoolId(),
          contextId,
          input.areaId(),
          name,
          input.unit(),
          input.legacyId());
    } else {
      var previous = row(user, contextId, "topics", id).get("legacy_id");
      if (!Objects.equals(previous, input.legacyId())) conflict("Legacy IDs are immutable");
      db.update(
          "update topics set area_id=?,name=?,unit=? where school_id=? and context_id=? and id=?",
          input.areaId(),
          name,
          input.unit(),
          user.schoolId(),
          contextId,
          id);
    }
    bump(user, contextId);
    return view(row(user, contextId, "topics", id));
  }

  public Map<String, Object> schedule(
      PlannerPrincipal user, String contextId, String id, ScheduleInput input) {
    context(user, contextId, true);
    if (id != null)
      editArea(user, contextId, row(user, contextId, "schedules", id).get("area_id").toString());
    editArea(user, contextId, input.areaId());
    try {
      if (input.weekday() < 1
          || input.weekday() > 7
          || !input.startTime().matches("\\d{2}:\\d{2}")
          || !input.endTime().matches("\\d{2}:\\d{2}")
          || !LocalTime.parse(input.startTime()).isBefore(LocalTime.parse(input.endTime())))
        bad("Invalid schedule");
    } catch (RuntimeException error) {
      bad("Invalid schedule");
    }
    if (id == null) {
      id = id();
      db.update(
          "insert into schedules(id,school_id,context_id,area_id,weekday,start_time,end_time)"
              + " values(?,?,?,?,?,?,?)",
          id,
          user.schoolId(),
          contextId,
          input.areaId(),
          input.weekday(),
          input.startTime(),
          input.endTime());
    } else
      db.update(
          "update schedules set area_id=?,weekday=?,start_time=?,end_time=? where school_id=? and"
              + " context_id=? and id=?",
          input.areaId(),
          input.weekday(),
          input.startTime(),
          input.endTime(),
          user.schoolId(),
          contextId,
          id);
    bump(user, contextId);
    return view(row(user, contextId, "schedules", id));
  }

  public Map<String, Object> session(
      PlannerPrincipal user, String contextId, String id, SessionInput input) {
    var school = context(user, contextId, true);
    if (id != null) {
      var existing = row(user, contextId, "planned_sessions", id);
      editTopic(user, contextId, existing.get("topic_id").toString());
      if (!existing.get("topic_id").equals(input.topicId()))
        conflict("A session cannot change topic");
      if (input.version() == null
          || ((Number) existing.get("version")).longValue() != input.version())
        conflict("STALE_SESSION");
    }
    var topic = editTopic(user, contextId, input.topicId());
    validateFields(school, input.assignment(), input.status(), input.date(), input.notes());
    checkBlock(user, contextId, topic.get("area_id").toString(), input.date());
    String assignment = "Septiembre".equals(input.assignment()) ? "Setiembre" : input.assignment();
    if (id == null) {
      id = id();
      db.update(
          "insert into"
              + " planned_sessions(id,school_id,context_id,topic_id,assignment,status,exact_date,notes,version)"
              + " values(?,?,?,?,?,?,?,?,0)",
          id,
          user.schoolId(),
          contextId,
          input.topicId(),
          assignment,
          input.status(),
          input.date(),
          input.notes());
    } else
      db.update(
          "update planned_sessions set assignment=?,status=?,exact_date=?,notes=?,version=version+1"
              + " where school_id=? and context_id=? and id=?",
          assignment,
          input.status(),
          input.date(),
          input.notes(),
          user.schoolId(),
          contextId,
          id);
    bump(user, contextId);
    return view(row(user, contextId, "planned_sessions", id));
  }

  public Map<String, Object> block(
      PlannerPrincipal user, String contextId, String id, BlockInput input) {
    var school = context(user, contextId, true);
    if (id != null) editBlock(user, contextId, row(user, contextId, "calendar_blocks", id));
    if (input.areaId() == null) admin(user);
    else editArea(user, contextId, input.areaId());
    String label = text(input.label(), 200);
    if (!Set.of("EXAM", "VACATION", "HOLIDAY", "OTHER").contains(Objects.toString(input.kind(), ""))
        || input.startDate() == null
        || input.endDate() == null
        || input.startDate().isAfter(input.endDate())
        || input.startDate().getYear() != year(school)
        || input.endDate().getYear() != year(school)) bad("Invalid calendar block range or kind");
    if (id == null) {
      id = id();
      db.update(
          "insert into"
              + " calendar_blocks(id,school_id,context_id,area_id,label,kind,start_date,end_date)"
              + " values(?,?,?,?,?,?,?,?)",
          id,
          user.schoolId(),
          contextId,
          input.areaId(),
          label,
          input.kind(),
          input.startDate(),
          input.endDate());
    } else
      db.update(
          "update calendar_blocks set area_id=?,label=?,kind=?,start_date=?,end_date=? where"
              + " school_id=? and context_id=? and id=?",
          input.areaId(),
          label,
          input.kind(),
          input.startDate(),
          input.endDate(),
          user.schoolId(),
          contextId,
          id);
    bump(user, contextId);
    var result = new LinkedHashMap<>(view(row(user, contextId, "calendar_blocks", id)));
    result.put("warnings", blockWarnings(user, contextId));
    return result;
  }

  public void delete(
      PlannerPrincipal user, String contextId, String table, String id, Long version) {
    if (!TABLES.contains(table) || table.equals("import_history")) bad("Invalid delete");
    context(user, contextId, true);
    var existing = row(user, contextId, table, id);
    switch (table) {
      case "areas" -> admin(user);
      case "topics", "schedules" -> editArea(user, contextId, existing.get("area_id").toString());
      case "calendar_blocks" -> editBlock(user, contextId, existing);
      case "planned_sessions" -> {
        editTopic(user, contextId, existing.get("topic_id").toString());
        if (version == null || ((Number) existing.get("version")).longValue() != version)
          conflict("STALE_SESSION");
      }
      default -> bad("Invalid delete");
    }
    db.update(
        "delete from " + table + " where school_id=? and context_id=? and id=?",
        user.schoolId(),
        contextId,
        id);
    bump(user, contextId);
  }

  public Map<String, Object> plan(PlannerPrincipal user, String contextId) {
    var school = context(user, contextId, true);
    var result = contextView(school);
    result.put("state", state(user, contextId));
    result.put("warnings", blockWarnings(user, contextId));
    return result;
  }

  public Map<String, Object> preview(PlannerPrincipal user, String contextId, ImportInput input) {
    var school = context(user, contextId, true);
    var result = importer(user, contextId, school).preview(input.json(), state(user, contextId));
    authorizeImport(user, contextId, input.json());
    return Map.of(
        "revision",
        school.get("revision"),
        "result",
        result,
        "notice",
        "Incoming fields replace matching existing fields. Confirm explicitly; omitted fields"
            + " remain unchanged.");
  }

  public Map<String, Object> confirm(PlannerPrincipal user, String contextId, ImportInput input) {
    var school = context(user, contextId, true);
    if (input.revision() == null
        || ((Number) school.get("revision")).longValue() != input.revision())
      conflict("STALE_PREVIEW");
    if (!Boolean.TRUE.equals(input.acknowledgeIncomingWins()))
      bad("Explicit incoming-wins acknowledgment required");
    // Recompute against the locked current plan; never trust client preview state.
    var result = importer(user, contextId, school).preview(input.json(), state(user, contextId));
    Set<String> incoming = authorizeImport(user, contextId, input.json());
    for (var topic :
        db.queryForList(
            "select * from topics where school_id=? and context_id=? and legacy_id is not null",
            user.schoolId(),
            contextId)) {
      String legacy = topic.get("legacy_id").toString();
      if (!incoming.contains(legacy)) continue;
      var state = result.state();
      String date = state.dates().get(legacy);
      checkBlock(user, contextId, topic.get("area_id").toString(), date);
      var rows =
          db.queryForList(
              "select * from planned_sessions where school_id=? and context_id=? and topic_id=?",
              user.schoolId(),
              contextId,
              topic.get("id"));
      if (rows.isEmpty())
        db.update(
            "insert into"
                + " planned_sessions(id,school_id,context_id,topic_id,assignment,status,exact_date,notes,version)"
                + " values(?,?,?,?,?,?,?,?,0)",
            id(),
            user.schoolId(),
            contextId,
            topic.get("id"),
            state.assignments().get(legacy),
            state.status().get(legacy),
            date,
            state.notes().get(legacy));
      else
        db.update(
            "update planned_sessions set"
                + " assignment=?,status=?,exact_date=?,notes=?,version=version+1 where school_id=?"
                + " and context_id=? and topic_id=?",
            state.assignments().get(legacy),
            state.status().get(legacy),
            date,
            state.notes().get(legacy),
            user.schoolId(),
            contextId,
            topic.get("id"));
    }
    bump(user, contextId);
    long revision = input.revision() + 1;
    db.update(
        "insert into"
            + " import_history(id,school_id,context_id,user_id,imported_at,affected_topics,revision)"
            + " values(?,?,?,?,?,?,?)",
        id(),
        user.schoolId(),
        contextId,
        user.id(),
        OffsetDateTime.now(ZoneOffset.UTC),
        result.preview().affectedTopics(),
        revision);
    return Map.of("revision", revision, "result", result);
  }

  private Set<String> authorizeImport(PlannerPrincipal user, String contextId, String json) {
    try {
      // Parsing is safe here only after the bounded strict pure importer has accepted it.
      var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
      if (root.has("state")) root = root.get("state");
      var ids = new HashSet<String>();
      root.properties().forEach(field -> field.getValue().fieldNames().forEachRemaining(ids::add));
      for (String legacy : ids) {
        var topics =
            db.queryForList(
                "select * from topics where school_id=? and context_id=? and legacy_id=?",
                user.schoolId(),
                contextId,
                legacy);
        if (topics.isEmpty()) missing();
        editArea(user, contextId, topics.getFirst().get("area_id").toString());
      }
      return ids;
    } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
      throw new IllegalArgumentException("Invalid import");
    }
  }

  private LegacyPlanImporter importer(
      PlannerPrincipal user, String contextId, Map<String, Object> school) {
    if (!"PRIMARY".equals(school.get("level"))
        || ((Number) school.get("stage_value")).intValue() != 6)
      bad("Legacy imports require PRIMARY grade 6 context");
    var ids =
        Set.copyOf(
            db.queryForList(
                "select legacy_id from topics where school_id=? and context_id=? and legacy_id is"
                    + " not null",
                String.class,
                user.schoolId(),
                contextId));
    return new LegacyPlanImporter(
        ids, year(school), ((Number) school.get("stage_value")).intValue());
  }

  private LegacyPlanState state(PlannerPrincipal user, String contextId) {
    var maps =
        List.of(
            new HashMap<String, String>(),
            new HashMap<String, String>(),
            new HashMap<String, String>(),
            new HashMap<String, String>());
    for (var row :
        db.queryForList(
            "select t.legacy_id,p.assignment,p.status,p.exact_date,p.notes from planned_sessions p"
                + " join topics t on p.topic_id=t.id and p.school_id=t.school_id and"
                + " p.context_id=t.context_id where p.school_id=? and p.context_id=? and"
                + " t.legacy_id is not null",
            user.schoolId(),
            contextId)) {
      var columns = List.of("assignment", "status", "exact_date", "notes");
      for (int i = 0; i < columns.size(); i++)
        if (row.get(columns.get(i)) != null)
          maps.get(i).put(row.get("legacy_id").toString(), row.get(columns.get(i)).toString());
    }
    return new LegacyPlanState(maps.get(0), maps.get(1), maps.get(2), maps.get(3));
  }

  private void validateFields(
      Map<String, Object> school, String assignment, String status, String date, String notes) {
    if (assignment != null
        && !Set.of(
                "",
                "Marzo",
                "Abril",
                "Mayo",
                "Junio",
                "Julio",
                "Agosto",
                "Setiembre",
                "Septiembre",
                "Octubre",
                "Noviembre",
                "Diciembre")
            .contains(assignment)) bad("Invalid assignment");
    if (status != null && !Set.of("", "Trabajado").contains(status)) bad("Invalid status");
    if (notes != null && notes.length() > 100_000) bad("Notes exceed allowed size");
    if (date != null && !date.isEmpty()) {
      try {
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
            || LocalDate.parse(date).getYear() != year(school)) bad("Invalid session date");
      } catch (java.time.format.DateTimeParseException error) {
        bad("Invalid session date");
      }
    }
  }

  private void checkBlock(PlannerPrincipal user, String contextId, String areaId, String date) {
    if (date == null || date.isEmpty()) return;
    if (db.queryForObject(
            "select count(*) from calendar_blocks where school_id=? and context_id=? and (area_id"
                + " is null or area_id=?) and start_date<=? and end_date>=?",
            Integer.class,
            user.schoolId(),
            contextId,
            areaId,
            LocalDate.parse(date),
            LocalDate.parse(date))
        > 0) conflict("BLOCKED_DATE: exact date was not changed");
  }

  private List<Map<String, Object>> blockWarnings(PlannerPrincipal user, String contextId) {
    return db
        .queryForList(
            "select p.id,p.exact_date,b.id as block_id,b.label from planned_sessions p join topics"
                + " t on p.school_id=t.school_id and p.context_id=t.context_id and p.topic_id=t.id"
                + " join calendar_blocks b on b.school_id=p.school_id and b.context_id=p.context_id"
                + " and (b.area_id is null or b.area_id=t.area_id) where p.school_id=? and"
                + " p.context_id=? and p.exact_date is not null and p.exact_date<>'' and"
                + " p.exact_date>=cast(b.start_date as varchar) and p.exact_date<=cast(b.end_date"
                + " as varchar)",
            user.schoolId(),
            contextId)
        .stream()
        .map(this::view)
        .toList();
  }

  private Map<String, Object> editTopic(PlannerPrincipal user, String contextId, String topicId) {
    var topic = row(user, contextId, "topics", topicId);
    editArea(user, contextId, topic.get("area_id").toString());
    return topic;
  }

  private void editBlock(PlannerPrincipal user, String contextId, Map<String, Object> block) {
    if (block.get("area_id") == null) admin(user);
    else editArea(user, contextId, block.get("area_id").toString());
  }

  private void editArea(PlannerPrincipal user, String contextId, String areaId) {
    var area = row(user, contextId, "areas", areaId);
    if (!user.admin()
        && !area.get("owner_id").equals(user.id())
        && !Boolean.TRUE.equals(area.get("shared_edit"))) forbidden();
  }

  private Map<String, Object> row(
      PlannerPrincipal user, String contextId, String table, String id) {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("Unknown resource");
    context(user, contextId, false);
    var rows =
        db.queryForList(
            "select * from " + table + " where school_id=? and context_id=? and id=?",
            user.schoolId(),
            contextId,
            id);
    if (rows.isEmpty()) missing();
    return rows.getFirst();
  }

  private Map<String, Object> school(PlannerPrincipal user, boolean lock) {
    var rows =
        db.queryForList(
            "select * from schools where id=?" + (lock ? " for update" : ""), user.schoolId());
    if (rows.isEmpty()) missing();
    return rows.getFirst();
  }

  private void bump(PlannerPrincipal user, String contextId) {
    db.update(
        "update academic_contexts set revision=revision+1 where school_id=? and id=?",
        user.schoolId(),
        contextId);
  }

  private int year(Map<String, Object> school) {
    return ((Number) school.get("school_year")).intValue();
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          StringBuilder name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toCharArray()) {
            if (c == '_') upper = true;
            else {
              name.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
          }
          result.put(
              name.toString(),
              value instanceof java.sql.Date d ? d.toLocalDate().toString() : value);
        });
    return result;
  }

  /** Transitional compatibility: always the immutable migrated/bootstrap default, never newest. */
  public String legacyContextId(PlannerPrincipal user) {
    var rows =
        db.queryForList(
            "select id from academic_contexts where school_id=? and legacy_default=true",
            user.schoolId());
    if (rows.size() != 1) missing();
    return rows.getFirst().get("id").toString();
  }

  public record ContextInput(int year, String level, int stageValue) {}

  public List<Map<String, Object>> contexts(PlannerPrincipal user) {
    return db
        .queryForList(
            "select * from academic_contexts where school_id=? order by"
                + " school_year,level,stage_value",
            user.schoolId())
        .stream()
        .map(this::contextView)
        .toList();
  }

  public Map<String, Object> createContext(PlannerPrincipal user, ContextInput input) {
    admin(user);
    school(user, true);
    var spec = new AcademicContext(input.year(), input.level(), input.stageValue());
    String id = id();
    db.update(
        "insert into"
            + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
            + " values(?,?,?,?,?,?,0,false)",
        id,
        user.schoolId(),
        spec.year(),
        spec.level(),
        spec.stageKind(),
        spec.stageValue());
    return contextView(context(user, id, false));
  }

  private Map<String, Object> context(PlannerPrincipal user, String contextId, boolean lock) {
    if (contextId == null || contextId.isBlank()) bad("Explicit contextId required");
    var rows =
        db.queryForList(
            "select * from academic_contexts where school_id=? and id=?"
                + (lock ? " for update" : ""),
            user.schoolId(),
            contextId);
    if (rows.isEmpty()) missing();
    return rows.getFirst();
  }

  private Map<String, Object> contextView(Map<String, Object> row) {
    var result = new LinkedHashMap<>(view(row));
    var spec =
        new AcademicContext(
            year(row), row.get("level").toString(), ((Number) row.get("stage_value")).intValue());
    result.remove("schoolYear");
    result.put("year", spec.year());
    result.put("displayLabel", spec.displayLabel());
    result.put("cycle", spec.cycle());
    result.put("grade", spec.stageKind().equals("GRADE") ? spec.stageValue() : null);
    return result;
  }

  public List<Map<String, Object>> list(PlannerPrincipal user, String table) {
    return list(user, legacyContextId(user), table);
  }

  public Map<String, Object> area(PlannerPrincipal user, String id, AreaInput input) {
    return area(user, legacyContextId(user), id, input);
  }

  public Map<String, Object> topic(PlannerPrincipal user, String id, TopicInput input) {
    return topic(user, legacyContextId(user), id, input);
  }

  public Map<String, Object> schedule(PlannerPrincipal user, String id, ScheduleInput input) {
    return schedule(user, legacyContextId(user), id, input);
  }

  public Map<String, Object> session(PlannerPrincipal user, String id, SessionInput input) {
    return session(user, legacyContextId(user), id, input);
  }

  public Map<String, Object> block(PlannerPrincipal user, String id, BlockInput input) {
    return block(user, legacyContextId(user), id, input);
  }

  public void delete(PlannerPrincipal user, String table, String id, Long version) {
    delete(user, legacyContextId(user), table, id, version);
  }

  public Map<String, Object> plan(PlannerPrincipal user) {
    return plan(user, legacyContextId(user));
  }

  public Map<String, Object> preview(PlannerPrincipal user, ImportInput input) {
    return preview(user, legacyContextId(user), input);
  }

  public Map<String, Object> confirm(PlannerPrincipal user, ImportInput input) {
    return confirm(user, legacyContextId(user), input);
  }

  private static String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      bad("Required text exceeds allowed size or is blank");
    return value.strip();
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private static void admin(PlannerPrincipal user) {
    if (!user.admin()) forbidden();
  }

  private static void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static void conflict(String message) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private static void forbidden() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted");
  }

  private static void missing() {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
  }
}
