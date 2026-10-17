package pe.edu.planner.exports;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class MonthlyExportTest {
  @Test
  void preservesLongNotesAndUnsupportedCharactersWithoutTruncating() throws Exception {
    String note = "x".repeat(1792) + "🙂" + "y".repeat(8000) + "END_OF_NOTE";
    var base = fixture();
    var entry =
        new MonthlyPlan.Entry(
            "LONG", "Área sintética", 1, "Tema Unicode 🙂", "2026-03-02", "Marzo", "", note);
    var plan =
        new MonthlyPlan(
            base.school(),
            base.year(),
            base.grade(),
            base.month(),
            base.area(),
            List.of(entry),
            List.of(),
            List.of());
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(new MonthlyWorkbook().write(plan)))) {
      var all = new StringBuilder();
      for (var row : book.getSheet("Anexos")) for (var cell : row) all.append(cell.toString());
      assertThat(all).contains(note);
    }
    try (var pdf = Loader.loadPDF(new MonthlyPdf().write(plan))) {
      String text = new PDFTextStripper().getText(pdf).replaceAll("\\s", "");
      assertThat(text).contains("[U+1F642]", "END_OF_NOTE");
    }
  }

  @Test
  void rendersAccurateHeadersForInitialPrimaryAndSecondaryWithoutLosingAppendices()
      throws Exception {
    var base = fixture();
    for (var context :
        List.of(
            new pe.edu.planner.planning.AcademicContext(2026, "INITIAL", 0),
            new pe.edu.planner.planning.AcademicContext(2026, "PRIMARY", 3),
            new pe.edu.planner.planning.AcademicContext(2026, "SECONDARY", 5))) {
      String name = "context-" + context.level().toLowerCase(java.util.Locale.ROOT);
      var plan =
          new MonthlyPlan(
              base.school(),
              context.year(),
              context.level().equals("INITIAL") ? null : context.stageValue(),
              3,
              base.area(),
              base.entries(),
              base.events(),
              base.schedules(),
              context.displayLabel());
      var directory = Path.of(".tools/export-qa");
      Files.createDirectories(directory);
      byte[] workbook = new MonthlyWorkbook().write(plan);
      Files.write(directory.resolve(name + ".xlsx"), workbook);
      try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
        assertThat(book.getSheetAt(0).getRow(1).getCell(0).toString())
            .contains(context.displayLabel())
            .doesNotContain("null");
        var all = new StringBuilder();
        for (var sheet : book)
          for (var row : sheet) for (var cell : row) all.append(cell.toString());
        for (var entry : base.entries())
          assertThat(all).contains(entry.topic(), entry.date(), entry.notes());
      }
      byte[] bytes = new MonthlyPdf().write(plan);
      Files.write(directory.resolve(name + ".pdf"), bytes);
      try (var pdf = Loader.loadPDF(bytes)) {
        assertThat(new PDFTextStripper().getText(pdf))
            .contains(context.displayLabel())
            .doesNotContain("null", "0.° grado");
        for (int page = 0; page < pdf.getNumberOfPages(); page++)
          ImageIO.write(
              new PDFRenderer(pdf).renderImageWithDPI(page, 90),
              "PNG",
              directory.resolve(name + "-page" + (page + 1) + ".png").toFile());
      }
    }
  }

  MonthlyPlan fixture() {
    var entries = new ArrayList<MonthlyPlan.Entry>();
    for (int i = 1; i <= 12; i++)
      entries.add(
          new MonthlyPlan.Entry(
              "T" + i,
              "Área sintética",
              1,
              "Tema largo de prueba " + i,
              "2026-03-02",
              "Abril",
              "",
              "Nota completa " + i));
    entries.add(
        new MonthlyPlan.Entry(
            "SAT", "Área sintética", 2, "Tema de sábado", "2026-03-07", "Marzo", "Trabajado", ""));
    entries.add(
        new MonthlyPlan.Entry(
            "SUN", "Área sintética", 2, "Tema de domingo", "2026-03-01", "Marzo", "", ""));
    entries.add(new MonthlyPlan.Entry("NO", "Área sintética", 3, "Sin fecha", "", "Marzo", "", ""));
    entries.add(
        new MonthlyPlan.Entry(
            "OUT", "Área sintética", 3, "Mes distinto", "2026-04-01", "Marzo", "", ""));
    return new MonthlyPlan(
        "Escuela sintética",
        2026,
        6,
        3,
        "Área sintética",
        entries,
        List.of(new MonthlyPlan.Event("EXAM", "Examen confirmado", "2026-03-02", "2026-03-02")),
        List.of("Lunes 08:00-09:30 · Área sintética"));
  }

  @Test
  void producesRealWorkbookWithWeekdayPairsAndUnlimitedRows() throws Exception {
    byte[] bytes = new MonthlyWorkbook().write(fixture());
    assertThat(bytes[0]).isEqualTo((byte) 'P');
    assertThat(bytes[1]).isEqualTo((byte) 'K');
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = book.getSheetAt(0);
      assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 10);
      assertThat(sheet.getMergedRegions().stream().map(Object::toString))
          .contains("org.apache.poi.ss.util.CellRangeAddress [A1:J1]");
      var all = new StringBuilder();
      for (var s : book) for (var r : s) for (var c : r) all.append(c.toString()).append('\n');
      for (int i = 1; i <= 12; i++) assertThat(all).contains("Tema largo de prueba " + i);
      assertThat(all)
          .contains(
              "2026-03-02",
              "Tema de sábado",
              "Tema de domingo",
              "Sin fecha",
              "Mes distinto",
              "Examen confirmado",
              "Horario",
              "CONFLICTO");
      assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("2026-02-23");
      assertThat(sheet.getLastRowNum()).isGreaterThan(35);
      assertThat(book.getSheet("Anexos")).isNotNull();
    }
    Files.createDirectories(Path.of(".tools/export-qa"));
    Files.write(Path.of(".tools/export-qa/synthetic.xlsx"), bytes);
  }

  @Test
  void producesPaginatedPdfPreservingEveryTopicAndExactDate() throws Exception {
    byte[] bytes = new MonthlyPdf().write(fixture());
    assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    try (var document = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(document);
      for (int i = 1; i <= 12; i++) assertThat(text).contains("Tema largo de prueba " + i);
      assertThat(text)
          .contains(
              "2026-03-02",
              "Tema de sábado",
              "Tema de domingo",
              "Sin fecha",
              "Mes distinto",
              "Examen confirmado",
              "CONFLICTO");
      assertThat(document.getNumberOfPages()).isGreaterThan(1);
      Files.createDirectories(Path.of(".tools/export-qa"));
      for (int page = 0; page < document.getNumberOfPages(); page++)
        ImageIO.write(
            new PDFRenderer(document).renderImageWithDPI(page, 90),
            "PNG",
            Path.of(".tools/export-qa/synthetic-page" + (page + 1) + ".png").toFile());
    }
    Files.write(Path.of(".tools/export-qa/synthetic.pdf"), bytes);
  }
}
