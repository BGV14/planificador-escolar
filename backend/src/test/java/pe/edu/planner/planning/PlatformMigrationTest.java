package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.planner.auth.PlannerPrincipal;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformMigrationTest {
  @Autowired JdbcTemplate db;
  @Autowired PlannerService planner;

  private PlannerPrincipal account(String id, String role) {
    db.update("insert into schools(id,name,school_year,grade) values(?,?,2026,6)", id, id);
    db.update("insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,'preserved-hash',?)", id, id, id, role);
    return new PlannerPrincipal(id, id, id, "preserved-hash", role);
  }

  @Test
  void grantAndRevocationAreVisibleToTheSamePrincipalWithoutIdentityChanges() {
    var user = account("platform-admin", "ADMIN");
    db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value) values(?,?,2026,'PRIMARY','GRADE',6)", user.id(), user.id());
    db.update("insert into areas(id,school_id,context_id,name,owner_id) values('platform-area',?,?, 'Preserved',?)", user.id(), user.id(), user.id());
    var before = db.queryForMap("select * from app_users where id=?", user.id());
    assertThat(planner.me(user)).containsEntry("superadmin", false);
    db.update("insert into platform_administrators(user_id) values(?)", user.id());
    assertThat(planner.me(user)).containsEntry("superadmin", true).containsEntry("role", "ADMIN").containsEntry("schoolId", user.schoolId());
    assertThat(db.queryForMap("select * from app_users where id=?", user.id())).isEqualTo(before);
    assertThat(db.queryForObject("select owner_id from areas where id='platform-area'", String.class)).isEqualTo(user.id());
    db.update("delete from platform_administrators where user_id=?", user.id());
    assertThat(planner.me(user)).containsEntry("superadmin", false);
  }

  @Test
  void databaseRejectsTeacherMissingDuplicateAndRoleChangingGrants() {
    var teacher = account("platform-teacher", "TEACHER");
    assertThatThrownBy(() -> db.update("insert into platform_administrators(user_id) values(?)", teacher.id())).isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> db.update("insert into platform_administrators(user_id,user_role) values(?,'TEACHER')", teacher.id())).isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> db.update("insert into platform_administrators(user_id) values('absent')")).isInstanceOf(DataIntegrityViolationException.class);
    var admin = account("platform-granted", "ADMIN");
    db.update("insert into platform_administrators(user_id) values(?)", admin.id());
    assertThatThrownBy(() -> db.update("insert into platform_administrators(user_id) values(?)", admin.id())).isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> db.update("update app_users set role='TEACHER' where id=?", admin.id())).isInstanceOf(DataIntegrityViolationException.class);
    var stale = new PlannerPrincipal(teacher.id(), teacher.schoolId(), teacher.getUsername(), "unused", "ADMIN");
    assertThat(planner.me(stale)).containsEntry("superadmin", false);
  }
  @Test
  void guardFailsClosedAndLockingRequiresAnActiveTransaction() {
    var user = account("platform-guard", "ADMIN");
    var policy = new pe.edu.planner.platform.PlatformAccessPolicy(db);
    assertThat(policy.allowed(null)).isFalse();
    assertThatThrownBy(() -> policy.require(user)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    db.update("insert into platform_administrators(user_id) values(?)", user.id());
    policy.require(user, true);
    var wrongSchool = new PlannerPrincipal(user.id(), "another-school", user.getUsername(), "unused", "ADMIN");
    assertThat(policy.allowed(wrongSchool)).isFalse();
    var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource()));
    tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    tx.executeWithoutResult(status -> assertThatThrownBy(() -> policy.require(user, true)).isInstanceOf(IllegalStateException.class));
    db.update("delete from platform_administrators where user_id=?", user.id());
    assertThatThrownBy(() -> policy.require(user, true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void upgradesVersionFiveWithoutChangingExistingOwnershipOrCredentials() {
    var source = new com.zaxxer.hikari.HikariDataSource();
    source.setJdbcUrl(
        "jdbc:h2:mem:platform-upgrade-" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    source.setUsername("sa");
    source.setPassword("");
    var old = org.flywaydb.core.Flyway.configure().dataSource(source).target("5").load();
    old.migrate();
    var isolated = new JdbcTemplate(source);
    isolated.update("insert into schools(id,name,school_year,grade) values('old','Unchanged',2026,6)");
    isolated.update("insert into app_users(id,school_id,username,password_hash,role) values('old','old','OldAdmin','old-hash','ADMIN')");
    isolated.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value) values('old','old',2026,'PRIMARY','GRADE',6)");
    isolated.update("insert into areas(id,school_id,context_id,name,owner_id) values('old','old','old','Old area','old')");
    var before = isolated.queryForMap("select * from app_users where id='old'");
    org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
    assertThat(isolated.queryForMap("select * from app_users where id='old'")).isEqualTo(before);
    assertThat(isolated.queryForObject("select owner_id from areas where id='old'", String.class)).isEqualTo("old");
    assertThat(isolated.queryForObject("select count(*) from platform_administrators", Integer.class)).isZero();
    assertThatThrownBy(() -> isolated.update("update areas set owner_id='absent' where id='old'")).isInstanceOf(DataIntegrityViolationException.class);
    source.close();
  }}
