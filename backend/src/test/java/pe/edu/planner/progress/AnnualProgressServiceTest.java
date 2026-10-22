package pe.edu.planner.progress;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;

@ExtendWith(MockitoExtension.class)
class AnnualProgressServiceTest {

  @Mock private JdbcTemplate db;

  private AnnualProgressService service;

  private final PlannerPrincipal user =
      new PlannerPrincipal("u-1", "school-1", "teacher1", "hash", "TEACHER");

  @BeforeEach
  void setUp() {
    service = new AnnualProgressService(db);
  }

  @Test
  void throwsBadRequestWhenContextIdIsBlankOrNull() {
    assertThatThrownBy(() -> service.getAnnualProgress(user, null, null, null))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          var rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });

    assertThatThrownBy(() -> service.getAnnualProgress(user, "   ", null, null))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          var rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
  }

  @Test
  void throwsNotFoundWhenContextDoesNotExistForSchool() {
    when(db.queryForList(anyString(), any(Object.class), any(Object.class)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.getAnnualProgress(user, "non-existent", null, null))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          var rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        });
  }

  @Test
  void returnsAnnualProgressWithCoherentDenominatorAndMutuallyExclusiveStates() {
    Map<String, Object> contextRow =
        Map.of(
            "id", "ctx-2026",
            "school_id", "school-1",
            "school_year", 2026,
            "level", "PRIMARY",
            "stage_kind", "GRADE",
            "stage_value", 6,
            "revision", 1,
            "legacy_default", true);

    when(db.queryForList(anyString(), eq("school-1"), eq("ctx-2026")))
        .thenReturn(List.of(contextRow));

    AnnualProgressDto progress = service.getAnnualProgress(user, "ctx-2026", null, null);

    assertThat(progress).isNotNull();
    assertThat(progress.contextId()).isEqualTo("ctx-2026");
    assertThat(progress.year()).isEqualTo(2026);
    assertThat(progress.topics()).isNotEmpty();

    // Verify Mutually Exclusive States: each topic has exactly one defined state
    for (AnnualTopicItemDto topic : progress.topics()) {
      assertThat(topic.status()).isNotNull();
      assertThat(
              topic.status() == TopicAnnualStatus.COMPLETADO
                  || topic.status() == TopicAnnualStatus.PLANIFICADO_PENDIENTE
                  || topic.status() == TopicAnnualStatus.NO_PLANIFICADO)
          .isTrue();

      if (topic.status() == TopicAnnualStatus.COMPLETADO) {
        assertThat(topic.assignment()).isNotBlank();
        assertThat(topic.exactDate()).isNotNull();
      } else if (topic.status() == TopicAnnualStatus.PLANIFICADO_PENDIENTE) {
        assertThat(topic.assignment()).isNotBlank();
      } else {
        assertThat(topic.status()).isEqualTo(TopicAnnualStatus.NO_PLANIFICADO);
        assertThat(topic.exactDate()).isNull();
      }
    }

    // Verify Coherence in counts:
    // Total topics must equal the sum of completed, planned pending, and unplanned topics
    var summary = progress.summary();
    assertThat(summary.totalTopics())
        .isEqualTo(
            summary.completedTopics()
                + summary.plannedPendingTopics()
                + summary.unplannedTopics());
    assertThat(summary.totalTopics()).isEqualTo(progress.topics().size());
  }

  @Test
  void filtersDoNotRedefineAnnualDenominator() {
    Map<String, Object> contextRow =
        Map.of(
            "id", "ctx-2026",
            "school_id", "school-1",
            "school_year", 2026,
            "level", "PRIMARY",
            "stage_kind", "GRADE",
            "stage_value", 6,
            "revision", 1,
            "legacy_default", true);

    when(db.queryForList(anyString(), eq("school-1"), eq("ctx-2026")))
        .thenReturn(List.of(contextRow));

    AnnualProgressDto unfiltered = service.getAnnualProgress(user, "ctx-2026", null, null);
    int constantTotalDenominator = unfiltered.summary().totalTopics();
    int constantCompleted = unfiltered.summary().completedTopics();

    // Apply filter by status = COMPLETADO
    AnnualProgressDto filteredByStatus =
        service.getAnnualProgress(user, "ctx-2026", null, TopicAnnualStatus.COMPLETADO);

    // The topic list is filtered
    assertThat(filteredByStatus.topics())
        .allMatch(t -> t.status() == TopicAnnualStatus.COMPLETADO);
    assertThat(filteredByStatus.topics().size()).isLessThanOrEqualTo(constantTotalDenominator);

    // CRITERION 4: The annual denominator and global summary MUST remain constant!
    assertThat(filteredByStatus.summary().totalTopics()).isEqualTo(constantTotalDenominator);
    assertThat(filteredByStatus.summary().completedTopics()).isEqualTo(constantCompleted);

    // Apply filter by non-existent area -> should yield empty topics list but keep denominator
    AnnualProgressDto emptyFiltered =
        service.getAnnualProgress(user, "ctx-2026", "non-existent-area", null);
    assertThat(emptyFiltered.topics()).isEmpty();
    assertThat(emptyFiltered.summary().totalTopics()).isEqualTo(constantTotalDenominator);
  }
}

