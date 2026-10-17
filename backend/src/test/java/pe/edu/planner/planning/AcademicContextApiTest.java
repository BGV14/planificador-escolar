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
}
