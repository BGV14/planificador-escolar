package pe.edu.planner.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.servlet.session.cookie.secure=true")
@ActiveProfiles("test")
class StaticSecurityTest {
  private static final Path STATIC_ROOT = createStaticRoot();
  private final HttpClient client = HttpClient.newHttpClient();
  @LocalServerPort int port;

  private static Path createStaticRoot() {
    try {
      Path root = Files.createTempDirectory("planner-static-security-");
      Files.createDirectory(root.resolve("assets"));
      Files.writeString(
          root.resolve("index.html"), "<!doctype html><title>Synthetic login shell</title>");
      Files.writeString(root.resolve("assets/app.js"), "console.log('synthetic static asset');");
      return root;
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void staticResources(DynamicPropertyRegistry properties) {
    properties.add("spring.web.resources.static-locations", () -> STATIC_ROOT.toUri().toString());
  }

  @AfterAll
  static void deleteStaticRoot() throws IOException {
    Files.delete(STATIC_ROOT.resolve("assets/app.js"));
    Files.delete(STATIC_ROOT.resolve("assets"));
    Files.delete(STATIC_ROOT.resolve("index.html"));
    Files.delete(STATIC_ROOT);
  }

  @Test
  void permitsOnlyFrontendReadsWithoutOpeningApiOrOtherPaths() throws Exception {
    assertThat(request("GET", "/").statusCode()).isEqualTo(200);
    assertThat(request("GET", "/index.html").body()).contains("Synthetic login shell");
    assertThat(request("GET", "/assets/app.js").statusCode()).isEqualTo(200);
    assertThat(request("HEAD", "/assets/app.js").statusCode()).isEqualTo(200);
    assertThat(request("GET", "/api/me").statusCode()).isEqualTo(401);
    assertThat(request("GET", "/api/exports/backup?format=full").statusCode()).isEqualTo(401);
    assertThat(request("GET", "/internal").statusCode()).isEqualTo(401);
    // Anonymous error dispatches can be challenged as 401; neither mutation is allowed.
    assertThat(request("POST", "/index.html").statusCode()).isIn(401, 403);
    assertThat(request("POST", "/api/login").statusCode()).isIn(401, 403);
  }

  @Test
  void retainsSecureHttpOnlyStrictSessionCookieAndCsrfEndpoint() throws Exception {
    var response = request("GET", "/api/csrf");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("token", "headerName");
    assertThat(response.headers().allValues("set-cookie"))
        .anySatisfy(cookie -> assertThat(cookie).contains("Secure", "HttpOnly", "SameSite=Strict"));
  }

  private HttpResponse<String> request(String method, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
