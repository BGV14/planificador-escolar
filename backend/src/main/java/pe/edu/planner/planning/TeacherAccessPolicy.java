package pe.edu.planner.planning;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.planner.auth.PlannerPrincipal;

/** Database-backed read policy: assignments are deliberately not cached in login sessions. */
@Component
public class TeacherAccessPolicy {
  private final JdbcTemplate db;

  public TeacherAccessPolicy(JdbcTemplate db) {
    this.db = db;
  }

  public boolean tutor(PlannerPrincipal user, String contextId) {
    return db.queryForObject(
            "select count(*) from classroom_tutors where school_id=? and context_id=? and user_id=?",
            Integer.class, user.schoolId(), contextId, user.id()) > 0;
  }

  public Set<String> visibleAreaIds(PlannerPrincipal user, String contextId) {
    boolean all = user.admin() || tutor(user, contextId);
    return new HashSet<>(db.queryForList(
        "select id from areas where school_id=? and context_id=?"
            + (all ? "" : " and (owner_id=? or shared_edit=true)"),
        String.class,
        all ? new Object[] {user.schoolId(), contextId}
            : new Object[] {user.schoolId(), contextId, user.id()}));
  }

  public boolean canReadContext(PlannerPrincipal user, String contextId) {
    if (db.queryForObject("select count(*) from academic_contexts where school_id=? and id=?",
        Integer.class, user.schoolId(), contextId) != 1) return false;
    return user.admin() || tutor(user, contextId) || !visibleAreaIds(user, contextId).isEmpty();
  }
}
