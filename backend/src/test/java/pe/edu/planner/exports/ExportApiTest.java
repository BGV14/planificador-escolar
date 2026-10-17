package pe.edu.planner.exports;

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
import pe.edu.planner.legacy.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExportApiTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  final ObjectMapper json = new ObjectMapper();
  HttpClient client;

  String url(String path) {
    return "http://localhost:" + port + path;
  }

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
    for (String school : List.of("one", "two")) {
      db.update(
          "insert into schools(id,name,school_year,grade,revision) values(?,?,2026,6,0)",
          school,
          "School " + school);
      db.update(
          "insert into"
              + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
              + " values(?,?,2026,'PRIMARY','GRADE',6,0,true)",
          school,
          school);
      db.update(
          "insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)",
          "u" + school,
          school,
          "u" + school,
          passwords.encode("synthetic-password"),
          "TEACHER");
      db.update(
          "insert into areas(id,school_id,context_id,name,owner_id,shared_edit)"
              + " values(?,?,?,?,?,false)",
          "a" + school,
          school,
          school,
          "Area " + school,
          "u" + school);
      db.update(
          "insert into topics(id,school_id,context_id,area_id,name,unit,legacy_id)"
              + " values(?,?,?,?,?,1,?)",
          "t" + school,
          school,
          school,
          "a" + school,
          "Topic " + school,
          "Math|1|0");
      db.update(
          "insert into"
              + " planned_sessions(id,school_id,context_id,topic_id,assignment,status,exact_date,notes,version)"
              + " values(?,?,?,?,?,?,?,?,0)",
          "p" + school,
          school,
          school,
          "t" + school,
          "Abril",
          "",
          "2026-03-02",
          "preserved-note-" + school);
    }
    db.update(
        "insert into topics(id,school_id,context_id,area_id,name,unit)"
            + " values('custom','one','one','aone','Custom topic',2)");
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    login();
  }

  void login() throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create(url("/api/csrf"))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var csrf = json.readTree(response.body());
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(url("/api/login")))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                "username=uone&password=synthetic-password"))
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
  }

  HttpResponse<byte[]> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  @Test
  void downloadsAuthenticatedNativeFormatsAndRefusesCrossSchoolArea() throws Exception {
    var xlsx = get("/api/exports/monthly?areaId=aone&month=3&format=xlsx");
    assertThat(xlsx.statusCode()).isEqualTo(200);
    assertThat(xlsx.headers().firstValue("Content-Type").orElse("")).contains("spreadsheetml");
    assertThat(xlsx.body()[0]).isEqualTo((byte) 'P');
    assertThat(xlsx.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    var pdf = get("/api/exports/monthly?areaId=aone&month=3&format=pdf");
    assertThat(pdf.statusCode()).isEqualTo(200);
    assertThat(new String(pdf.body(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    assertThat(get("/api/exports/monthly?areaId=atwo&month=3&format=xlsx").statusCode())
        .isEqualTo(404);
    assertThat(get("/api/exports/backup?areaId=atwo").statusCode()).isEqualTo(404);
    assertThat(
            HttpClient.newHttpClient()
                .send(
                    HttpRequest.newBuilder(
                            URI.create(url("/api/exports/monthly?month=3&format=pdf")))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void legacyBackupPreservesIndependentFieldsAndExactDateMonthConflict() throws Exception {
    var response = get("/api/exports/backup?areaId=aone&month=3&format=json");
    assertThat(response.statusCode()).isEqualTo(200);
    String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("two", "Custom topic");
    var result =
        new LegacyPlanImporter(Set.of("Math|1|0"), 2026, 6).preview(body, LegacyPlanState.empty());
    assertThat(result.state().assignments()).containsEntry("Math|1|0", "Abril");
    assertThat(result.state().dates()).containsEntry("Math|1|0", "2026-03-02");
    assertThat(result.state().status()).containsEntry("Math|1|0", "");
    assertThat(result.state().notes()).containsEntry("Math|1|0", "preserved-note-one");
    assertThat(result.warnings()).extracting(ImportWarning::code).contains("MONTH_DATE_CONFLICT");
    assertThat(
            json.readTree(get("/api/exports/backup?month=5").body())
                .get("state")
                .get("dates")
                .isEmpty())
        .isTrue();
  }

  @Test
  void fullV3BackupIncludesCustomTopicsWithoutCredentialsOrForeignSchool() throws Exception {
    String body =
        new String(
            get("/api/exports/backup?format=full&month=3").body(),
            java.nio.charset.StandardCharsets.UTF_8);
    var tree = json.readTree(body);
    assertThat(tree.get("version").asInt()).isEqualTo(3);
    assertThat(tree.get("scope").asText()).isEqualTo("complete-school");
    assertThat(body)
        .contains("Custom topic", "preserved-note-one")
        .doesNotContain("Topic two", "preserved-note-two", "password", "synthetic-password");
    assertThat(tree.get("areas").size()).isEqualTo(1);
  }

  void contextFixture(String id, int year, String level, int stage) {
    db.update(
        "insert into"
            + " academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
            + " values(?,'one',?,?,?, ?,0,false)",
        id,
        year,
        level,
        level.equals("INITIAL") ? "AGE" : "GRADE",
        stage);
    db.update(
        "insert into areas(id,school_id,context_id,name,owner_id,shared_edit)"
            + " values(?,'one',?,?,'uone',false)",
        "a" + id,
        id,
        "Area " + id);
    db.update(
        "insert into topics(id,school_id,context_id,area_id,name,unit) values(?,'one',?,?,?,1)",
        "t" + id,
        id,
        "a" + id,
        "Topic " + id);
    db.update(
        "insert into"
            + " planned_sessions(id,school_id,context_id,topic_id,assignment,status,exact_date,notes,version)"
            + " values(?,'one',?,?,'Abril','',?, ?,0)",
        "p" + id,
        id,
        "t" + id,
        year + "-03-05",
        "Note " + id);
  }

  @Test
  void explicitContextExportsUseItsMetadataAndRejectWrongAreaOrSchool() throws Exception {
    contextFixture("initial", 2027, "INITIAL", 0);
    var response = get("/api/exports/monthly?contextId=initial&month=3&format=pdf");
    assertThat(response.statusCode()).isEqualTo(200);
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.body())) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("2027", "Inicial", "Menores de 1 año", "Topic initial", "2027-03-05")
          .doesNotContain("Topic one", "null", "0.° grado");
    }
    var xlsx = get("/api/exports/monthly?contextId=initial&month=3&format=xlsx");
    try (var book =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(
            new java.io.ByteArrayInputStream(xlsx.body()))) {
      assertThat(book.getSheetAt(0).getRow(1).getCell(0).toString())
          .contains("Inicial", "Menores de 1 año");
    }
    assertThat(
            get("/api/exports/monthly?contextId=initial&areaId=aone&month=3&format=pdf")
                .statusCode())
        .isEqualTo(404);
    assertThat(get("/api/exports/backup?contextId=initial&areaId=aone").statusCode())
        .isEqualTo(404);
    assertThat(get("/api/exports/monthly?contextId=two&month=3&format=pdf").statusCode())
        .isEqualTo(404);
    assertThat(get("/api/exports/backup?contextId=two&format=full-v4").statusCode()).isEqualTo(404);
  }

  @Test
  void fullV4SnapshotIncludesEveryContextAndYearWithoutRedefiningV3() throws Exception {
    contextFixture("primary2026", 2026, "PRIMARY", 3);
    contextFixture("primary2027", 2027, "PRIMARY", 3);
    contextFixture("secondary", 2028, "SECONDARY", 5);
    var response = get("/api/exports/backup?contextId=primary2027&format=full-v4");
    assertThat(response.statusCode()).isEqualTo(200);
    var tree = json.readTree(response.body());
    assertThat(tree.get("version").asInt()).isEqualTo(4);
    assertThat(tree.get("scope").asText()).isEqualTo("complete-school-all-contexts");
    assertThat(tree.get("contexts").size()).isEqualTo(4);
    String body = tree.toString();
    assertThat(body)
        .contains("2026-03-02", "2026-03-05", "2027-03-05", "2028-03-05", "Custom topic")
        .doesNotContain("password", "synthetic-password", "Topic two", "preserved-note-two");
    for (var context : tree.get("contexts")) {
      String id = context.get("context").get("id").asText();
      for (String collection :
          List.of("areas", "topics", "sessions", "schedules", "blocks", "history"))
        for (var item : context.get(collection))
          assertThat(item.get("contextId").asText()).isEqualTo(id);
    }
    var v3 = json.readTree(get("/api/exports/backup?format=full").body());
    assertThat(v3.get("version").asInt()).isEqualTo(3);
    assertThat(v3.toString()).doesNotContain("primary2027", "Topic secondary");
  }

  @Test
  void rejectsInvalidMonthAndFormat() throws Exception {
    assertThat(get("/api/exports/monthly?month=13&format=pdf").statusCode()).isEqualTo(400);
    assertThat(get("/api/exports/monthly?month=0&format=xlsx").statusCode()).isEqualTo(400);
    assertThat(get("/api/exports/monthly?month=3&format=xls").statusCode()).isEqualTo(400);
    assertThat(get("/api/exports/backup?format=html").statusCode()).isEqualTo(400);
  }
}
