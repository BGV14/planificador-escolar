package pe.edu.planner.auth;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.planner.config.Bootstrap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BootstrapTest {
  @Autowired Bootstrap bootstrap;
  @Autowired JdbcTemplate db;

  @AfterEach
  void clearBootstrapCredentials() {
    ReflectionTestUtils.setField(bootstrap, "level", "PRIMARY");
    ReflectionTestUtils.setField(bootstrap, "grade", 6);
    ReflectionTestUtils.setField(bootstrap, "year", 2026);
    for (String field : List.of("username", "password", "school"))
      ReflectionTestUtils.setField(bootstrap, field, "");
  }

  @Test
  void bootstrapsOnlyEmptyDatabaseWithPublicCatalogAndNeverResetsPasswords() {
    clearDatabase();
    ReflectionTestUtils.setField(bootstrap, "username", "testbootstrap");
    ReflectionTestUtils.setField(bootstrap, "password", "synthetic-bootstrap-password");
    ReflectionTestUtils.setField(bootstrap, "school", "Synthetic school");
    bootstrap.run(new DefaultApplicationArguments());
    assertThat(db.queryForObject("select count(*) from areas", Integer.class)).isEqualTo(4);
    assertThat(db.queryForObject("select count(*) from topics", Integer.class)).isGreaterThan(100);
    assertThat(
            db.queryForObject(
                "select count(*) from topics where legacy_id='Aritmética|1|0' and name='Conjuntos'",
                Integer.class))
        .isEqualTo(1);
    String hash =
        db.queryForObject(
            "select password_hash from app_users where username='testbootstrap'", String.class);
    assertThat(hash).doesNotContain("synthetic");
    ReflectionTestUtils.setField(bootstrap, "password", "another-synthetic-password");
    bootstrap.run(new DefaultApplicationArguments());
    assertThat(db.queryForObject("select count(*) from schools", Integer.class)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select password_hash from app_users where username='testbootstrap'", String.class))
        .isEqualTo(hash);
  }

  void clearDatabase() {
    for (String table :
        List.of(
            "import_history",
            "planned_sessions",
            "calendar_blocks",
            "schedules",
            "topics",
            "areas",
            "app_users",
            "academic_contexts",
            "schools")) db.update("delete from " + table);
  }

  @Test
  void neverSeedsGradeSixCatalogIntoOtherStages() {
    clearDatabase();
    ReflectionTestUtils.setField(bootstrap, "username", "syntheticinitial");
    ReflectionTestUtils.setField(bootstrap, "password", "synthetic-long-password");
    ReflectionTestUtils.setField(bootstrap, "school", "Synthetic initial");
    ReflectionTestUtils.setField(bootstrap, "level", "INITIAL");
    ReflectionTestUtils.setField(bootstrap, "grade", 0);
    ReflectionTestUtils.setField(bootstrap, "year", 2035);
    bootstrap.run(new DefaultApplicationArguments());
    assertThat(db.queryForObject("select count(*) from topics", Integer.class)).isZero();
    assertThat(db.queryForMap("select * from academic_contexts"))
        .containsEntry("level", "INITIAL")
        .containsEntry("stage_value", 0)
        .containsEntry("school_year", 2035);
  }
}
