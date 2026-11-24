/*
 * Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package l9g.account.info.db.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test for the database schema derived from the JPA entities.
 * <p>
 * The database structure must not change across framework upgrades: production
 * runs with {@code spring.jpa.hibernate.ddl-auto: update}, so any difference in
 * the mapping (column types, lengths, nullability, check constraints, the
 * generated names of unique constraints) would be applied to the live database
 * on the next start.
 * <p>
 * The test lets Hibernate generate the {@code create} script for all entities
 * offline (no database connection), with the naming strategies taken from
 * Spring Boot's own {@link HibernateProperties} defaults, and compares it with
 * {@code src/test/resources/golden/schema-postgresql.sql}, recorded with
 * Hibernate 6.6 (Spring Boot 3.5) before the upgrade to Hibernate 7
 * (Spring Boot 4). The actual script is always written to
 * {@code target/golden/schema-postgresql.sql}.
 */
class SchemaGoldenTest
{
  private static final String GOLDEN = "/golden/schema-postgresql.sql";

  private static final Path ACTUAL = Path.of("target/golden/schema-postgresql.sql");

  @Test
  void schemaIsUnchanged()
    throws IOException
  {
    Files.createDirectories(ACTUAL.getParent());
    Files.deleteIfExists(ACTUAL);

    // naming strategies exactly as Spring Boot applies them by default
    // (Boot 3: CamelCaseToUnderscoresNamingStrategy, Boot 4:
    // PhysicalNamingStrategySnakeCaseImpl; both with SpringImplicitNamingStrategy)
    Map<String, Object> bootDefaults = new HibernateProperties()
      .determineHibernateProperties(Map.of(), new HibernateSettings());

    Map<String, Object> settings = new HashMap<>();
    settings.put("hibernate.physical_naming_strategy",
      bootDefaults.get("hibernate.physical_naming_strategy"));
    settings.put("hibernate.implicit_naming_strategy",
      bootDefaults.get("hibernate.implicit_naming_strategy"));
    // production runs on PostgreSQL only; Hibernate selects PostgreSQLDialect
    // from product name and version, as it does from the JDBC metadata at runtime
    settings.put("jakarta.persistence.database-product-name", "PostgreSQL");
    settings.put("jakarta.persistence.database-major-version", "18");
    settings.put("jakarta.persistence.database-minor-version", "0");
    settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
    settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
    settings.put("jakarta.persistence.schema-generation.scripts.create-target",
      ACTUAL.toString());
    settings.put("hibernate.format_sql", "false");
    assertThat(settings.get("hibernate.physical_naming_strategy")).isNotNull();
    assertThat(settings.get("hibernate.implicit_naming_strategy")).isNotNull();

    StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
      .applySettings(settings)
      .build();
    List<String> uniqueKeys = new ArrayList<>();
    try
    {
      Metadata metadata = new MetadataSources(registry)
        .addAnnotatedClass(SdbSecretData.class)
        .addAnnotatedClass(SdbProperty.class)
        .addAnnotatedClass(SdbVaultAdminKey.class)
        .addAnnotatedClass(SdbLastSeen.class)
        .buildMetadata();

      SchemaManagementToolCoordinator.process(metadata, registry, settings, null);

      // the unique constraints are emitted inline without their names, but
      // ddl-auto=update identifies them by name (drop + recreate on mismatch)
      for(Table table : metadata.collectTableMappings())
      {
        for(UniqueKey key : table.getUniqueKeys().values())
        {
          uniqueKeys.add("-- unique key " + table.getName() + "." + key.getName()
            + " (" + key.getColumns().stream().map(Column::getName)
              .collect(Collectors.joining(", ")) + ")");
        }
      }
    }
    finally
    {
      StandardServiceRegistryBuilder.destroy(registry);
    }

    // one statement per line, sorted: the order Hibernate emits statements in
    // is not part of the schema
    List<String> actual = new ArrayList<>(Arrays.stream(
      Files.readString(ACTUAL, StandardCharsets.UTF_8).split(";"))
      .map(String::strip)
      .filter(statement -> ! statement.isEmpty())
      .toList());
    actual.addAll(uniqueKeys);
    actual.sort(null);
    Files.write(ACTUAL, actual, StandardCharsets.UTF_8);

    assertThat(normalized(actual)).containsExactlyElementsOf(normalized(readGolden()));
  }

  /**
   * Removes the redundant parentheses Hibernate 7 puts around a check
   * constraint ({@code check ((type in (...)))} instead of Hibernate 6's
   * {@code check (type in (...))}). Both render the same constraint, and
   * {@code ddl-auto=update} never touches the check constraints of existing
   * tables; the column types, lengths, nullability and constraint names are
   * compared verbatim.
   */
  private static List<String> normalized(List<String> statements)
  {
    return statements.stream()
      .map(statement -> statement.replaceAll(
        "check \\(\\(([^()]*\\([^()]*\\))\\)\\)", "check ($1)"))
      .toList();
  }

  private static List<String> readGolden()
    throws IOException
  {
    try(InputStream in = SchemaGoldenTest.class.getResourceAsStream(GOLDEN))
    {
      assertThat(in)
        .as("golden file %s missing; review %s and copy it there", GOLDEN, ACTUAL)
        .isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }
  }

}
