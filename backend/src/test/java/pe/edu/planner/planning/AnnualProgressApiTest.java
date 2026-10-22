package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class AnnualProgressApiTest extends PlannerApiTest {

  @Test
  void requiresAuthentication() throws Exception {
    var anonymous = HttpClient.newHttpClient();
    var response =
        anonymous.send(
            HttpRequest.newBuilder(URI.create(url("/api/progress/annual?contextId=s1")))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void returns200OkForAuthenticatedTeacherWithSessionAndCsrfToken() throws Exception {
    // Iniciar sesión como docente y asegurar cabecera CSRF sincronizada
    login("teacher");
    assertThat(csrf).isNotBlank();

    var request =
        HttpRequest.newBuilder(URI.create(url("/api/progress/annual?contextId=s1")))
            .header("X-CSRF-TOKEN", csrf)
            .GET()
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    var json = body(response);
    assertThat(json.get("contextId").asText()).isEqualTo("s1");
    assertThat(json.get("summary").get("totalTopics").asInt()).isEqualTo(17);
    assertThat(json.get("summary").get("completedTopics").asInt()).isEqualTo(7);
  }

  @Test
  void accessibleForAdminRole() throws Exception {
    login("admin");
    var response = call("GET", "/api/progress/annual?contextId=s1", null);
    assertThat(response.statusCode()).isEqualTo(200);
    var json = body(response);
    assertThat(json.get("contextId").asText()).isEqualTo("s1");
  }

  @Test
  void doesNotRequireAreaOwnership() throws Exception {
    // El usuario "other" es docente en "s1", pero NO es dueño del área "Math" (cuyo dueño es "teacher").
    // La consulta de avance anual es de solo lectura y debe retornar 200 OK sin lanzar 403 Forbidden.
    login("other");
    var response = call("GET", "/api/progress/annual?contextId=s1", null);
    assertThat(response.statusCode()).isEqualTo(200);
    var json = body(response);
    assertThat(json.get("topics").size()).isGreaterThan(0);
  }

  @Test
  void filtersDoNotAlterGlobalDenominatorOverHttp() throws Exception {
    login("teacher");
    // Filter by area
    var response =
        call("GET", "/api/progress/annual?contextId=s1&areaId=area-aritmetica", null);
    assertThat(response.statusCode()).isEqualTo(200);
    var json = body(response);

    // Topics list is filtered to Aritmética (6 topics)
    assertThat(json.get("topics").size()).isEqualTo(6);
    for (var topic : json.get("topics")) {
      assertThat(topic.get("areaId").asText()).isEqualTo("area-aritmetica");
    }

    // CRITERION 4: Global denominator is still 17!
    assertThat(json.get("summary").get("totalTopics").asInt()).isEqualTo(17);
    assertThat(json.get("summary").get("completedTopics").asInt()).isEqualTo(7);

    // Filter by status COMPLETADO
    var statusResp =
        call("GET", "/api/progress/annual?contextId=s1&status=COMPLETADO", null);
    assertThat(statusResp.statusCode()).isEqualTo(200);
    var statusJson = body(statusResp);
    assertThat(statusJson.get("topics").size()).isEqualTo(7);
    for (var topic : statusJson.get("topics")) {
      assertThat(topic.get("status").asText()).isEqualTo("COMPLETADO");
    }
    assertThat(statusJson.get("summary").get("totalTopics").asInt()).isEqualTo(17);

    // Empty filter
    var emptyResp =
        call("GET", "/api/progress/annual?contextId=s1&areaId=non-existent-area", null);
    assertThat(emptyResp.statusCode()).isEqualTo(200);
    var emptyJson = body(emptyResp);
    assertThat(emptyJson.get("topics").size()).isEqualTo(0);
    assertThat(emptyJson.get("summary").get("totalTopics").asInt()).isEqualTo(17);
  }

  @Test
  void returns404ForNonExistentContext() throws Exception {
    login("teacher");
    var response = call("GET", "/api/progress/annual?contextId=non-existent-ctx", null);
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void rejectsForeignSchoolUser() throws Exception {
    // "foreign" pertenece a escuela "s2"; no debe tener acceso a contextos de "s1"
    login("foreign");
    var response = call("GET", "/api/progress/annual?contextId=s1", null);
    assertThat(response.statusCode()).isEqualTo(404);
  }
}
