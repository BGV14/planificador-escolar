package pe.edu.planner.planning;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Stable API vocabulary; labels are presentation metadata, not tenant selectors. */
public record AcademicContext(int year, String level, int stageValue, String section) {
  public AcademicContext(int year, String level, int stageValue) {
    this(year, level, stageValue, "UNICA");
  }

  public AcademicContext {
    section = section == null ? "UNICA" : section.trim().toUpperCase(Locale.ROOT);
    if (!section.matches("[A-Z][A-Z0-9]{0,15}"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid section: use 1–16 ASCII letters or digits, beginning with a letter");
    boolean valid =
        year >= 1900
            && year <= 2200
            && level != null
            && switch (level) {
              case "INITIAL" -> stageValue >= 0 && stageValue <= 5;
              case "PRIMARY" -> stageValue >= 1 && stageValue <= 6;
              case "SECONDARY" -> stageValue >= 1 && stageValue <= 5;
              default -> false;
            };
    if (!valid)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid academic year, level or age/grade");
  }

  public String stageKind() {
    return level.equals("INITIAL") ? "AGE" : "GRADE";
  }

  public String cycle() {
    return level.equals("INITIAL") ? (stageValue <= 2 ? "I" : "II") : null;
  }

  public String displayLabel() {
    String stage =
        level.equals("INITIAL")
            ? (stageValue == 0
                ? "Menores de 1 a\u00f1o"
                : stageValue + (stageValue == 1 ? " a\u00f1o" : " a\u00f1os"))
            : stageValue + "\u00b0 grado";
    String label =
        switch (level) {
          case "INITIAL" -> "Inicial";
          case "PRIMARY" -> "Primaria";
          default -> "Secundaria";
        };
    String sectionLabel = section.equals("UNICA") ? "Sección única" : "Sección " + section;
    return year + " \u00b7 " + label + " \u00b7 " + stage + " \u00b7 " + sectionLabel;
  }
}
