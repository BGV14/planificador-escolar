package pe.edu.planner.exports;

import java.io.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Five weekday pairs with bounded rows and continuation pages, followed by complete appendices. */
public final class MonthlyPdf {
  public byte[] write(MonthlyPlan plan) throws IOException {
    try (var document = new PDDocument();
        var out = new ByteArrayOutputStream();
        var page = new Pages(document, plan)) {
      LocalDate last = YearMonth.of(plan.year(), plan.month()).atEndOfMonth();
      for (LocalDate monday = plan.firstMonday();
          !monday.isAfter(last);
          monday = monday.plusWeeks(1)) {
        page.newPage();
        page.paragraph("SEMANA DEL " + monday + " · lunes a viernes", true);
        var texts = new ArrayList<List<String>>();
        int rows = 4;
        for (int d = 0; d < 5; d++) {
          String date = monday.plusDays(d).toString();
          var values = new ArrayList<String>();
          if (date.startsWith(plan.prefix())) {
            for (var b : plan.blocks(date))
              values.add(
                  b.kind() + " | " + b.label() + (b.area().isEmpty() ? "" : " · " + b.area()));
            for (var e : plan.on(date))
              values.add(
                  "U"
                      + e.unit()
                      + " | "
                      + e.label()
                      + (plan.blocked(e) ? " · CONFLICTO CON BLOQUEO" : "")
                      + (e.monthConflict() ? " · CONFLICTO MES/FECHA" : ""));
          }
          texts.add(values);
          rows = Math.max(rows, values.size());
        }
        page.weekHeader(monday);
        for (int i = 0; i < rows; i++) {
          var cells = new ArrayList<String>();
          for (int d = 0; d < 5; d++) cells.add(i < texts.get(d).size() ? texts.get(d).get(i) : "");
          page.weekRow(cells, monday);
        }
      }
      page.newPage();
      page.paragraph("ANEXOS · fines de semana y advertencias", true);
      for (var e : plan.entries())
        if (e.date().startsWith(plan.prefix())
            && LocalDate.parse(e.date()).getDayOfWeek().getValue() > 5)
          page.paragraph(e.date() + " · " + e.code() + " · " + e.label(), false);
      page.paragraph("ADVERTENCIAS · no se infieren ni se mueven fechas", true);
      if (plan.warnings().isEmpty()) page.paragraph("Sin advertencias.", false);
      for (String w : plan.warnings()) page.paragraph(w, false);
      page.paragraph("Horario semanal de referencia", true);
      for (String s : plan.schedules()) page.paragraph(s, false);
      page.paragraph("Bloqueos y eventos del mes · incluidos fines de semana", true);
      for (var e : plan.events())
        page.paragraph(
            e.start()
                + " a "
                + e.end()
                + " · "
                + e.kind()
                + " · "
                + e.label()
                + " · "
                + (e.area().isEmpty() ? "Toda la escuela" : e.area()),
            false);
      page.paragraph("DETALLE COMPLETO · todos los temas seleccionados", true);
      for (var e : plan.entries()) {
        page.paragraph(
            e.code()
                + " · "
                + e.label()
                + " · Fecha: "
                + (e.date().isEmpty() ? "sin fecha" : e.date())
                + " · Mes: "
                + (e.assignment().isEmpty() ? "sin asignar" : e.assignment()),
            true);
        page.paragraph("Notas: " + e.notes(), false);
      }
      page.paragraph(
          "LEYENDA · U = unidad; EXAM = exámenes; VACATION = vacaciones; HOLIDAY = feriado; OTHER ="
              + " otro. Cada semana tiene 4 o más filas. Los fines de semana y todas las notas se"
              + " conservan en este anexo. Los caracteres fuera de la fuente PDF se representan sin"
              + " pérdida como [U+XXXX].",
          false);
      page.closeStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static final class Pages implements AutoCloseable {
    private static final float LEFT = 30, WIDTH = 782, BOTTOM = 38, LEADING = 11;
    private final PDDocument document;
    private final MonthlyPlan plan;
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream stream;
    private float y;
    private int number;

    Pages(PDDocument document, MonthlyPlan plan) {
      this.document = document;
      this.plan = plan;
    }

    void newPage() throws IOException {
      closeStream();
      var page = new PDPage(new PDRectangle(842, 595));
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      number++;
      var metadata =
          wrap(
              plan.school() + " · " + plan.area() + " · " + plan.contextLabel(), WIDTH, regular, 9);
      float bottom = 535 - metadata.size() * 11;
      stream.setNonStrokingColor(0.06f, 0.26f, 0.29f);
      stream.addRect(0, bottom, 842, 595 - bottom);
      stream.fill();
      text(LEFT, 565, plan.title(), bold, 15, 1, 1, 1);
      for (int i = 0; i < metadata.size(); i++)
        text(LEFT, 544 - i * 11, metadata.get(i), regular, 9, 0.84f, 0.94f, 0.92f);
      text(
          LEFT,
          20,
          "Fecha exacta prioritaria · sin reprogramación automática",
          regular,
          8,
          .4f,
          .45f,
          .5f);
      text(738, 20, "Página " + number, regular, 8, .4f, .45f, .5f);
      y = bottom - 22;
    }

    void paragraph(String value, boolean heading) throws IOException {
      PDFont font = heading ? bold : regular;
      var lines = wrap(value, WIDTH, font, 9);
      if (heading && y < 70) newPage();
      for (String line : lines) {
        if (y < BOTTOM + LEADING) newPage();
        text(LEFT, y, line, font, 9, .12f, .2f, .25f);
        y -= LEADING;
      }
      y -= heading ? 9 : 6;
    }

    void weekHeader(LocalDate monday) throws IOException {
      for (int d = 0; d < 5; d++) {
        float x = LEFT + d * WIDTH / 5;
        stream.setNonStrokingColor(.9f, .95f, .94f);
        stream.addRect(x, y - 30, WIDTH / 5, 30);
        stream.fill();
        text(
            x + 5,
            y - 11,
            MonthlyPlan.DAYS.get(d).toUpperCase(Locale.ROOT),
            bold,
            8,
            .12f,
            .32f,
            .34f);
        text(x + 5, y - 24, monday.plusDays(d).toString(), regular, 8, .3f, .4f, .45f);
      }
      y -= 30;
    }

    void weekRow(List<String> values, LocalDate monday) throws IOException {
      var codes = new ArrayList<String>();
      var columns = new ArrayList<List<String>>();
      int count = 3;
      for (String value : values) {
        int split = value.indexOf(" | ");
        String code = split < 0 ? "" : value.substring(0, split);
        String body = split < 0 ? value : value.substring(split + 3);
        codes.add(code);
        var lines = wrap(body, WIDTH / 5 - 40, regular, 8);
        columns.add(lines);
        count = Math.max(count, lines.size());
      }
      int offset = 0;
      while (offset < count) {
        if (y < BOTTOM + 45) {
          newPage();
          paragraph("SEMANA DEL " + monday + " · continuación", true);
          weekHeader(monday);
        }
        int capacity = Math.max(1, (int) ((y - BOTTOM - 10) / 10));
        int length = Math.min(count - offset, capacity);
        float height = length * 10 + 10;
        for (int d = 0; d < 5; d++) {
          float x = LEFT + d * WIDTH / 5;
          stream.setStrokingColor(.8f, .85f, .87f);
          stream.setLineWidth(.4f);
          stream.addRect(x, y - height, WIDTH / 5, height);
          stream.moveTo(x + 30, y);
          stream.lineTo(x + 30, y - height);
          stream.stroke();
          if (offset == 0) text(x + 3, y - 12, codes.get(d), bold, 5, .14f, .4f, .43f);
          for (int l = 0; l < length; l++)
            if (offset + l < columns.get(d).size())
              text(
                  x + 35,
                  y - 12 - l * 10,
                  columns.get(d).get(offset + l),
                  regular,
                  8,
                  .16f,
                  .24f,
                  .3f);
        }
        y -= height;
        offset += length;
      }
    }

    private List<String> wrap(String value, float width, PDFont font, float size)
        throws IOException {
      String safe = safe(value, font);
      var lines = new ArrayList<String>();
      for (String paragraph : safe.split("\n", -1)) {
        StringBuilder line = new StringBuilder();
        for (String token : paragraph.split("(?<=\\s)|(?=\\s)", -1)) {
          if (line.length() > 0
              && font.getStringWidth(line.toString() + token) / 1000 * size > width) {
            lines.add(line.toString());
            line.setLength(0);
          }
          if (font.getStringWidth(token) / 1000 * size <= width) {
            line.append(token);
          } else {
            // Unbroken identifiers and long notes still wrap without clipping or truncation.
            for (int i = 0; i < token.length(); i++) {
              char c = token.charAt(i);
              if (line.length() > 0
                  && font.getStringWidth(line.toString() + c) / 1000 * size > width) {
                lines.add(line.toString());
                line.setLength(0);
              }
              line.append(c);
            }
          }
        }
        lines.add(line.toString());
      }
      return lines;
    }

    private String safe(String value, PDFont font) {
      var result = new StringBuilder();
      value
          .codePoints()
          .forEach(
              cp -> {
                if (cp == '\n') {
                  result.append('\n');
                  return;
                }
                if (cp == '\r') return;
                if (cp == '\t') {
                  result.append("    ");
                  return;
                }
                String s = new String(Character.toChars(cp));
                try {
                  font.encode(s);
                  result.append(s);
                } catch (IllegalArgumentException | IOException unsupported) {
                  result
                      .append("[U+")
                      .append(Integer.toHexString(cp).toUpperCase(Locale.ROOT))
                      .append(']');
                }
              });
      return result.toString();
    }

    private void text(
        float x, float y, String value, PDFont font, float size, float r, float g, float b)
        throws IOException {
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(r, g, b);
      stream.newLineAtOffset(x, y);
      stream.showText(safe(value, font));
      stream.endText();
    }

    void closeStream() throws IOException {
      if (stream != null) {
        stream.close();
        stream = null;
      }
    }

    @Override
    public void close() throws IOException {
      closeStream();
    }
  }
}
