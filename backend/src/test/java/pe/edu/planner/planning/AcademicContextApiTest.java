package pe.edu.planner.planning;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AcademicContextApiTest extends PlannerApiTest {
  String context(int year, String level, int stage) throws Exception {
    var response =
        call(
            "POST",
            "/api/contexts",
            json.writeValueAsString(Map.of("year", year, "level", level, "stageValue", stage)));
    assertThat(response.statusCode()).isEqualTo(200);
    return body(response).get("id").asText();
  }

  @Test
  void createsAllLevelsAndEmptyIndependentFutureYears() throws Exception {
    String id = context(2031, "INITIAL", 0);
    var plan = body(call("GET", "/api/plan?contextId=" + id, null));
    assertThat(plan.get("year").asInt()).isEqualTo(2031);
    assertThat(plan.get("stageKind").asText()).isEqualTo("AGE");
    assertThat(plan.get("displayLabel").asText()).contains("Menores de 1 año");
    assertThat(body(call("GET", "/api/topics?contextId=" + id, null)).size()).isZero();
    context(2031, "PRIMARY", 6);
    context(2031, "SECONDARY", 5);
    assertThat(
            call("POST", "/api/contexts", "{\"year\":2031,\"level\":\"INITIAL\",\"stageValue\":6}")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            call(
                    "POST",
                    "/api/contexts",
                    "{\"year\":2031,\"level\":\"SECONDARY\",\"stageValue\":6}")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            call("POST", "/api/contexts", "{\"year\":99999,\"level\":\"PRIMARY\",\"stageValue\":1}")
                .statusCode())
        .isEqualTo(400);
    for (int age = 1; age <= 5; age++) {
      String next = context(2031, "INITIAL", age);
      var metadata = body(call("GET", "/api/plan?contextId=" + next, null));
      assertThat(metadata.get("cycle").asText()).isEqualTo(age <= 2 ? "I" : "II");
      assertThat(metadata.get("grade").isNull()).isTrue();
    }
    for (int grade = 1; grade <= 5; grade++) context(2031, "PRIMARY", grade);
    for (int grade = 1; grade <= 4; grade++) context(2031, "SECONDARY", grade);
    assertThat(
            call("POST", "/api/contexts", "{\"year\":2031,\"level\":\"PRIMARY\",\"stageValue\":6}")
                .statusCode())
        .isEqualTo(409);
    assertThat(call("GET", "/api/plan?contextId=", null).statusCode()).isEqualTo(400);
    login("teacher");
    assertThat(
            call("POST", "/api/contexts", "{\"year\":2032,\"level\":\"PRIMARY\",\"stageValue\":1}")
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void validatesEveryReferenceAndKeepsRevisionsAndDatesIndependent() throws Exception {
    String savedSession =
        body(call("POST", "/api/sessions", sessionBody(0, "2026-03-02"))).get("id").asText();
    String savedSchedule =
        body(call(
                "POST",
                "/api/schedules",
                json.writeValueAsString(
                    Map.of(
                        "areaId", area, "weekday", 1, "startTime", "09:00", "endTime", "10:00"))))
            .get("id")
            .asText();
    String savedBlock =
        body(call(
                "POST",
                "/api/blocks",
                "{\"label\":\"Exam\",\"kind\":\"EXAM\",\"startDate\":\"2026-03-10\",\"endDate\":\"2026-03-11\"}"))
            .get("id")
            .asText();
    String other = context(2032, "INITIAL", 0);
    String selected = "?contextId=" + other;
    assertThat(
            call(
                    "POST",
                    "/api/topics" + selected,
                    json.writeValueAsString(Map.of("areaId", area, "name", "Forged", "unit", 1)))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "PUT",
                    "/api/topics/" + topic + selected,
                    json.writeValueAsString(
                        Map.of(
                            "areaId", area, "name", "Forged", "unit", 1, "legacyId", "Math|1|0")))
                .statusCode())
        .isEqualTo(404);
    assertThat(call("DELETE", "/api/topics/" + topic + selected, null).statusCode()).isEqualTo(404);
    assertThat(call("POST", "/api/sessions" + selected, sessionBody(0, "2032-03-02")).statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "POST",
                    "/api/schedules" + selected,
                    json.writeValueAsString(
                        Map.of(
                            "areaId",
                            area,
                            "weekday",
                            1,
                            "startTime",
                            "09:00",
                            "endTime",
                            "10:00")))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "PUT",
                    "/api/areas/" + area + selected,
                    "{\"name\":\"Forged\",\"ownerId\":\"teacher\",\"sharedEdit\":false}")
                .statusCode())
        .isEqualTo(404);
    assertThat(call("DELETE", "/api/areas/" + area + selected, null).statusCode()).isEqualTo(404);
    assertThat(
            call("PUT", "/api/sessions/" + savedSession + selected, sessionBody(0, "2032-03-02"))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call("DELETE", "/api/sessions/" + savedSession + selected + "&version=0", null)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "PUT",
                    "/api/schedules/" + savedSchedule + selected,
                    json.writeValueAsString(
                        Map.of(
                            "areaId",
                            area,
                            "weekday",
                            1,
                            "startTime",
                            "09:00",
                            "endTime",
                            "10:00")))
                .statusCode())
        .isEqualTo(404);
    assertThat(call("DELETE", "/api/schedules/" + savedSchedule + selected, null).statusCode())
        .isEqualTo(404);
    String forgedBlock =
        json.writeValueAsString(
            Map.of(
                "areaId",
                area,
                "label",
                "Forged",
                "kind",
                "OTHER",
                "startDate",
                "2032-03-02",
                "endDate",
                "2032-03-02"));
    assertThat(call("POST", "/api/blocks" + selected, forgedBlock).statusCode()).isEqualTo(404);
    assertThat(call("PUT", "/api/blocks/" + savedBlock + selected, forgedBlock).statusCode())
        .isEqualTo(404);
    assertThat(call("DELETE", "/api/blocks/" + savedBlock + selected, null).statusCode())
        .isEqualTo(404);
    long old = body(call("GET", "/api/plan", null)).get("revision").asLong();
    var a =
        body(
            call(
                "POST",
                "/api/areas" + selected,
                "{\"name\":\"Math\",\"ownerId\":\"teacher\",\"sharedEdit\":false}"));
    var t =
        body(
            call(
                "POST",
                "/api/topics" + selected,
                json.writeValueAsString(
                    Map.of("areaId", a.get("id").asText(), "name", "Play", "unit", 1))));
    topic = t.get("id").asText();
    assertThat(call("POST", "/api/sessions" + selected, sessionBody(0, "2032-03-02")).statusCode())
        .isEqualTo(200);
    assertThat(call("POST", "/api/sessions" + selected, sessionBody(0, "2026-03-02")).statusCode())
        .isEqualTo(400);
    assertThat(body(call("GET", "/api/plan", null)).get("revision").asLong()).isEqualTo(old);
    assertThat(call("POST", "/api/imports/preview" + selected, "{\"json\":\"{}\"}").statusCode())
        .isEqualTo(400);
    login("foreign");
    assertThat(call("GET", "/api/plan" + selected, null).statusCode()).isEqualTo(404);
    assertThat(call("GET", "/api/topics" + selected, null).statusCode()).isEqualTo(404);
    assertThat(call("GET", "/api/imports" + selected, null).statusCode()).isEqualTo(404);
    assertThat(call("POST", "/api/imports/preview" + selected, "{\"json\":\"{}\"}").statusCode())
        .isEqualTo(404);
  }
  @Test
  void createsCanonicalIndependentSectionsAndRejectsInvalidNames() throws Exception {
    String a = section("A");
    String b = section(" b ");
    assertThat(a).isNotEqualTo(b);
    assertThat(body(call("GET", "/api/plan?contextId=" + b, null)).get("section").asText()).isEqualTo("B");
    assertThat(body(call("GET", "/api/plan?contextId=" + b, null)).get("displayLabel").asText()).contains("Sección B");
    assertThat(call("POST", "/api/contexts", sectionBody(" a ")).statusCode()).isEqualTo(409);
    for (String invalid : java.util.List.of("", " ", "1", "Á", "A-B", "ABCDEFGHIJKLMNOPQ"))
      assertThat(call("POST", "/api/contexts", sectionBody(invalid)).statusCode()).isEqualTo(400);
    String selected = "?contextId=" + b;
    assertThat(call("PUT", "/api/topics/" + topic + selected,
        json.writeValueAsString(Map.of("areaId", area, "name", "Forged", "unit", 1))).statusCode()).isEqualTo(404);
    assertThat(call("POST", "/api/sessions" + selected, sessionBody(0, "2035-03-02")).statusCode()).isEqualTo(404);
    assertThat(body(call("GET", "/api/topics?contextId=" + a, null)).size()).isZero();
    assertThat(body(call("GET", "/api/plan?contextId=" + a, null)).get("revision").asLong()).isZero();
  }

  @Test
  void legacyImportsOnlyWriteTheExplicitSection() throws Exception {
    String a = section("A"), b = section("B");
    for (String id : java.util.List.of(a, b)) {
      var newArea = body(call("POST", "/api/areas?contextId=" + id,
          "{\"name\":\"Math\",\"ownerId\":\"teacher\",\"sharedEdit\":false}"));
      assertThat(call("POST", "/api/topics?contextId=" + id, json.writeValueAsString(Map.of(
          "areaId", newArea.get("id").asText(), "name", "Sets", "unit", 1, "legacyId", "Math|1|0"))).statusCode()).isEqualTo(200);
    }
    var aTopic = body(call("GET", "/api/topics?contextId=" + a, null)).get(0);
    var bTopic = body(call("GET", "/api/topics?contextId=" + b, null)).get(0);
    topic = aTopic.get("id").asText();
    assertThat(call("POST", "/api/sessions?contextId=" + b,
        sessionBody(0, "2035-03-02")).statusCode()).isEqualTo(404);
    assertThat(call("PUT", "/api/topics/" + bTopic.get("id").asText() + "?contextId=" + b,
        json.writeValueAsString(Map.of("areaId", aTopic.get("areaId").asText(),
            "name", "Forged", "unit", 1))).statusCode()).isEqualTo(404);
    var before = body(call("GET", "/api/plan?contextId=" + a, null));
    for (String payload : java.util.List.of(
        "{\"notes\":{\"Math|1|0\":\"section B only\"}}",
        "{\"version\":2,\"year\":2035,\"grade\":6,\"state\":{\"status\":{\"Math|1|0\":\"Trabajado\"}}}")) {
      var preview = body(call("POST", "/api/imports/preview?contextId=" + b, json.writeValueAsString(Map.of("json", payload))));
      assertThat(call("POST", "/api/imports/confirm?contextId=" + b, json.writeValueAsString(Map.of(
          "json", payload, "revision", preview.get("revision").asLong(), "acknowledgeIncomingWins", true))).statusCode()).isEqualTo(200);
    }
    assertThat(body(call("GET", "/api/plan?contextId=" + a, null))).isEqualTo(before);
    assertThat(body(call("GET", "/api/imports?contextId=" + a, null)).size()).isZero();
    assertThat(body(call("GET", "/api/imports?contextId=" + b, null)).size()).isEqualTo(2);
    assertThat(call("GET", "/api/plan?contextId=" + b, null).body()).contains("section B only", "Trabajado");
  }

  @Test
  void concurrentCanonicalDuplicatesHaveOnlyOneWinner() throws Exception {
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
      for (String value : java.util.List.of("C", " c ")) {
        tasks.add(pool.submit(() -> {
          start.await();
          return call("POST", "/api/contexts", sectionBody(value)).statusCode();
        }));
      }
      start.countDown();
      assertThat(java.util.List.of(tasks.get(0).get(), tasks.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  String sectionBody(String section) throws Exception {
    return json.writeValueAsString(Map.of("year", 2035, "level", "PRIMARY", "stageValue", 6, "section", section));
  }

  String section(String section) throws Exception {
    var response = call("POST", "/api/contexts", sectionBody(section));
    assertThat(response.statusCode()).isEqualTo(200);
    return body(response).get("id").asText();
  }

}
