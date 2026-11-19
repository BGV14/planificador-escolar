package pe.edu.planner.auth;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.planner.config.Bootstrap;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformBootstrapTest {
  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate db;

  private void run(String username) throws Exception {
    Object runner = context.getBean(Class.forName("pe.edu.planner.config.PlatformBootstrap"));
    ReflectionTestUtils.setField(runner, "username", username);
    try { ((ApplicationRunner) runner).run(new DefaultApplicationArguments()); }
    finally { ReflectionTestUtils.setField(runner, "username", ""); }
  }

  @Test
  void bootstrapIsOptInExactExistingAdminOnlyAndIdempotent() throws Exception {
    db.update("insert into schools(id,name,school_year,grade) values('boot-platform','Existing',2026,6)");
    db.update("insert into app_users(id,school_id,username,password_hash,role) values('boot-admin','boot-platform','ExistingAdmin','unchanged','ADMIN')");
    db.update("insert into app_users(id,school_id,username,password_hash,role) values('boot-teacher','boot-platform','ExistingTeacher','unchanged','TEACHER')");
    var before = db.queryForList("select * from app_users order by id");
    run("");
    assertThat(db.queryForObject("select count(*) from platform_administrators", Integer.class)).isZero();
    for (String invalid : new String[] {"missing", "ExistingTeacher", "existingadmin", " ExistingAdmin"}) {
      assertThatThrownBy(() -> run(invalid)).isInstanceOf(IllegalStateException.class).hasMessage("Superadministrator bootstrap requires an existing school administrator");
    }
    assertThat(db.queryForObject("select count(*) from platform_administrators", Integer.class)).isZero();
    run("ExistingAdmin");
    run("ExistingAdmin");
    assertThat(db.queryForList("select user_id from platform_administrators", String.class)).containsExactly("boot-admin");
    assertThat(db.queryForList("select * from app_users order by id")).isEqualTo(before);
  }

  @Test
  void schoolBootstrapRunsBeforePlatformBootstrap() throws Exception {
    var runners = new ArrayList<>(context.getBeansOfType(ApplicationRunner.class).values());
    AnnotationAwareOrderComparator.sort(runners);
    assertThat(runners.indexOf(context.getBean(Bootstrap.class))).isLessThan(runners.indexOf(context.getBean(Class.forName("pe.edu.planner.config.PlatformBootstrap"))));
  }
  @Test
  void concurrentBootstrapsSerializeOnTheExistingUser() throws Exception {
    var source = new com.zaxxer.hikari.HikariDataSource();
    source.setJdbcUrl(
        "jdbc:h2:mem:platform-concurrent-" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    source.setUsername("sa");
    source.setPassword("");
    org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
    var isolated = new JdbcTemplate(source);
    isolated.update("insert into schools(id,name,school_year,grade) values('concurrent','Existing',2026,6)");
    isolated.update("insert into app_users(id,school_id,username,password_hash,role) values('concurrent','concurrent','ConcurrentAdmin','old-hash','ADMIN')");
    var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
        var runner = new pe.edu.planner.config.PlatformBootstrap(isolated, tx);
        ReflectionTestUtils.setField(runner, "username", "ConcurrentAdmin");
        try { start.await(); runner.run(new DefaultApplicationArguments()); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
      }));
      start.countDown();
      for (var future : futures) future.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(isolated.queryForList("select user_id from platform_administrators", String.class)).containsExactly("concurrent");
    assertThat(isolated.queryForObject("select password_hash from app_users where id='concurrent'", String.class)).isEqualTo("old-hash");
    source.close();
  }}
