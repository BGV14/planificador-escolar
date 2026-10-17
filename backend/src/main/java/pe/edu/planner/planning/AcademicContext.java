package pe.edu.planner.planning;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Stable API vocabulary; labels are presentation metadata, not tenant selectors. */
public record AcademicContext(int year, String level, int stageValue) {
  public AcademicContext {
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
    return year + " \u00b7 " + label + " \u00b7 " + stage;
  }
}
