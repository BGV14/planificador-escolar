package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Extends the cohort key without rebuilding contexts or their dependent rows. */
public class V4__classroom_sections extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Map<String, Set<String>> constraints = new LinkedHashMap<>();
    String sql =
        "select tc.constraint_name, kcu.column_name from information_schema.table_constraints tc "
            + "join information_schema.key_column_usage kcu on tc.constraint_catalog=kcu.constraint_catalog "
            + "and tc.constraint_schema=kcu.constraint_schema and tc.constraint_name=kcu.constraint_name "
            + "and tc.table_name=kcu.table_name where tc.table_schema=? and tc.table_name='academic_contexts' "
            + "and tc.constraint_type='UNIQUE'";
    try (PreparedStatement query = connection.prepareStatement(sql)) {
      query.setString(1, connection.getSchema());
      try (ResultSet rows = query.executeQuery()) {
        while (rows.next())
          constraints
              .computeIfAbsent(rows.getString(1), key -> new HashSet<>())
              .add(rows.getString(2));
      }
    }
    Set<String> oldKey = Set.of("school_id", "school_year", "level", "stage_value");
    var matches = constraints.entrySet().stream().filter(e -> e.getValue().equals(oldKey)).toList();
    // H2 DDL is not transactional: refuse unexpected metadata before the first alteration.
    if (matches.size() != 1)
      throw new SQLException("Expected exactly one four-column academic context unique constraint");
    String quote = connection.getMetaData().getIdentifierQuoteString().trim();
    if (quote.isEmpty())
      throw new SQLException("Database does not support quoted constraint identifiers");
    String identifier = quote + matches.getFirst().getKey().replace(quote, quote + quote) + quote;
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "alter table academic_contexts add column section varchar(16) default 'UNICA' not null");
      statement.execute("alter table academic_contexts drop constraint " + identifier);
      statement.execute(
          "alter table academic_contexts add constraint academic_context_section_key"
              + " unique(school_id,school_year,level,stage_value,section)");
    }
  }
}
