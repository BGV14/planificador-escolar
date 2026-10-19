package pe.edu.planner.progress;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.api.ApiErrors;
import pe.edu.planner.auth.PlannerPrincipal;

@ExtendWith(MockitoExtension.class)
class AnnualProgressControllerTest {

  private MockMvc mockMvc;

  @Mock private AnnualProgressService service;

  private final PlannerPrincipal principal =
      new PlannerPrincipal("u-1", "school-1", "teacher1", "password", "TEACHER");

  @BeforeEach
  void setUp() {
    HandlerMethodArgumentResolver principalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                || parameter.getParameterType().equals(PlannerPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return principal;
          }
        };

    AnnualProgressController controller = new AnnualProgressController(service);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiErrors())
            .setCustomArgumentResolvers(principalResolver)
            .build();
  }

  @Test
  void getAnnualProgressReturnsOkWithValidContext() throws Exception {
    AnnualProgressSummaryDto summary =
        new AnnualProgressSummaryDto(17, 6, 6, 5, 35.3);

    AnnualTopicItemDto topic1 =
        new AnnualTopicItemDto(
            "t1",
            "Conjuntos",
            "area-aritmetica",
            "Aritmética",
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            "2026-03-12",
            true);

    AnnualProgressAreaSummaryDto areaSummary =
        new AnnualProgressAreaSummaryDto("area-aritmetica", "Aritmética", 6, 2, 2, 2);

    AnnualProgressDto dto =
        new AnnualProgressDto(
            "ctx-1",
            "2026 · Primaria · 6° grado",
            2026,
            "PRIMARY",
            6,
            summary,
            List.of(areaSummary),
            List.of(topic1));

    when(service.getAnnualProgress(any(), eq("ctx-1"), isNull(), isNull()))
        .thenReturn(dto);

    mockMvc
        .perform(get("/api/progress/annual").param("contextId", "ctx-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.contextId").value("ctx-1"))
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.summary.totalTopics").value(17))
        .andExpect(jsonPath("$.summary.completedTopics").value(6))
        .andExpect(jsonPath("$.summary.completionPercentage").value(35.3))
        .andExpect(jsonPath("$.topics[0].name").value("Conjuntos"))
        .andExpect(jsonPath("$.topics[0].status").value("COMPLETADO"));
  }

  @Test
  void getAnnualProgressPassesFilterParameters() throws Exception {
    AnnualProgressSummaryDto summary =
        new AnnualProgressSummaryDto(17, 6, 6, 5, 35.3);

    AnnualProgressDto dto =
        new AnnualProgressDto(
            "ctx-1",
            "2026 · Primaria · 6° grado",
            2026,
            "PRIMARY",
            6,
            summary,
            List.of(),
            List.of());

    when(service.getAnnualProgress(any(), eq("ctx-1"), eq("area-aritmetica"), eq(TopicAnnualStatus.COMPLETADO)))
        .thenReturn(dto);

    mockMvc
        .perform(
            get("/api/progress/annual")
                .param("contextId", "ctx-1")
                .param("areaId", "area-aritmetica")
                .param("status", "COMPLETADO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.totalTopics").value(17))
        .andExpect(jsonPath("$.topics").isArray());

    verify(service)
        .getAnnualProgress(any(), eq("ctx-1"), eq("area-aritmetica"), eq(TopicAnnualStatus.COMPLETADO));
  }

  @Test
  void returns404WhenContextNotFound() throws Exception {
    when(service.getAnnualProgress(any(), eq("missing"), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic context not found"));

    mockMvc
        .perform(get("/api/progress/annual").param("contextId", "missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Academic context not found"));
  }

  @Test
  void returns400WhenMissingContextId() throws Exception {
    mockMvc
        .perform(get("/api/progress/annual"))
        .andExpect(status().isBadRequest());
  }
}
