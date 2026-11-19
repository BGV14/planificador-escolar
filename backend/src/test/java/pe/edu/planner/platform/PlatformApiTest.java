package pe.edu.planner.platform;

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
class PlatformApiTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @Autowired PlatformService platform;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  final ObjectMapper json = new ObjectMapper();
  HttpClient client;
  String csrf;

  @BeforeEach
  void setup() {
    for (String table : List.of("platform_administrators", "classroom_tutors", "import_history", "planned_sessions", "calendar_blocks", "schedules", "topics", "areas", "app_users", "academic_contexts", "schools"))
      db.update("delete from " + table);
    for (String school : List.of("pa", "pb")) {
      db.update("insert into schools(id,name,school_year,grade) values(?,?,2026,6)", school, school);
      db.update("insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,legacy_default) values(?,?,2026,'PRIMARY','GRADE',6,true)", school, school);
    }
    String hash = passwords.encode("synthetic-password");
    for (String user : List.of("global", "local", "teacher"))
      db.update("insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)", user, user.equals("local") ? "pb" : "pa", user, hash, user.equals("teacher") ? "TEACHER" : "ADMIN");
    db.update("insert into platform_administrators(user_id) values('global')");
    db.update("insert into areas(id,school_id,context_id,name,owner_id) values('preserved','pa','pa','Preserved','global')");
    client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    csrf = null;
  }

  @AfterEach
  void removeGrants() {
    db.update("delete from platform_administrators");
  }

  String school(String username) throws Exception {
    return json.writeValueAsString(Map.of("name", "New school", "year", 2030, "level", "INITIAL", "stageValue", 0, "section", " b ", "adminUsername", username, "adminPassword", "new-school-password"));
  }

  @Test
  void createsEmptySchoolAtomicallyAndKeepsTheSameSchoolIdentity() throws Exception {
    login("global");
    var original = db.queryForMap("select * from app_users where id='global'");
    var response = call("POST", "/api/platform/schools", school("newadmin"));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("password", "hash", "new-school-password", "$2a$");
    String id = json.readTree(response.body()).get("id").asText();
    assertThat(db.queryForMap("select * from academic_contexts where school_id=?", id))
        .containsEntry("school_year", 2030).containsEntry("level", "INITIAL").containsEntry("stage_kind", "AGE").containsEntry("stage_value", 0).containsEntry("section", "B").containsEntry("legacy_default", true);
    for (String table : List.of("areas", "topics", "planned_sessions", "schedules", "calendar_blocks", "import_history"))
      assertThat(db.queryForObject("select count(*) from " + table + " where school_id=?", Integer.class, id)).as(table).isZero();
    var admin = db.queryForMap("select * from app_users where school_id=?", id);
    assertThat(admin).containsEntry("role", "ADMIN");
    assertThat(admin.get("password_hash").toString()).startsWith("$2a$12$");
    assertThat(passwords.matches("new-school-password", admin.get("password_hash").toString())).isTrue();
    assertThat(db.queryForMap("select * from app_users where id='global'")).isEqualTo(original);
    assertThat(call("GET", "/api/me", null).body()).contains("\"schoolId\":\"pa\"", "\"superadmin\":true");
    assertThat(call("GET", "/api/contexts", null).body()).doesNotContain(id);
    assertThat(call("GET", "/api/areas?contextId=" + id, null).statusCode()).isEqualTo(404);
    var list = call("GET", "/api/platform/schools", null);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains("newadmin", "New school").doesNotContain("password", "hash", "Preserved");
    login("newadmin", "new-school-password");
    assertThat(call("GET", "/api/me", null).body()).contains("\"schoolId\":\"" + id + "\"", "\"superadmin\":false");
    assertThat(call("GET", "/api/areas", null).body()).isEqualTo("[]");
    assertThat(call("GET", "/api/platform/schools", null).statusCode()).isEqualTo(403);
  }

  @Test
  void duplicateUsernameRollsBackSchoolAndInitialContextWithoutSecretLeak() throws Exception {
    login("global");
    int schools = db.queryForObject("select count(*) from schools", Integer.class);
    int contexts = db.queryForObject("select count(*) from academic_contexts", Integer.class);
    var response = call("POST", "/api/platform/schools", school("local"));
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).doesNotContain("new-school-password", "password_hash", "insert into");
    assertThat(db.queryForObject("select count(*) from schools", Integer.class)).isEqualTo(schools);
    assertThat(db.queryForObject("select count(*) from academic_contexts", Integer.class)).isEqualTo(contexts);
    assertThat(db.queryForObject("select owner_id from areas where id='preserved'", String.class)).isEqualTo("global");
  }

  @Test
  void ordinaryAdminsTeachersAndRevokedSessionsCannotUseGlobalRoutes() throws Exception {
    var anonymous = HttpClient.newHttpClient();
    assertThat(anonymous.send(HttpRequest.newBuilder(URI.create(url("/api/platform/schools"))).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    for (String username : List.of("local", "teacher")) {
      login(username);
      assertThat(call("GET", "/api/platform/schools", null).statusCode()).isEqualTo(403);
      assertThat(call("POST", "/api/platform/schools", school("forged")).statusCode()).isEqualTo(403);
      for (String id : List.of("pa", "absent"))
        assertThat(call("POST", "/api/platform/schools/" + id + "/administrators", "{\"username\":\"forged\",\"password\":\"synthetic-password\",\"role\":\"SUPERADMIN\",\"superadmin\":true}").statusCode()).isEqualTo(403);
    }
    login("global");
    String token = csrf;
    csrf = null;
    assertThat(call("POST", "/api/platform/schools", school("nocsrf")).statusCode()).isEqualTo(403);
    csrf = token;
    db.update("delete from platform_administrators where user_id='global'");
    assertThat(call("GET", "/api/me", null).body()).contains("\"superadmin\":false");
    assertThat(call("GET", "/api/platform/schools", null).statusCode()).isEqualTo(403);
    assertThat(call("POST", "/api/platform/schools", school("revoked")).statusCode()).isEqualTo(403);
    assertThat(call("POST", "/api/platform/schools/absent/administrators", "{\"username\":\"revoked\",\"password\":\"synthetic-password\"}").statusCode()).isEqualTo(403);
  }

  @Test
  void onlyGlobalRouteCreatesAdminsAndItNeverGrantsGlobalCapability() throws Exception {
    login("global");
    var response = call("POST", "/api/platform/schools/pb/administrators", "{\"username\":\"secondadmin\",\"password\":\"synthetic-password\",\"role\":\"SUPERADMIN\",\"superadmin\":true}");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"schoolId\":\"pb\"", "\"role\":\"ADMIN\"").doesNotContain("password", "hash");
    assertThat(db.queryForObject("select count(*) from platform_administrators", Integer.class)).isEqualTo(1);
    assertThat(call("POST", "/api/platform/schools/absent/administrators", "{\"username\":\"another\",\"password\":\"synthetic-password\"}").statusCode()).isEqualTo(404);
    for (String username : List.of("global", "local")) {
      login(username);
      for (String role : List.of("ADMIN", "SUPERADMIN"))
        assertThat(call("POST", "/api/users", "{\"username\":\"forgedadmin\",\"password\":\"synthetic-password\",\"role\":\"" + role + "\"}").statusCode()).isEqualTo(400);
      assertThat(call("POST", "/api/users", "{\"username\":\"course-" + username + "\",\"password\":\"synthetic-password\",\"role\":\"TEACHER\"}").statusCode()).isEqualTo(200);
    }
    login("secondadmin");
    assertThat(call("GET", "/api/platform/schools", null).statusCode()).isEqualTo(403);
    assertThat(call("GET", "/api/areas?contextId=pa", null).statusCode()).isEqualTo(404);
  }

  @Test
  void validatesAcademicIdentityAndPasswordBeforeCreatingAnything() throws Exception {
    login("global");
    var base = json.readTree(school("invalid"));
    for (String field : List.of("name", "level", "section", "adminUsername", "adminPassword")) {
      var input = base.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) input).put(field, "");
      assertThat(call("POST", "/api/platform/schools", input.toString()).statusCode()).as(field).isEqualTo(400);
    }
    for (String field : List.of("year", "stageValue")) {
      var input = base.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) input).put(field, 9999);
      assertThat(call("POST", "/api/platform/schools", input.toString()).statusCode()).as(field).isEqualTo(400);
    }
    var input = base.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("adminPassword", "á".repeat(37));
    assertThat(call("POST", "/api/platform/schools", input.toString()).statusCode()).isEqualTo(400);
    assertThat(db.queryForObject("select count(*) from schools", Integer.class)).isEqualTo(2);
    assertThat(db.queryForObject("select count(*) from academic_contexts", Integer.class)).isEqualTo(2);
  }

  @Test
  void grantLockLastsUntilTheProvisioningTransactionCompletes() throws Exception {
    login("global");
    var holding = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var revoking = new java.util.concurrent.CountDownLatch(1);
    var principal = new pe.edu.planner.auth.PlannerPrincipal("global", "pa", "global", "unused", "ADMIN");
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var provision = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
        var created = platform.createSchool(principal, new PlatformService.SchoolInput("Locked school", 2030, "PRIMARY", 1, "A", "lockedadmin", "synthetic-password"));
        holding.countDown();
        try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        return created;
      }));
      assertThat(holding.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var revoke = executor.submit(() -> {
        revoking.countDown();
        return db.update("delete from platform_administrators where user_id='global'");
      });
      try {
        assertThat(revoking.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> revoke.get(300, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
      } finally { release.countDown(); }
      assertThat(provision.get(10, java.util.concurrent.TimeUnit.SECONDS).name()).isEqualTo("Locked school");
      assertThat(revoke.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
    } finally { release.countDown(); }
    assertThat(call("GET", "/api/platform/schools", null).statusCode()).isEqualTo(403);
    assertThat(call("POST", "/api/platform/schools", school("afterrevoke")).statusCode()).isEqualTo(403);
  }
  void login(String username) throws Exception { login(username, "synthetic-password"); }
  void login(String username, String password) throws Exception {
    call("POST", "/api/logout", "");
    csrf = json.readTree(call("GET", "/api/csrf", null).body()).get("token").asText();
    var response = client.send(HttpRequest.newBuilder(URI.create(url("/api/login"))).header("X-CSRF-TOKEN", csrf).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    csrf = json.readTree(call("GET", "/api/csrf", null).body()).get("token").asText();
  }
  String url(String path) { return "http://localhost:" + port + path; }
  HttpResponse<String> call(String method, String path, String payload) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json");
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    return client.send(request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
