package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
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
class PlannerApiTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  final ObjectMapper json = new ObjectMapper();
  HttpClient client;
  String csrf;
  String area;
  String topic;

  @BeforeEach
  void setup() throws Exception {
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
    for (String school : List.of("s1", "s2"))
      db.update(
          "insert into schools(id,name,school_year,grade,revision) values(?,?,2026,6,0)",
          school,
          school);
    db.update(
        "insert into"
            + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
            + " select id,id,school_year,'PRIMARY','GRADE',grade,revision,true from schools");
    for (String name : List.of("admin", "teacher", "other", "foreign"))
      db.update(
          "insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)",
          name,
          name.equals("foreign") ? "s2" : "s1",
          name,
          passwords.encode("synthetic-password"),
          name.equals("admin") ? "ADMIN" : "TEACHER");
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    login("admin");
    area =
        body(call(
                "POST",
                "/api/areas",
                "{\"name\":\"Math\",\"ownerId\":\"teacher\",\"sharedEdit\":false}"))
            .get("id")
            .asText();
    topic =
        body(call(
                "POST",
                "/api/topics",
                "{\"areaId\":\""
                    + area
                    + "\",\"name\":\"Sets\",\"unit\":1,\"legacyId\":\"Math|1|0\"}"))
            .get("id")
            .asText();
  }

  @Test
  void requiresSessionAndCsrfAndRejectsWrongPassword() throws Exception {
    var anonymous = HttpClient.newHttpClient();
    assertThat(
            anonymous
                .send(
                    HttpRequest.newBuilder(URI.create(url("/api/me"))).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(url("/api/areas")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(403);
    assertThat(call("POST", "/api/logout", "").statusCode()).isEqualTo(204);
    csrf = body(call("GET", "/api/csrf", null)).get("token").asText();
    assertThat(formLogin("admin", "incorrect").statusCode()).isEqualTo(401);
  }

  @Test
  void isolatesSchoolAndAreaOwnershipAndUserAdministration() throws Exception {
    login("foreign");
    assertThat(call("GET", "/api/topics", null).body()).doesNotContain("Sets");
    assertThat(
            call(
                    "PUT",
                    "/api/areas/" + area,
                    "{\"name\":\"Intrusion\",\"ownerId\":\"foreign\",\"sharedEdit\":true}")
                .statusCode())
        .isEqualTo(403);
    assertThat(
            call(
                    "POST",
                    "/api/topics",
                    "{\"areaId\":\"" + area + "\",\"name\":\"Intrusion\",\"unit\":1}")
                .statusCode())
        .isEqualTo(404);
    login("other");
    assertThat(call("POST", "/api/sessions", sessionBody(0, "2026-03-02")).statusCode())
        .isEqualTo(403);
    assertThat(
            call(
                    "POST",
                    "/api/users",
                    "{\"username\":\"new\",\"password\":\"long-password\",\"role\":\"ADMIN\"}")
                .statusCode())
        .isEqualTo(403);
    login("admin");
    assertThat(
            call(
                    "PUT",
                    "/api/areas/" + area,
                    "{\"name\":\"Math\",\"ownerId\":\"foreign\",\"sharedEdit\":false}")
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void detectsStaleEditsAndBlockedDatesWithoutMovingDates() throws Exception {
    login("teacher");
    var created = call("POST", "/api/sessions", sessionBody(0, "2026-03-02"));
    assertThat(created.statusCode()).isEqualTo(200);
    var id = body(created).get("id").asText();
    assertThat(call("PUT", "/api/sessions/" + id, sessionBody(0, "2026-03-03")).statusCode())
        .isEqualTo(200);
    assertThat(call("PUT", "/api/sessions/" + id, sessionBody(0, "2026-03-04")).statusCode())
        .isEqualTo(409);
    login("admin");
    assertThat(
            call(
                    "POST",
                    "/api/blocks",
                    "{\"label\":\"Exam\",\"kind\":\"EXAM\",\"startDate\":\"2026-03-05\",\"endDate\":\"2026-03-06\"}")
                .statusCode())
        .isEqualTo(200);
    assertThat(call("PUT", "/api/sessions/" + id, sessionBody(1, "2026-03-05")).statusCode())
        .isEqualTo(409);
    assertThat(call("GET", "/api/sessions", null).body())
        .contains("2026-03-03")
        .doesNotContain("2026-03-05");
    assertThat(call("PUT", "/api/sessions/" + id, sessionBody(1, "2027-03-05")).statusCode())
        .isEqualTo(400);
  }

  @Test
  void importsCumulativelyAtomicallyAndRejectsStalePreview() throws Exception {
    String first = "{\"assignments\":{\"Math|1|0\":\"Marzo\"},\"notes\":{\"Math|1|0\":\"keep\"}}";
    var preview =
        body(call("POST", "/api/imports/preview", json.writeValueAsString(Map.of("json", first))));
    assertThat(preview.get("result").get("preview").get("affectedTopics").asInt()).isEqualTo(1);
    assertThat(confirm(first, preview.get("revision").asLong()).statusCode()).isEqualTo(200);
    assertThat(confirm(first, preview.get("revision").asLong()).statusCode()).isEqualTo(409);
    long revision = body(call("GET", "/api/plan", null)).get("revision").asLong();
    assertThat(
            confirm(
                    "{\"status\":{\"Math|1|0\":\"Trabajado\"},\"notes\":{\"Math|1|0\":\"\"}}",
                    revision)
                .statusCode())
        .isEqualTo(200);
    var state = body(call("GET", "/api/plan", null)).get("state");
    assertThat(state.get("assignments").get("Math|1|0").asText()).isEqualTo("Marzo");
    assertThat(state.get("status").get("Math|1|0").asText()).isEqualTo("Trabajado");
    assertThat(state.get("notes").get("Math|1|0").asText()).isEmpty();
    assertThat(body(call("GET", "/api/imports", null)).size()).isEqualTo(2);
    revision = body(call("GET", "/api/plan", null)).get("revision").asLong();
    assertThat(confirm("{\"dates\":{\"unknown|1|0\":\"2026-03-01\"}}", revision).statusCode())
        .isEqualTo(400);
    assertThat(body(call("GET", "/api/imports", null)).size()).isEqualTo(2);
  }

  @Test
  void supportsSharedEditingAndCrudWithForeignReferenceChecks() throws Exception {
    assertThat(
            call(
                    "POST",
                    "/api/users",
                    "{\"username\":\"newteacher\",\"password\":\"a-new-long-password\",\"role\":\"TEACHER\"}")
                .statusCode())
        .isEqualTo(200);
    assertThat(call("GET", "/api/users", null).body()).doesNotContain("password");
    assertThat(
            call(
                    "PUT",
                    "/api/areas/" + area,
                    "{\"name\":\"Math\",\"ownerId\":\"teacher\",\"sharedEdit\":true}")
                .statusCode())
        .isEqualTo(200);
    login("other");
    var schedule =
        call(
            "POST",
            "/api/schedules",
            "{\"areaId\":\""
                + area
                + "\",\"weekday\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}");
    assertThat(schedule.statusCode()).isEqualTo(200);
    String scheduleId = body(schedule).get("id").asText();
    assertThat(
            call(
                    "PUT",
                    "/api/schedules/" + scheduleId,
                    "{\"areaId\":\""
                        + area
                        + "\",\"weekday\":2,\"startTime\":\"11:00\",\"endTime\":\"10:00\"}")
                .statusCode())
        .isEqualTo(400);
    assertThat(call("DELETE", "/api/schedules/" + scheduleId, null).statusCode()).isEqualTo(200);
    var session = body(call("POST", "/api/sessions", sessionBody(0, "2026-03-02")));
    assertThat(
            call("DELETE", "/api/sessions/" + session.get("id").asText() + "?version=9", null)
                .statusCode())
        .isEqualTo(409);
    assertThat(
            call("DELETE", "/api/sessions/" + session.get("id").asText() + "?version=0", null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            call(
                    "POST",
                    "/api/blocks",
                    "{\"label\":\"School"
                        + " break\",\"kind\":\"VACATION\",\"startDate\":\"2026-04-01\",\"endDate\":\"2026-04-03\"}")
                .statusCode())
        .isEqualTo(403);
    assertThat(call("DELETE", "/api/topics/" + topic, null).statusCode()).isEqualTo(200);
    login("admin");
    assertThat(call("DELETE", "/api/areas/" + area, null).statusCode()).isEqualTo(200);
  }

  @Test
  void preservesExistingDatesWhenNewBlockOverlapsAndRollsBackBlockedImport() throws Exception {
    var session = body(call("POST", "/api/sessions", sessionBody(0, "2026-03-02")));
    var block =
        call(
            "POST",
            "/api/blocks",
            "{\"areaId\":\""
                + area
                + "\",\"label\":\"Exam\",\"kind\":\"EXAM\",\"startDate\":\"2026-03-02\",\"endDate\":\"2026-03-03\"}");
    assertThat(block.statusCode()).isEqualTo(200);
    assertThat(body(block).get("warnings").size()).isEqualTo(1);
    long revision = body(call("GET", "/api/plan", null)).get("revision").asLong();
    assertThat(
            confirm(
                    "{\"dates\":{\"Math|1|0\":\"2026-03-03\"},\"notes\":{\"Math|1|0\":\"must"
                        + " rollback\"}}",
                    revision)
                .statusCode())
        .isEqualTo(409);
    var persisted = body(call("GET", "/api/sessions", null)).get(0);
    assertThat(persisted.get("exactDate").asText()).isEqualTo("2026-03-02");
    assertThat(persisted.get("notes").asText()).isEmpty();
    assertThat(body(call("GET", "/api/imports", null)).size()).isZero();
    assertThat(body(call("GET", "/api/plan", null)).get("revision").asLong()).isEqualTo(revision);
  }

  @Test
  void importsAreForbiddenForUnownedAreasAndCannotForgeSchool() throws Exception {
    login("other");
    assertThat(
            call(
                    "POST",
                    "/api/imports/preview",
                    json.writeValueAsString(
                        Map.of("json", "{\"notes\":{\"Math|1|0\":\"intrusion\"}}")))
                .statusCode())
        .isEqualTo(403);
    login("foreign");
    assertThat(
            call(
                    "POST",
                    "/api/schedules",
                    "{\"areaId\":\""
                        + area
                        + "\",\"weekday\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "POST",
                    "/api/blocks",
                    "{\"areaId\":\""
                        + area
                        + "\",\"label\":\"Bad\",\"kind\":\"OTHER\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-02\"}")
                .statusCode())
        .isEqualTo(404);
    assertThat(call("POST", "/api/sessions", sessionBody(0, "2026-03-02")).statusCode())
        .isEqualTo(404);
  }

  @Test
  void concurrentImportsUseOneRevisionAndDatabaseSessionsPersistAuthentication() throws Exception {
    assertThat(
            db.queryForObject(
                "select count(*) from spring_session where principal_name='admin'", Integer.class))
        .isGreaterThan(0);
    long revision = body(call("GET", "/api/plan", null)).get("revision").asLong();
    String payload =
        json.writeValueAsString(
            Map.of(
                "json",
                "{\"notes\":{\"Math|1|0\":\"concurrent\"}}",
                "revision",
                revision,
                "acknowledgeIncomingWins",
                true));
    var request =
        HttpRequest.newBuilder(URI.create(url("/api/imports/confirm")))
            .header("Content-Type", "application/json")
            .header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    assertThat(List.of(first.get().statusCode(), second.get().statusCode()))
        .containsExactlyInAnyOrder(200, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from import_history where school_id='s1'", Integer.class))
        .isEqualTo(1);
  }

  HttpResponse<String> confirm(String payload, long revision) throws Exception {
    return call(
        "POST",
        "/api/imports/confirm",
        json.writeValueAsString(
            Map.of("json", payload, "revision", revision, "acknowledgeIncomingWins", true)));
  }

  String sessionBody(long version, String date) {
    return "{\"topicId\":\""
        + topic
        + "\",\"version\":"
        + version
        + ",\"assignment\":\"Marzo\",\"status\":\"\",\"date\":\""
        + date
        + "\",\"notes\":\"\"}";
  }

  void login(String username) throws Exception {
    call("POST", "/api/logout", "");
    csrf = body(call("GET", "/api/csrf", null)).get("token").asText();
    assertThat(formLogin(username, "synthetic-password").statusCode()).isEqualTo(200);
    csrf = body(call("GET", "/api/csrf", null)).get("token").asText();
  }

  HttpResponse<String> formLogin(String username, String password) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(url("/api/login")))
            .header("X-CSRF-TOKEN", csrf)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "username=" + username + "&password=" + password))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> call(String method, String path, String payload) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json");
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    return client.send(
        request
            .method(
                method,
                payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  JsonNode body(HttpResponse<String> response) throws Exception {
    return json.readTree(response.body());
  }

  String url(String path) {
    return "http://localhost:" + port + path;
  }
}
