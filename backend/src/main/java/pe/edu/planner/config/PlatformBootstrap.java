package pe.edu.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in grant to an existing administrator; never creates or alters login credentials. */
@Component
@Order(20)
public class PlatformBootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  private final TransactionTemplate transactions;

  @Value("${planner.bootstrap.superadmin-username:}")
  String username;

  public PlatformBootstrap(JdbcTemplate db, TransactionTemplate transactions) {
    this.db = db;
    this.transactions = transactions;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank()) return;
    transactions.executeWithoutResult(status -> {
      var users = db.queryForList("select id,role from app_users where username=? for update", username);
      if (users.size() != 1 || !"ADMIN".equals(users.getFirst().get("role")))
        throw new IllegalStateException("Superadministrator bootstrap requires an existing school administrator");
      String id = users.getFirst().get("id").toString();
      if (db.queryForObject("select count(*) from platform_administrators where user_id=?", Integer.class, id) == 0)
        db.update("insert into platform_administrators(user_id) values(?)", id);
    });
  }
}
