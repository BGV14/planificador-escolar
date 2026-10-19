package pe.edu.planner.progress;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.planner.auth.PlannerPrincipal;
import pe.edu.planner.planning.AcademicContext;

/**
 * Servicio para consultar el avance anual de los temas por área y estado dentro de un contexto académico.
 * Utiliza datos sintéticos para simular la integración de T1 (catálogo anual) sin alterar datos productivos.
 */
@Service
@Transactional(readOnly = true)
public class AnnualProgressService {

  private final JdbcTemplate db;

  public AnnualProgressService(JdbcTemplate db) {
    this.db = db;
  }

  public AnnualProgressDto getAnnualProgress(
      PlannerPrincipal user, String contextId, String areaId, TopicAnnualStatus status) {
    if (contextId == null || contextId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit contextId required");
    }

    // Validar contexto académico para la escuela del usuario autenticado
    var rows =
        db.queryForList(
            "select * from academic_contexts where school_id=? and id=?",
            user.schoolId(),
            contextId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic context not found");
    }

    var contextRow = rows.getFirst();
    int year = ((Number) contextRow.get("school_year")).intValue();
    String level = contextRow.get("level").toString();
    int stageValue = ((Number) contextRow.get("stage_value")).intValue();

    var academicContext = new AcademicContext(year, level, stageValue);
    String displayLabel = academicContext.displayLabel();

    // Generación sintética simulando integración T1 (catálogo anual)
    List<AnnualTopicItemDto> allTopics = generateSyntheticAnnualTopics(contextId, year);

    // Calcular resumen global del contexto (denominador anual constante)
    int totalTopics = allTopics.size();
    int completedTopics =
        (int) allTopics.stream().filter(t -> t.status() == TopicAnnualStatus.COMPLETADO).count();
    int plannedPendingTopics =
        (int)
            allTopics.stream()
                .filter(t -> t.status() == TopicAnnualStatus.PLANIFICADO_PENDIENTE)
                .count();
    int unplannedTopics =
        (int) allTopics.stream().filter(t -> t.status() == TopicAnnualStatus.NO_PLANIFICADO).count();

    double completionPercentage =
        totalTopics > 0 ? Math.round((completedTopics * 100.0 / totalTopics) * 10.0) / 10.0 : 0.0;

    AnnualProgressSummaryDto summary =
        new AnnualProgressSummaryDto(
            totalTopics, completedTopics, plannedPendingTopics, unplannedTopics, completionPercentage);

    // Resumen por áreas (constante por contexto)
    Map<String, List<AnnualTopicItemDto>> topicsByArea =
        allTopics.stream().collect(Collectors.groupingBy(AnnualTopicItemDto::areaId, LinkedHashMap::new, Collectors.toList()));

    List<AnnualProgressAreaSummaryDto> areaSummaries = new ArrayList<>();
    topicsByArea.forEach(
        (aId, topicsInArea) -> {
          String areaName = topicsInArea.getFirst().areaName();
          int areaTotal = topicsInArea.size();
          int areaCompleted =
              (int)
                  topicsInArea.stream()
                      .filter(t -> t.status() == TopicAnnualStatus.COMPLETADO)
                      .count();
          int areaPlanned =
              (int)
                  topicsInArea.stream()
                      .filter(t -> t.status() == TopicAnnualStatus.PLANIFICADO_PENDIENTE)
                      .count();
          int areaUnplanned =
              (int)
                  topicsInArea.stream()
                      .filter(t -> t.status() == TopicAnnualStatus.NO_PLANIFICADO)
                      .count();

          areaSummaries.add(
              new AnnualProgressAreaSummaryDto(
                  aId, areaName, areaTotal, areaCompleted, areaPlanned, areaUnplanned));
        });

    // Aplicar filtros sobre la lista de temas devuelta (sin alterar los conteos del summary general)
    List<AnnualTopicItemDto> filteredTopics =
        allTopics.stream()
            .filter(t -> areaId == null || areaId.isBlank() || t.areaId().equalsIgnoreCase(areaId))
            .filter(t -> status == null || t.status() == status)
            .toList();

    return new AnnualProgressDto(
        contextId,
        displayLabel,
        year,
        level,
        stageValue,
        summary,
        areaSummaries,
        filteredTopics);
  }

  /**
   * Genera temas anuales sintéticos que modelan el catálogo anual de T1 con estados excluyentes:
   * COMPLETADO, PLANIFICADO_PENDIENTE, NO_PLANIFICADO.
   */
  private List<AnnualTopicItemDto> generateSyntheticAnnualTopics(String contextId, int year) {
    List<AnnualTopicItemDto> list = new ArrayList<>();

    // 1. Aritmética
    String a1 = "area-aritmetica";
    String n1 = "Aritmética";
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-1",
            "Conjuntos y determinación",
            a1,
            n1,
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            year + "-03-12",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-2",
            "Operaciones entre conjuntos",
            a1,
            n1,
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            year + "-03-24",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-3",
            "Sistema de numeración decimal",
            a1,
            n1,
            2,
            TopicAnnualStatus.COMPLETADO,
            "Abril",
            year + "-04-10",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-4",
            "Operaciones combinadas con números naturales",
            a1,
            n1,
            3,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Mayo",
            null,
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-5",
            "Múltiplos, divisores y números primos",
            a1,
            n1,
            4,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Junio",
            null,
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-arit-6",
            "Fracciones y números mixtos",
            a1,
            n1,
            5,
            TopicAnnualStatus.NO_PLANIFICADO,
            null,
            null,
            true));

    // 2. Álgebra y Estadística
    String a2 = "area-algebra";
    String n2 = "Álgebra y Estadística";
    list.add(
        new AnnualTopicItemDto(
            contextId + "-alg-1",
            "Proposiciones lógicas y conectivos",
            a2,
            n2,
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            year + "-03-18",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-alg-2",
            "Polinomios y valor numérico",
            a2,
            n2,
            2,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Abril",
            year + "-04-22",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-alg-3",
            "Ecuaciones e inecuaciones lineales",
            a2,
            n2,
            3,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Mayo",
            null,
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-alg-4",
            "Tablas de frecuencias y gráficos de barras",
            a2,
            n2,
            4,
            TopicAnnualStatus.NO_PLANIFICADO,
            null,
            null,
            true));

    // 3. Geometría
    String a3 = "area-geometria";
    String n3 = "Geometría";
    list.add(
        new AnnualTopicItemDto(
            contextId + "-geo-1",
            "Nociones de Geometría y segmentos",
            a3,
            n3,
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            year + "-03-15",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-geo-2",
            "Ángulos y clasificación",
            a3,
            n3,
            2,
            TopicAnnualStatus.COMPLETADO,
            "Abril",
            year + "-04-15",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-geo-3",
            "Triángulos y propiedades fundamentales",
            a3,
            n3,
            3,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Junio",
            null,
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-geo-4",
            "Perímetros y áreas de polígonos",
            a3,
            n3,
            4,
            TopicAnnualStatus.NO_PLANIFICADO,
            null,
            null,
            true));

    // 4. Comunicación
    String a4 = "area-comunicacion";
    String n4 = "Comunicación";
    list.add(
        new AnnualTopicItemDto(
            contextId + "-com-1",
            "El texto narrativo y su estructura",
            a4,
            n4,
            1,
            TopicAnnualStatus.COMPLETADO,
            "Marzo",
            year + "-03-20",
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-com-2",
            "Categorías gramaticales: el sustantivo y adjetivo",
            a4,
            n4,
            2,
            TopicAnnualStatus.PLANIFICADO_PENDIENTE,
            "Mayo",
            null,
            true));
    list.add(
        new AnnualTopicItemDto(
            contextId + "-com-3",
            "Comprensión lectora: inferencias e ideas principales",
            a4,
            n4,
            3,
            TopicAnnualStatus.NO_PLANIFICADO,
            null,
            null,
            true));

    return list;
  }
}

