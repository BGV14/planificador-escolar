package pe.edu.planner.exports;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AppendixLayoutTest {
  @Test
  void splitsExplicitNewlinesIntoVisibleRowsWithoutChangingContent() throws Exception {
    String note = String.join("\n", Collections.nCopies(110, "Line 🙂")) + "\n\nEND";
    verifyVisible(note, "newlines-regression.xlsx");
  }

  @Test
  void splitsWideCodePointsLongWordsAndMixedLineEndingsWithoutLoss() throws Exception {
    String note =
        "W".repeat(2600)
            + "🙂".repeat(300)
            + "\r\n"
            + "界".repeat(3000)
            + "\n"
            + "\n".repeat(85)
            + "END";
    verifyVisible(note, "wrapped-regression.xlsx");
  }

  private void verifyVisible(String note, String filename) throws Exception {
    var entry = new MonthlyPlan.Entry("T", "Area", 1, "Topic", "2026-03-02", "Marzo", "", note);
    var plan = new MonthlyPlan("School", 2026, 6, 3, "Area", List.of(entry), List.of(), List.of());
    byte[] bytes = new MonthlyWorkbook().write(plan);
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = book.getSheet("Anexos");
      boolean notes = false;
      var joined = new StringBuilder();
      int rows = 0;
      for (Row row : sheet) {
        String value = row.getCell(0).getStringCellValue();
        if (value.startsWith("Notas: ")) notes = true;
        if (!notes) continue;
        rows++;
        joined.append(value);
        int visualLines = conservativeLines(value);
        assertThat(row.getHeightInPoints())
            .as("row %s must display all %s conservative lines", row.getRowNum() + 1, visualLines)
            .isGreaterThanOrEqualTo(visualLines * 15f + 12f)
            .isLessThanOrEqualTo(390f);
        assertThat(value).doesNotContain("\uFFFD");
        if (!value.isEmpty()) {
          assertThat(Character.isHighSurrogate(value.charAt(value.length() - 1))).isFalse();
          assertThat(Character.isLowSurrogate(value.charAt(0))).isFalse();
        }
      }
      assertThat(rows).isGreaterThan(1);
      assertThat(joined.toString()).isEqualTo("Notas: " + note);
      assertThat(book.getPrintArea(1)).contains("$A$1:$J$");
    }
    Files.createDirectories(Path.of(".tools/export-qa"));
    Files.write(Path.of(".tools/export-qa/" + filename), bytes);
  }

  private int conservativeLines(String value) {
    int lines = 1, columns = 0;
    boolean cr = false;
    for (int cp : value.codePoints().toArray()) {
      if (cp == '\r' || cp == '\n') {
        if (cp == '\r' || !cr) lines++;
        columns = 0;
        cr = cp == '\r';
        continue;
      }
      cr = false;
      int width = cp == '\t' ? 4 : 1;
      if (columns + width > 36 && columns > 0) {
        lines++;
        columns = 0;
      }
      columns += width;
    }
    return lines;
  }
}
