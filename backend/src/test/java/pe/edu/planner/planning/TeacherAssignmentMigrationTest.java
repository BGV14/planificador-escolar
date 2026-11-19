package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.planner.auth.PlannerPrincipal;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeacherAssignmentMigrationTest {
  @Autowired JdbcTemplate db;

  @Test
  void assignmentIsSchoolBoundAndRevocationIsImmediate() {
    for (String s : new String[] {"policy-a", "policy-b"}) {
      db.update("insert into schools(id,name,school_year,grade) values(?,?,2026,6)", s, s);
      db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value) values(?,?,2026,'PRIMARY','GRADE',6)", s, s);
      db.update("insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,'unused','TEACHER')", s, s, s);
    }
    var teacher = new PlannerPrincipal("policy-a", "policy-a", "policy-a", "unused", "TEACHER");
    var policy = new TeacherAccessPolicy(db);
    assertThat(policy.canReadContext(teacher, "policy-a")).isFalse();
    assertThatThrownBy(() -> db.update("insert into classroom_tutors(school_id,context_id,user_id) values('policy-a','policy-b','policy-a')"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    db.update("insert into classroom_tutors(school_id,context_id,user_id) values('policy-a','policy-a','policy-a')");
    assertThat(policy.canReadContext(teacher, "policy-a")).isTrue();
    assertThat(policy.canReadContext(teacher, "policy-b")).isFalse();
    assertThatThrownBy(() -> db.update("insert into classroom_tutors(school_id,context_id,user_id) values('policy-a','policy-a','policy-a')"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    db.update("delete from classroom_tutors where school_id='policy-a'");
    assertThat(policy.canReadContext(teacher, "policy-a")).isFalse();
    db.update("insert into areas(id,school_id,context_id,name,owner_id,shared_edit) values('policy-own','policy-a','policy-a','Owned','policy-a',false)");
    assertThat(policy.visibleAreaIds(teacher, "policy-a")).containsExactly("policy-own");
    assertThatThrownBy(() -> db.update("update areas set owner_id='policy-b' where id='policy-own'"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    db.update("insert into app_users(id,school_id,username,password_hash,role) values('policy-peer','policy-a','policy-peer','unused','TEACHER')");
    db.update("update areas set owner_id='policy-peer' where id='policy-own'");
    assertThat(policy.visibleAreaIds(teacher, "policy-a")).isEmpty();
    db.update("update areas set shared_edit=true where id='policy-own'");
    assertThat(policy.visibleAreaIds(teacher, "policy-a")).containsExactly("policy-own");
  }
}
