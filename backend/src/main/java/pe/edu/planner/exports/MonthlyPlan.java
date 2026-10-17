package pe.edu.planner.exports;

import java.time.*;
import java.util.*;

/** A tenant-filtered, immutable export snapshot. Exact dates always win over month labels. */
public record MonthlyPlan(
    String school,
    int year,
    Integer grade,
    int month,
    String area,
    List<Entry> entries,
    List<Event> events,
    List<String> schedules,
    String contextLabel) {
  /** Compatibility constructor for existing grade-based render fixtures. */
  public MonthlyPlan(
      String school,
      int year,
      int grade,
      int month,
      String area,
      List<Entry> entries,
      List<Event> events,
      List<String> schedules) {
    this(
        school,
        year,
        grade,
        month,
        area,
        entries,
        events,
        schedules,
        new pe.edu.planner.planning.AcademicContext(year, "PRIMARY", grade).displayLabel());
  }

  public static final List<String> MONTHS =
      List.of(
          "Enero",
          "Febrero",
          "Marzo",
          "Abril",
          "Mayo",
          "Junio",
          "Julio",
          "Agosto",
          "Setiembre",
          "Octubre",
          "Noviembre",
          "Diciembre");
  public static final List<String> DAYS =
      List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");

  public MonthlyPlan {
    entries = List.copyOf(entries);
    events = List.copyOf(events);
    schedules = List.copyOf(schedules);
    if (month < 1 || month > 12) throw new IllegalArgumentException("Invalid month");
  }

  public record Entry(
      String code,
      String area,
      int unit,
      String topic,
      String date,
      String assignment,
      String status,
      String notes) {
    public Entry {
      date = Objects.toString(date, "");
      assignment = Objects.toString(assignment, "");
      status = Objects.toString(status, "");
      notes = Objects.toString(notes, "");
    }

    public String label() {
      return area
          + " · U"
          + unit
          + " · "
          + topic
          + (status.equals("Trabajado") ? " [Trabajado]" : " [Pendiente]");
    }

    public boolean monthConflict() {
      return !date.isEmpty()
          && !assignment.isEmpty()
          && !MONTHS.get(LocalDate.parse(date).getMonthValue() - 1).equals(assignment);
    }
  }

  public record Event(String kind, String label, String start, String end, String area) {
    public Event(String kind, String label, String start, String end) {
      this(kind, label, start, end, "");
    }

    public boolean on(String date) {
      return start.compareTo(date) <= 0 && end.compareTo(date) >= 0;
    }
  }

  public String title() {
    return "PROGRAMACIÓN MENSUAL · " + MONTHS.get(month - 1) + " " + year;
  }

  public String prefix() {
    return "%04d-%02d".formatted(year, month);
  }

  public List<Entry> on(String date) {
    return entries.stream().filter(e -> e.date.equals(date)).toList();
  }

  public List<Event> blocks(String date) {
    return events.stream().filter(e -> e.on(date)).toList();
  }

  public boolean blocked(Entry entry) {
    return !entry.date.isEmpty()
        && events.stream()
            .anyMatch(e -> e.on(entry.date) && (e.area.isEmpty() || e.area.equals(entry.area)));
  }

  public LocalDate firstMonday() {
    LocalDate first = LocalDate.of(year, month, 1);
    return first.minusDays(first.getDayOfWeek().getValue() - 1);
  }

  public List<String> warnings() {
    var result = new ArrayList<String>();
    for (var e : entries) {
      if (e.date.isEmpty())
        result.add(
            "SIN FECHA · "
                + e.code
                + " · "
                + e.topic
                + " · Mes: "
                + (e.assignment.isEmpty() ? "sin asignar" : e.assignment));
      if (e.monthConflict())
        result.add(
            "CONFLICTO MES/FECHA · "
                + e.code
                + " · "
                + e.topic
                + " · Mes asignado: "
                + e.assignment
                + " · Fecha conservada: "
                + e.date);
      if (blocked(e))
        result.add("CONFLICTO CON BLOQUEO · " + e.code + " · " + e.topic + " · " + e.date);
    }
    return result;
  }
}
