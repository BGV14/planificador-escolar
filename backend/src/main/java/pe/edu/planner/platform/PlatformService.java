package pe.edu.planner.platform;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.planning.AcademicContext;

@Service
@Transactional
public class PlatformService {
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private final PlatformAccessPolicy access;

  public PlatformService(JdbcTemplate db, PasswordEncoder passwords, PlatformAccessPolicy access) {
    this.db = db;
    this.passwords = passwords;
    this.access = access;
  }

  public record SchoolInput(String name, int year, String level, int stageValue, String section,
      String adminUsername, String adminPassword) {}
  public record AdministratorInput(String username, String password) {}
  public record AdministratorView(String id, String schoolId, String username, String role) {}
  public record SchoolView(String id, String name, int year, int grade,
      List<AdministratorView> administrators) {}

  @Transactional(readOnly = true)
  public List<SchoolView> schools(PlannerPrincipal user) {
    access.require(user);
    return db.queryForList("select id,name,school_year,grade from schools order by name,id")
        .stream().map(this::view).toList();
  }

  public SchoolView createSchool(PlannerPrincipal user, SchoolInput input) {
    access.require(user, true);
    String name = text(input.name(), 200);
    var context = new AcademicContext(input.year(), input.level(), input.stageValue(), input.section());
    String username = validateAdministrator(input.adminUsername(), input.adminPassword());
    String id = UUID.randomUUID().toString();
    db.update("insert into schools(id,name,school_year,grade,revision) values(?,?,?,?,0)",
        id, name, context.year(), Math.max(1, context.stageValue()));
    db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,section,revision,legacy_default) values(?,?,?,?,?,?,?,0,true)",
        id, id, context.year(), context.level(), context.stageKind(), context.stageValue(), context.section());
    insertAdministrator(id, username, input.adminPassword());
    return view(db.queryForMap("select id,name,school_year,grade from schools where id=?", id));
  }

  public AdministratorView createAdministrator(PlannerPrincipal user, String schoolId, AdministratorInput input) {
    access.require(user, true);
    // Check the global grant before looking up a foreign school, including nonexistent IDs.
    var schools = db.queryForList("select id from schools where id=? for update", schoolId);
    if (schools.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found");
    String username = validateAdministrator(input.username(), input.password());
    return insertAdministrator(schoolId, username, input.password());
  }

  private AdministratorView insertAdministrator(String schoolId, String username, String password) {
    String id = UUID.randomUUID().toString();
    db.update("insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,'ADMIN')",
        id, schoolId, username, passwords.encode(password));
    return new AdministratorView(id, schoolId, username, "ADMIN");
  }

  private SchoolView view(Map<String, Object> school) {
    String id = school.get("id").toString();
    var admins = db.query("select id,school_id,username,role from app_users where school_id=? and role='ADMIN' order by username,id",
        (rs, n) -> new AdministratorView(rs.getString("id"), rs.getString("school_id"), rs.getString("username"), rs.getString("role")), id);
    return new SchoolView(id, school.get("name").toString(), ((Number) school.get("school_year")).intValue(),
        ((Number) school.get("grade")).intValue(), admins);
  }

  private String validateAdministrator(String username, String password) {
    String normalized = text(username, 100);
    if (!normalized.matches("[A-Za-z0-9@._+-]{3,100}") || password == null
        || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user or password (12+ characters, at most 72 UTF-8 bytes)");
    return normalized;
  }

  private String text(String value, int max) {
    if (value == null || value.isBlank() || value.trim().length() > max)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid text");
    return value.trim();
  }
}
