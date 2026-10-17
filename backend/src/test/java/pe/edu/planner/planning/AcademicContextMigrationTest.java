package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AcademicContextMigrationTest {
  JdbcTemplate db;
  String url;
  SingleConnectionDataSource source;

  void oldDatabase(int grade) {
    url =
        "jdbc:h2:mem:"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    source = new SingleConnectionDataSource(url, "sa", "", true);
    Flyway.configure().dataSource(source).target("2").load().migrate();
    db = new JdbcTemplate(source);
    db.update(
        "insert into schools(id,name,school_year,grade,revision)"
            + " values('school','Synthetic',2027,?,17)",
        grade);
    db.update(
        "insert into app_users(id,school_id,username,password_hash,role)"
            + " values('teacher','school','synthetic','hash','ADMIN')");
    db.update(
        "insert into areas(id,school_id,name,owner_id,shared_edit)"
            + " values('area','school','Math','teacher',true)");
    db.update(
        "insert into topics(id,school_id,area_id,name,unit,legacy_id)"
            + " values('topic','school','area','Sets',1,'Math|1|0')");
    db.update(
        "insert into"
            + " planned_sessions(id,school_id,topic_id,assignment,status,exact_date,notes,version)"
            + " values('session','school','topic','Marzo','','2027-03-02','keep',9)");
    db.update(
        "insert into calendar_blocks(id,school_id,area_id,label,kind,start_date,end_date)"
            + " values('block','school',null,'Exam','EXAM','2027-03-03','2027-03-04')");
    db.update(
        "insert into import_history(id,school_id,user_id,imported_at,affected_topics,revision)"
            + " values('history','school','teacher',CURRENT_TIMESTAMP,1,17)");
    db.update(
        "insert into schedules(id,school_id,area_id,weekday,start_time,end_time)"
            + " values('schedule','school','area',1,'09:00','10:00')");
    db.update(
        "insert into"
            + " spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name)"
            + " values('login','session-cookie',1,2,1800,9999999999999,'synthetic')");
  }

  @org.junit.jupiter.api.AfterEach
  void releaseConnection() {
    if (source != null) source.destroy();
  }

  @Test
  void preservesHistoricalIdentityValuesAndRevision() {
    oldDatabase(6);
    Flyway.configure().dataSource(source).load().migrate();
    assertThat(db.queryForMap("select * from academic_contexts where school_id='school'"))
        .containsEntry("level", "PRIMARY")
        .containsEntry("stage_value", 6)
        .containsEntry("school_year", 2027)
        .containsEntry("revision", 17L);
    assertThat(db.queryForMap("select * from planned_sessions where id='session'"))
        .containsEntry("notes", "keep")
        .containsEntry("version", 9L)
        .containsEntry("context_id", "school");
    assertThat(
            db.queryForObject(
                "select context_id from import_history where id='history'", String.class))
        .isEqualTo("school");
    assertThat(db.queryForObject("select owner_id from areas where id='area'", String.class))
        .isEqualTo("teacher");
    assertThat(
            db.queryForObject("select context_id from schedules where id='schedule'", String.class))
        .isEqualTo("school");
    assertThat(
            db.queryForObject(
                "select session_id from spring_session where primary_id='login'", String.class))
        .isEqualTo("session-cookie");
    assertThat(
            db.queryForObject(
                "select context_id from calendar_blocks where id='block'", String.class))
        .isEqualTo("school");
  }

  @Test
  void refusesAmbiguousHistoricalGradeRatherThanGuessingLevel() {
    oldDatabase(7);
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
        .isInstanceOf(Exception.class);
  }

  @Test
  void databaseRejectsSameSchoolCrossContextParents() {
    oldDatabase(6);
    Flyway.configure().dataSource(source).load().migrate();
    db.update(
        "insert into"
            + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
            + " values('other','school',2028,'INITIAL','AGE',0,0,false)");
    assertThatThrownBy(
            () ->
                db.update(
                    "insert into topics(id,school_id,context_id,area_id,name,unit)"
                        + " values('forged','school','other','area','No',1)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (String sql :
        java.util.List.of(
            "insert into schedules(id,school_id,context_id,area_id,weekday,start_time,end_time)"
                + " values('bad-schedule','school','other','area',1,'09:00','10:00')",
            "insert into planned_sessions(id,school_id,context_id,topic_id)"
                + " values('bad-session','school','other','topic')",
            "insert into"
                + " calendar_blocks(id,school_id,context_id,area_id,label,kind,start_date,end_date)"
                + " values('bad-block','school','other','area','No','OTHER','2028-01-01','2028-01-01')")) {
      assertThatThrownBy(() -> db.update(sql))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }
}
