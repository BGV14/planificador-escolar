package pe.edu.planner.platform;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;

/** Global capability is read from the database, never from a cached session authority. */
@Component
public class PlatformAccessPolicy {
  private final JdbcTemplate db;

  public PlatformAccessPolicy(JdbcTemplate db) {
    this.db = db;
  }

  public boolean allowed(PlannerPrincipal user) {
    return user != null && user.admin() && !grants(user, false).isEmpty();
  }

  public void require(PlannerPrincipal user) {
    require(user, false);
  }

  /** Mutating callers hold the grant lock until their surrounding transaction completes. */
  public void require(PlannerPrincipal user, boolean lock) {
    if (lock && !TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Platform mutation requires a transaction");
    if (user == null || !user.admin() || grants(user, lock).isEmpty())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Global administration required");
  }

  private java.util.List<String> grants(PlannerPrincipal user, boolean lock) {
    return db.queryForList(
        "select p.user_id from platform_administrators p join app_users u"
            + " on u.id=p.user_id and u.role=p.user_role"
            + " where u.id=? and u.school_id=? and u.role='ADMIN'"
            + (lock ? " for update" : ""),
        String.class, user.id(), user.schoolId());
  }
}
