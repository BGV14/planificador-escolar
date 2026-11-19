package pe.edu.planner.progress;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The ordinary application must not register the synthetic progress endpoint. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnnualProgressProfileTest {
  @LocalServerPort int port;
  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void syntheticEndpointAndServiceAreAbsentForAnAuthenticatedSessionByDefault() throws Exception {
    assertThat(context.getBeansOfType(AnnualProgressController.class)).isEmpty();
    assertThat(context.getBeansOfType(AnnualProgressService.class)).isEmpty();
    assertThat(
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream()))
        .noneMatch(path -> path.startsWith("/api/progress"));
    String id = UUID.randomUUID().toString();
    db.update(
        "insert into schools(id,name,school_year,grade,revision) values(?,?,2026,6,0)",
        id,
        "Profile test");
    db.update(
        "insert into academic_contexts"
            + "(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)"
            + " values(?,?,2026,'PRIMARY','GRADE',6,0,true)",
        id,
        id);
    db.update(
        "insert into app_users(id,school_id,username,password_hash,role) values(?,?,?,?,?)",
        id,
        id,
        id,
        passwords.encode("synthetic-password"),
        "TEACHER");
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String base = "http://localhost:" + port;
    var csrfResponse =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var token = new ObjectMapper().readTree(csrfResponse.body());
    var login =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .header(token.get("headerName").asText(), token.get("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=" + id + "&password=synthetic-password"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(200);
    var me =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(me.statusCode()).isEqualTo(200);
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/progress/annual?contextId=" + id))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    // The absent route resolves to 404, then /error is denied by the existing fail-closed policy.
    assertThat(response.statusCode()).isEqualTo(403);
  }
}
