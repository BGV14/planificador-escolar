package pe.edu.planner.exports;

import java.io.*;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Native OOXML, never HTML disguised as a spreadsheet. */
public final class MonthlyWorkbook {
  public byte[] write(MonthlyPlan plan) throws IOException {
    try (var book = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var title = style(book, IndexedColors.DARK_TEAL, IndexedColors.WHITE, true);
      var header = style(book, IndexedColors.GREY_25_PERCENT, IndexedColors.DARK_TEAL, true);
      var normal = style(book, IndexedColors.WHITE, IndexedColors.BLACK, false);
      var warning = style(book, IndexedColors.LEMON_CHIFFON, IndexedColors.DARK_RED, false);
      var sheet = book.createSheet("Calendario");
      setup(sheet);
      merged(sheet, 0, 0, 9, plan.title(), title, 32);
      String metadata = plan.school() + " · ÁREA: " + plan.area() + " · " + plan.contextLabel();
      float metadataHeight = Math.max(34, (float) Math.ceil(metadata.length() / 110.0) * 13 + 12);
      merged(sheet, 1, 0, 7, metadata, normal, metadataHeight);
      merged(sheet, 1, 8, 9, MonthlyPlan.MONTHS.get(plan.month() - 1), header, metadataHeight);
      merged(
          sheet,
          2,
          0,
          9,
          "Fecha exacta prioritaria. Código + tema por día. Filas ampliables; fines de semana y"
              + " notas completas en Anexos.",
          normal,
          30);
      for (int d = 0; d < 5; d++)
        merged(
            sheet,
            3,
            d * 2,
            d * 2 + 1,
            MonthlyPlan.DAYS.get(d).toUpperCase(Locale.ROOT),
            title,
            25);
      int row = 4;
      LocalDate last =
          LocalDate.of(plan.year(), plan.month(), 1)
              .withDayOfMonth(YearMonth.of(plan.year(), plan.month()).lengthOfMonth());
      for (LocalDate monday = plan.firstMonday();
          !monday.isAfter(last);
          monday = monday.plusWeeks(1)) {
        var dayEntries = new ArrayList<List<MonthlyPlan.Entry>>();
        var dayBlocks = new ArrayList<List<MonthlyPlan.Event>>();
        int count = 4;
        for (int d = 0; d < 5; d++) {
          String date = monday.plusDays(d).toString();
          boolean inside = date.startsWith(plan.prefix());
          var entries = inside ? plan.on(date) : List.<MonthlyPlan.Entry>of();
          var blocks = inside ? plan.blocks(date) : List.<MonthlyPlan.Event>of();
          dayEntries.add(entries);
          dayBlocks.add(blocks);
          count = Math.max(count, entries.size() + blocks.size());
          merged(sheet, row, d * 2, d * 2 + 1, date, header, 25);
        }
        row++;
        for (int i = 0; i < count; i++) {
          var r = sheet.createRow(row++);
          int maxLines = 3;
          for (int d = 0; d < 5; d++) {
            String code = "", text = "";
            CellStyle cellStyle = normal;
            var blocks = dayBlocks.get(d);
            var entries = dayEntries.get(d);
            if (i < blocks.size()) {
              var b = blocks.get(i);
              code = b.kind();
              text = b.label() + (b.area().isEmpty() ? "" : " · " + b.area());
              cellStyle = warning;
            } else if (i - blocks.size() < entries.size()) {
              var e = entries.get(i - blocks.size());
              code = "U" + e.unit();
              text =
                  e.label()
                      + (plan.blocked(e) ? " · CONFLICTO CON BLOQUEO" : "")
                      + (e.monthConflict() ? " · CONFLICTO MES/FECHA" : "");
              cellStyle = plan.blocked(e) || e.monthConflict() ? warning : normal;
            }
            cell(r, d * 2, code, cellStyle);
            cell(r, d * 2 + 1, text, cellStyle);
            maxLines = Math.max(maxLines, (int) Math.ceil(text.length() / 23.0));
          }
          r.setHeightInPoints(Math.min(409, maxLines * 11 + 12));
        }
      }
      merged(
          sheet,
          row++,
          0,
          9,
          "LEYENDA · U = unidad · Pendiente / Trabajado · EXAM = exámenes · VACATION = vacaciones ·"
              + " HOLIDAY = feriado · OTHER = otro evento.",
          header,
          38);
      merged(
          sheet,
          row,
          0,
          9,
          "Sin recortes: cada semana tiene al menos 4 filas de contenido y crece según los datos."
              + " El anexo conserva fines de semana, diferencias y notas.",
          normal,
          35);
      sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
      book.setPrintArea(0, 0, 9, 0, row);
      var annex = book.createSheet("Anexos");
      setup(annex);
      int a = 0;
      merged(annex, a++, 0, 9, "ANEXOS · " + plan.title(), title, 32);
      a = paragraph(annex, a, "FINES DE SEMANA (fecha exacta conservada)", header);
      for (var e : plan.entries())
        if (e.date().startsWith(plan.prefix())
            && LocalDate.parse(e.date()).getDayOfWeek().getValue() > 5)
          a = paragraph(annex, a, e.date() + " · " + e.code() + " · " + e.label(), normal);
      a =
          paragraph(
              annex, a, "ADVERTENCIAS · sin fechas inferidas ni movimientos automáticos", header);
      if (plan.warnings().isEmpty()) a = paragraph(annex, a, "Sin advertencias.", normal);
      else for (String w : plan.warnings()) a = paragraph(annex, a, w, warning);
      a = paragraph(annex, a, "Horario semanal de referencia", header);
      for (String s : plan.schedules()) a = paragraph(annex, a, s, normal);
      a = paragraph(annex, a, "Bloqueos y eventos del mes (incluidos fines de semana)", header);
      for (var e : plan.events())
        a =
            paragraph(
                annex,
                a,
                e.start()
                    + " a "
                    + e.end()
                    + " · "
                    + e.kind()
                    + " · "
                    + e.label()
                    + " · "
                    + (e.area().isEmpty() ? "Toda la escuela" : e.area()),
                warning);
      a =
          paragraph(
              annex,
              a,
              "DETALLE COMPLETO · todos los temas seleccionados, con notas sin truncar",
              header);
      for (var e : plan.entries()) {
        a =
            paragraph(
                annex,
                a,
                e.code()
                    + " · "
                    + e.label()
                    + " · Fecha: "
                    + (e.date().isEmpty() ? "sin fecha" : e.date())
                    + " · Mes: "
                    + (e.assignment().isEmpty() ? "sin asignar" : e.assignment()),
                header);
        a = paragraph(annex, a, "Notas: " + e.notes(), normal);
      }
      book.setPrintArea(1, 0, 9, 0, a);
      book.write(output);
      return output.toByteArray();
    }
  }

  private static void setup(Sheet s) {
    for (int c = 0; c < 10; c++) s.setColumnWidth(c, (c % 2 == 0 ? 9 : 28) * 256);
    s.createFreezePane(0, 4);
    s.setFitToPage(true);
    s.getPrintSetup().setLandscape(true);
    s.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);
    s.getPrintSetup().setFitWidth((short) 1);
    s.getPrintSetup().setFitHeight((short) 0);
    s.setAutobreaks(true);
    s.getFooter().setRight("Página &P de &N");
  }

  private static CellStyle style(
      Workbook b, IndexedColors background, IndexedColors foreground, boolean bold) {
    var s = b.createCellStyle();
    s.setFillForegroundColor(background.getIndex());
    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    s.setWrapText(true);
    s.setVerticalAlignment(VerticalAlignment.TOP);
    s.setBorderBottom(BorderStyle.HAIR);
    s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    var f = b.createFont();
    f.setFontName("Arial");
    f.setFontHeightInPoints((short) 10);
    f.setBold(bold);
    f.setColor(foreground.getIndex());
    s.setFont(f);
    return s;
  }

  private static void cell(Row r, int column, String value, CellStyle style) {
    var c = r.createCell(column, value.isEmpty() ? CellType.BLANK : CellType.STRING);
    if (!value.isEmpty()) c.setCellValue(value);
    c.setCellStyle(style);
  }

  private static void merged(
      Sheet s, int row, int start, int end, String text, CellStyle style, float height) {
    var r = s.getRow(row);
    if (r == null) r = s.createRow(row);
    for (int c = start; c <= end; c++) cell(r, c, c == start ? text : "", style);
    s.addMergedRegion(new CellRangeAddress(row, row, start, end));
    r.setHeightInPoints(height);
  }

  private static int paragraph(Sheet s, int row, String text, CellStyle style) {
    if (text.isEmpty()) {
      merged(s, row++, 0, 9, "", style, 25);
      return row;
    }
    float fontPoints = s.getWorkbook().getFontAt(style.getFontIndex()).getFontHeightInPoints();
    float lineHeight = Math.max(15, fontPoints * 1.5f);
    int maxLines = (int) ((390 - 12) / lineHeight);
    // Deliberately conservative: four points per Excel column-width unit and two ems
    // per code point allow wide fallback glyphs and word-wrap slack without font discovery.
    double width = -16;
    for (int column = 0; column < 10; column++) width += s.getColumnWidth(column) / 256.0 * 4;
    double glyphWidth = fontPoints * 2;
    int start = 0, index = 0, lines = 1;
    double lineWidth = 0;
    boolean previousCr = false;
    while (index < text.length()) {
      int cp = text.codePointAt(index);
      int nextLines = lines;
      double nextWidth = lineWidth;
      if (cp == '\r' || cp == '\n') {
        if (cp == '\r' || !previousCr) nextLines++;
        nextWidth = 0;
      } else {
        double cpWidth = glyphWidth * (cp == '\t' ? 4 : 1);
        if (lineWidth > 0 && lineWidth + cpWidth > width) {
          nextLines++;
          nextWidth = 0;
        }
        nextWidth += cpWidth;
      }
      if (nextLines > maxLines) {
        merged(s, row++, 0, 9, text.substring(start, index), style, lines * lineHeight + 12);
        start = index;
        lines = 1;
        lineWidth = 0;
        previousCr = false;
        continue;
      }
      lines = nextLines;
      lineWidth = nextWidth;
      previousCr = cp == '\r';
      index += Character.charCount(cp);
    }
    merged(s, row++, 0, 9, text.substring(start), style, lines * lineHeight + 12);
    return row;
  }
}
