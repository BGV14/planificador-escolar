package pe.edu.planner.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.planner.planning.AcademicContext;

@Component
public class Bootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private final TransactionTemplate transactions;

  @Value("${planner.bootstrap.username:}")
  String username;

  @Value("${planner.bootstrap.password:}")
  String password;

  @Value("${planner.bootstrap.school:}")
  String school;

  @Value("${planner.bootstrap.year:2026}")
  int year;

  @Value("${planner.bootstrap.grade:6}")
  int grade;

  @Value("${planner.bootstrap.level:PRIMARY}")
  String level;

  public Bootstrap(JdbcTemplate db, PasswordEncoder passwords, TransactionTemplate transactions) {
    this.db = db;
    this.passwords = passwords;
    this.transactions = transactions;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank() && password.isBlank() && school.isBlank()) return;
    if (!username.matches("[A-Za-z0-9@._+-]{3,100}")
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || school.isBlank()
        || school.length() > 200)
      throw new IllegalStateException(
          "Provide valid bootstrap username, school and a 12+ character password (at most 72 UTF-8"
              + " bytes)");
    transactions.executeWithoutResult(
        status -> {
          if (db.queryForObject("select count(*) from schools", Integer.class) != 0) return;
          var context = new AcademicContext(year, level, grade);
          String schoolId = UUID.randomUUID().toString(), userId = UUID.randomUUID().toString();
          db.update(
              "insert into schools(id,name,school_year,grade,revision) values(?,?,?,?,0)",
              schoolId,
              school,
              year,
              Math.max(1, grade));
          db.update(
              "insert into"
                  + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
                  + " values(?,?,?,?,?,?,0,true)",
              schoolId,
              schoolId,
              year,
              level,
              context.stageKind(),
              grade);
          db.update(
              "insert into app_users(id,school_id,username,password_hash,role)"
                  + " values(?,?,?,?,'ADMIN')",
              userId,
              schoolId,
              username,
              passwords.encode(password));
          if (!level.equals("PRIMARY") || grade != 6) return;
          try (var stream = new ClassPathResource("catalog/legacy-grade6.json").getInputStream()) {
            var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            for (var course : data.properties()) {
              String areaId = UUID.randomUUID().toString();
              db.update(
                  "insert into areas(id,school_id,context_id,name,owner_id,shared_edit)"
                      + " values(?,?,?,?,?,false)",
                  areaId,
                  schoolId,
                  schoolId,
                  course.getKey(),
                  userId);
              for (var unit : course.getValue().properties())
                for (int i = 0; i < unit.getValue().size(); i++) {
                  db.update(
                      "insert into topics(id,school_id,context_id,area_id,name,unit,legacy_id)"
                          + " values(?,?,?,?,?,?,?)",
                      UUID.randomUUID().toString(),
                      schoolId,
                      schoolId,
                      areaId,
                      unit.getValue().get(i).asText(),
                      Integer.parseInt(unit.getKey()),
                      course.getKey() + "|" + unit.getKey() + "|" + i);
                }
            }
          } catch (java.io.IOException error) {
            throw new IllegalStateException("Bundled public catalog unavailable", error);
          }
        });
  }
}
