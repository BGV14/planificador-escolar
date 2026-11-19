package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeacherAccessApiTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  final ObjectMapper json = new ObjectMapper();
  HttpClient client;
  String csrf;

  @BeforeEach
  void setup() throws Exception {
    for (String table : List.of("classroom_tutors", "import_history", "planned_sessions", "calendar_blocks", "schedules", "topics", "areas", "app_users", "academic_contexts", "schools"))
      db.update("delete from " + table);
    for (String school : List.of("ta", "tb")) {
      db.update("insert into schools(id,name,school_year,grade) values(?,?,2026,6)", school, school);
      db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,legacy_default) values(?,?,2026,'PRIMARY','GRADE',6,true)", school, school);
    }
    db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,section) values('other-class','ta',2026,'PRIMARY','GRADE',6,'B')");
    for (String name : List.of("admin", "teacher", "peer", "unassigned", "foreign"))
      db.update("insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)", name, name.equals("foreign") ? "tb" : "ta", name, passwords.encode("synthetic-password"), name.equals("admin") ? "ADMIN" : "TEACHER");
    for (String area : List.of("owned", "private", "shared")) {
      db.update("insert into areas(id,school_id,context_id,name,owner_id,shared_edit) values(?,'ta','ta',?,?,?)", area, area, area.equals("owned") ? "teacher" : "peer", area.equals("shared"));
      db.update("insert into topics(id,school_id,context_id,area_id,name,unit,legacy_id) values(?,'ta','ta',?,?,1,?)", area, area, area + "-topic", area + "|1|0");
      db.update("insert into planned_sessions(id,school_id,context_id,topic_id,assignment,status,exact_date,notes) values(?,'ta','ta',?,'Marzo','','2026-03-02',?)", area, area, area + "-secret");
      db.update("insert into schedules(id,school_id,context_id,area_id,weekday,start_time,end_time) values(?,'ta','ta',?,1,'08:00','09:00')", area, area);
      db.update("insert into calendar_blocks(id,school_id,context_id,area_id,label,kind,start_date,end_date) values(?,'ta','ta',?,?,'OTHER','2026-03-02','2026-03-02')", area, area, area + "-block");
    }
    for (String user : List.of("teacher", "peer"))
      db.update("insert into import_history(id,school_id,context_id,user_id,imported_at,affected_topics,revision) values(?,'ta','ta',?,current_timestamp,1,0)", user + "-history", user);
    client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
  }

  @Test
  void courseFiltersAllReadSurfacesAndBackupsAreAdminOnly() throws Exception {
    login("teacher");
    for (String resource : List.of("areas", "topics", "sessions", "schedules", "blocks", "plan")) {
      var response = call("GET", "/api/" + resource + "?contextId=ta", null);
      assertThat(response.statusCode()).as(resource).isEqualTo(200);
      assertThat(response.body()).as(resource).doesNotContain("private");
      assertThat(response.body()).contains("owned");
    }
    assertThat(call("GET", "/api/contexts", null).body()).doesNotContain("other-class");
    assertThat(call("GET", "/api/imports?contextId=ta", null).body()).contains("teacher-history").doesNotContain("peer-history");
    assertThat(call("GET", "/api/plan?contextId=other-class", null).statusCode()).isEqualTo(403);
    assertThat(call("GET", "/api/exports/backup?contextId=ta&format=full", null).statusCode()).isEqualTo(403);
    assertThat(call("GET", "/api/exports/backup?contextId=ta&format=full-v4", null).statusCode()).isEqualTo(403);
    assertThat(call("GET", "/api/exports/backup?contextId=ta&format=json&month=3", null).body()).doesNotContain("private");
    var incoming = json.writeValueAsString(Map.of("json", "{\"notes\":{\"private|1|0\":\"intrusion\"}}"));
    assertThat(call("POST", "/api/imports/preview?contextId=ta", incoming).statusCode()).isEqualTo(403);
    var own = json.writeValueAsString(Map.of("json", "{\"notes\":{\"owned|1|0\":\"new\"}}"));
    var preview = call("POST", "/api/imports/preview?contextId=ta", own);
    assertThat(preview.statusCode()).isEqualTo(200);
    assertThat(preview.body()).doesNotContain("private");
  }

  @Test
  void tutorReadsWholeClassButNeverGainsForeignWriteAndRevokesWithoutLogin() throws Exception {
    login("admin");
    assertThat(call("POST", "/api/classroom-tutors?contextId=ta", "{\"userId\":\"unassigned\"}").statusCode()).isEqualTo(200);
    assertThat(call("GET", "/api/classroom-tutors?contextId=ta", null).body()).contains("unassigned");
    assertThat(call("DELETE", "/api/classroom-tutors/unassigned?contextId=ta", null).statusCode()).isEqualTo(200);
    assertThat(call("POST", "/api/classroom-tutors?contextId=ta", "{\"userId\":\"unassigned\"}").statusCode()).isEqualTo(200);
    login("unassigned");
    assertThat(call("GET", "/api/areas?contextId=ta", null).body()).contains("private");
    assertThat(call("GET", "/api/imports?contextId=ta", null).body()).contains("teacher-history", "peer-history");
    assertThat(call("GET", "/api/plan?contextId=other-class", null).statusCode()).isEqualTo(403);
    assertThat(call("POST", "/api/topics?contextId=ta", "{\"areaId\":\"private\",\"name\":\"No\",\"unit\":1}").statusCode()).isEqualTo(403);
    assertThat(call("POST", "/api/classroom-tutors?contextId=ta", "{\"userId\":\"teacher\"}").statusCode()).isEqualTo(403);
    db.update("delete from classroom_tutors where user_id='unassigned'");
    assertThat(call("GET", "/api/areas?contextId=ta", null).body()).doesNotContain("private");
    assertThat(call("GET", "/api/areas?contextId=ta", null).body()).contains("shared");
  }

  @Test
  void validatesTenantRoleAndOwnerTransfer() throws Exception {
    login("admin");
    for (String target : List.of("foreign", "admin", "missing"))
      assertThat(call("POST", "/api/classroom-tutors?contextId=ta", "{\"userId\":\"" + target + "\"}").statusCode()).isEqualTo(404);
    assertThat(call("POST", "/api/classroom-tutors?contextId=tb", "{\"userId\":\"teacher\"}").statusCode()).isEqualTo(404);
    assertThat(call("PUT", "/api/areas/owned?contextId=ta", "{\"name\":\"owned\",\"ownerId\":\"peer\",\"sharedEdit\":false}").statusCode()).isEqualTo(200);
    login("teacher");
    assertThat(call("GET", "/api/areas?contextId=ta", null).body()).doesNotContain("owned");
    assertThat(call("POST", "/api/topics?contextId=ta", "{\"areaId\":\"owned\",\"name\":\"No\",\"unit\":1}").statusCode()).isEqualTo(403);
  }

  @Test
  void noAssignmentsShowsNoContextsAndStrictValidationPrecedesAuthorization() throws Exception {
    db.update("update areas set shared_edit=false");
    login("unassigned");
    assertThat(call("GET", "/api/contexts", null).body()).isEqualTo("[]");
    assertThat(call("GET", "/api/plan?contextId=ta", null).statusCode()).isEqualTo(403);
    login("teacher");
    var malformed = json.writeValueAsString(Map.of("json", "{\"notes\":{\"private|1|0\":\"x\",\"private|1|0\":\"y\"}}"));
    assertThat(call("POST", "/api/imports/preview?contextId=ta", malformed).statusCode()).isEqualTo(400);
  }

  @Test
  void confirmationRechecksOwnershipAndDoesNotLeakBaseline() throws Exception {
    login("teacher");
    var payload = "{\"notes\":{\"owned|1|0\":\"new\"}}";
    var preview = call("POST", "/api/imports/preview?contextId=ta", json.writeValueAsString(Map.of("json", payload)));
    assertThat(preview.statusCode()).isEqualTo(200);
    long revision = json.readTree(preview.body()).get("revision").asLong();
    db.update("update areas set owner_id='peer' where id='owned'");
    var confirmation = json.writeValueAsString(Map.of("json", payload, "revision", revision, "acknowledgeIncomingWins", true));
    assertThat(call("POST", "/api/imports/confirm?contextId=ta", confirmation).statusCode()).isEqualTo(403);
    assertThat(db.queryForObject("select notes from planned_sessions where id='owned'", String.class)).isEqualTo("owned-secret");
  }

  void login(String username) throws Exception {
    call("POST", "/api/logout", "");
    csrf = json.readTree(call("GET", "/api/csrf", null).body()).get("token").asText();
    var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
        .header("X-CSRF-TOKEN", csrf).header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("username=" + username + "&password=synthetic-password")).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    csrf = json.readTree(call("GET", "/api/csrf", null).body()).get("token").asText();
  }

  HttpResponse<String> call(String method, String path, String payload) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    return client.send(request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
