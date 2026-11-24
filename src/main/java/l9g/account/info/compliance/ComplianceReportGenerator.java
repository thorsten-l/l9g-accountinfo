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
package l9g.account.info.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import l9g.account.info.db.model.SdbSecretType;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates the release documents of the compliance process automatically
 * from the code base, without a database or a running application:
 * <ol>
 * <li><strong>Security audit</strong> ({@code security-audit-<version>.md}):
 * risk register, access-control matrix, encrypted columns, framework versions
 * from the SBOM and the dependency-check findings;</li>
 * <li><strong>Process description / record of processing activities</strong>
 * ({@code prozessbeschreibung-<version>.md}, Art. 30 GDPR): processing
 * activities, stored data types, data model and personal-data inventory;</li>
 * <li><strong>Data protection impact assessment</strong>
 * ({@code dsfa-<version>.md}, Art. 35 GDPR): personal-data inventory with
 * Art. 9 marking and the evaluated risk register.</li>
 * </ol>
 * <p>
 * Information sources:
 * <ul>
 * <li>the JPA entities, scanned reflectively including their
 * {@code @MappedSuperclass} hierarchy: table and column names, whether a column
 * is encrypted (L9G {@code EncryptedAttributeConverter}) and the
 * {@link PersonalData} annotations;</li>
 * <li>the {@link SdbSecretType} enum, described by
 * {@code docs/compliance/processing-activities.yaml};</li>
 * <li>{@code docs/compliance/processing-activities.yaml} and
 * {@code docs/compliance/risk-register.yaml}, maintained by hand;</li>
 * <li>the recorded security matrix
 * {@code src/test/resources/golden/security-matrix.txt}; the release build runs
 * {@code ClientSecurityConfigGoldenTest} before this generator, so the matrix
 * matches the code;</li>
 * <li>the CycloneDX SBOM and the OWASP dependency-check JSON report written by
 * the {@code release} profile into the output directory;</li>
 * <li>Markdown templates under {@code src/main/resources/compliance/*.md} with
 * {@code {{placeholder}}} variables substituted by {@link #render}.</li>
 * </ul>
 * <p>
 * The detailed, hand-written analyses stay in {@code ACCOUNT_COMPLIANCE.md},
 * {@code ACCOUNT_DSFA.md} and {@code ACCOUNT_PROZESS_BESCHREIBUNG.md}; the
 * generated documents are the per-release evidence and refer to them.
 * <p>
 * Plain {@code main} program (no Spring context), executed by the
 * {@code exec-maven-plugin} in the {@code release} profile with the project
 * directory as working directory:
 * {@code ComplianceReportGenerator <version> <outputDir> [<copyDir>]}. The
 * documents are in German because they are addressed to German data-protection
 * officers and auditors.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public class ComplianceReportGenerator
{
  /**
   * Root package scanned for {@code @Entity} classes.
   */
  public static final String BASE_PACKAGE = "l9g.account.info";

  /**
   * The generated documents; each has a template
   * {@code compliance/<name>.md} on the classpath.
   */
  public static final List<String> DOCUMENTS =
    List.of("security-audit", "prozessbeschreibung", "dsfa");

  /**
   * Reports of other plugins of the release profile that are versioned
   * together with the generated documents. Optional: a missing report is
   * skipped.
   */
  static final List<String> RELEASE_ARTIFACTS = List.of("sbom.json",
    "dependency-check-report.html", "dependency-check-report.json");

  static final Path PROCESSING_ACTIVITIES =
    Path.of("docs/compliance/processing-activities.yaml");

  static final Path RISK_REGISTER = Path.of("docs/compliance/risk-register.yaml");

  static final Path SECURITY_MATRIX =
    Path.of("src/test/resources/golden/security-matrix.txt");

  static final Path BUILD_INFO =
    Path.of("target/classes/META-INF/build-info.properties");

  /**
   * One persistent attribute of an entity.
   *
   * @param name Java field name
   * @param column database column name
   * @param encrypted {@code true} if the column uses an L9G encrypted converter
   * @param personalCategory {@link PersonalData#category()} or {@code null}
   * @param purpose {@link PersonalData#purpose()} or empty
   * @param specialCategory {@link PersonalData#specialCategory()}
   * @param type simple Java type name
   */
  public record FieldInfo(String name, String column, boolean encrypted,
    String personalCategory, String purpose, boolean specialCategory,
    String type)
    {
    boolean personal()
    {
      return personalCategory != null;
    }

  }

  /**
   * One JPA entity with its persistent attributes.
   *
   * @param name simple class name
   * @param table database table name
   * @param fields attributes, superclass attributes first
   */
  public record EntityInfo(String name, String table, List<FieldInfo> fields)
    {
    long encryptedCount()
    {
      return fields.stream().filter(FieldInfo::encrypted).count();
    }

    long personalCount()
    {
      return fields.stream().filter(FieldInfo::personal).count();
    }

  }

  /**
   * One evaluated entry of the risk register.
   *
   * @param id risk id, e.g. {@code R-01}
   * @param risk description
   * @param goals protection goals
   * @param likelihood residual likelihood 1..3
   * @param severity residual severity 1..3
   * @param measures implemented measures
   * @param planned planned measures, may be empty
   */
  public record Risk(String id, String risk, String goals, int likelihood,
    int severity, String measures, String planned)
    {
    /**
     * @return risk value likelihood × severity
     */
    public int value()
    {
      return likelihood * severity;
    }

    /**
     * @return {@code gering} (1–2), {@code mittel} (3–4) or {@code hoch} (6–9)
     */
    public String level()
    {
      int value = value();
      return value <= 2 ? "gering" : value <= 4 ? "mittel" : "hoch";
    }

  }

  /**
   * One vulnerability reported by the OWASP dependency-check.
   *
   * @param file scanned file (jar or file inside a jar)
   * @param packageId package URL of the vulnerable component
   * @param cve vulnerability id
   * @param severity severity as reported
   * @param score CVSS base score or empty
   * @param knownExploited listed in the CISA KEV catalogue
   */
  public record Finding(String file, String packageId, String cve,
    String severity, String score, boolean knownExploited)
    {
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /**
   * Command-line entry point used by the Maven release profile.
   *
   * @param args {@code [version] [outputDir] [copyDir]}; defaults are
   * {@code dev} and {@code target/compliance}. With {@code copyDir} the
   * documents and the {@link #RELEASE_ARTIFACTS} are additionally copied there
   * (e.g. {@code docs/releases/<version>}) to version them with the code.
   *
   * @throws IOException on I/O errors
   */
  public static void main(String[] args)
    throws IOException
  {
    String version = args.length > 0 ? args[0] : "dev";
    Path out = Path.of(args.length > 1 ? args[1] : "target/compliance");
    Map<String, String> files = new ComplianceReportGenerator().generate(version, out);
    System.out.println("Compliance documents written to "
      + out.toAbsolutePath() + ": " + files.keySet());
    if(args.length > 2)
    {
      Path copy = Path.of(args[2]);
      List<String> copied = copyToRelease(out, copy, files.keySet());
      System.out.println("Release documents copied to "
        + copy.toAbsolutePath() + ": " + copied);
    }
  }

  /**
   * Generates all {@link #DOCUMENTS} into {@code out}.
   *
   * @param version version used in file names and headings
   * @param out output directory (created if missing), also searched for
   * {@code sbom.json} and {@code dependency-check-report.json}
   *
   * @return file name → rendered Markdown, in generation order
   *
   * @throws IOException if a template cannot be read or a document not written
   */
  public Map<String, String> generate(String version, Path out)
    throws IOException
  {
    Files.createDirectories(out);
    List<EntityInfo> entities = scanEntities();
    Map<String, Object> processing = loadYaml(PROCESSING_ACTIVITIES);
    List<Risk> risks = readRisks(loadYaml(RISK_REGISTER));
    List<Finding> findings = readDependencyCheck(out.resolve("dependency-check-report.json"));
    Path sbom = Files.exists(out.resolve("sbom.json")) ? out.resolve("sbom.json")
      : Path.of("target/classes/META-INF/sbom/application.cdx.json");

    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("version", version);
    vars.put("date", LocalDate.now().toString());
    vars.put("javaVersion", buildInfo("build.java.version", "?"));
    vars.put("entityTable", entityTable(entities));
    vars.put("encryptedFields", encryptedFieldList(entities));
    vars.put("personalDataTable", personalDataTable(entities));
    vars.put("dataTypeTable", dataTypeTable(processing));
    vars.put("processingActivities", processingActivities(processing));
    vars.put("riskTable", riskTable(risks));
    vars.put("riskSummary", riskSummary(risks));
    vars.put("accessMatrix", accessMatrix(SECURITY_MATRIX));
    vars.put("sbomSummary", sbomSummary(sbom));
    vars.put("dependencyFindings", findingsTable(findings,
      Files.exists(out.resolve("dependency-check-report.json"))));

    Map<String, String> result = new LinkedHashMap<>();
    for(String document : DOCUMENTS)
    {
      String rendered = render(readResource("compliance/" + document + ".md"), vars);
      String name = document + "-" + version + ".md";
      Files.writeString(out.resolve(name), rendered, StandardCharsets.UTF_8);
      result.put(name, rendered);
    }
    return result;
  }

  /**
   * Copies the generated documents and every existing
   * {@link #RELEASE_ARTIFACTS release artifact} into the release directory,
   * replacing older copies.
   *
   * @param out directory holding documents and plugin reports
   * @param copy release directory (created if missing)
   * @param documents file names of the generated documents
   *
   * @return names of all copied files
   *
   * @throws IOException on I/O errors
   */
  static List<String> copyToRelease(Path out, Path copy,
    Collection<String> documents)
    throws IOException
  {
    Files.createDirectories(copy);
    List<String> names = new ArrayList<>(documents);
    names.addAll(RELEASE_ARTIFACTS);
    List<String> copied = new ArrayList<>();
    for(String name : names)
    {
      Path source = out.resolve(name);
      if(Files.isRegularFile(source))
      {
        Files.copy(source, copy.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        copied.add(name);
      }
    }
    return copied;
  }

  // ------------------------------------------------------------------ entities
  /**
   * Scans {@link #BASE_PACKAGE} for {@code @Entity} classes and describes
   * their persistent attributes, including those inherited from
   * {@code @MappedSuperclass} classes. Static and {@code @Transient} fields are
   * skipped.
   *
   * @return entities sorted by simple class name
   */
  public List<EntityInfo> scanEntities()
  {
    ClassPathScanningCandidateComponentProvider scanner =
      new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    List<EntityInfo> result = new ArrayList<>();
    for(var candidate : scanner.findCandidateComponents(BASE_PACKAGE))
    {
      try
      {
        result.add(describe(Class.forName(candidate.getBeanClassName())));
      }
      catch(ClassNotFoundException ex)
      {
        throw new IllegalStateException(ex);
      }
    }
    result.sort((a, b) -> a.name().compareTo(b.name()));
    return result;
  }

  static EntityInfo describe(Class<?> type)
  {
    List<Class<?>> hierarchy = new ArrayList<>();
    for(Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
    {
      hierarchy.add(0, c);
    }
    List<FieldInfo> fields = new ArrayList<>();
    for(Class<?> c : hierarchy)
    {
      for(Field field : c.getDeclaredFields())
      {
        if(Modifier.isStatic(field.getModifiers())
          || field.isAnnotationPresent(Transient.class))
        {
          continue;
        }
        Column column = field.getAnnotation(Column.class);
        Convert convert = field.getAnnotation(Convert.class);
        // detected by name to stay independent of the crypto library's API
        boolean encrypted = convert != null
          && convert.converter().getSimpleName().startsWith("Encrypted")
          && convert.converter().getSimpleName().endsWith("Converter");
        PersonalData personal = field.getAnnotation(PersonalData.class);
        fields.add(new FieldInfo(field.getName(),
          column != null &&  ! column.name().isEmpty()
          ? column.name() : columnName(field.getName()),
          encrypted,
          personal == null ? null : personal.category(),
          personal == null ? "" : personal.purpose(),
          personal != null && personal.specialCategory(),
          field.getType().getSimpleName()));
      }
    }
    Table table = type.getAnnotation(Table.class);
    return new EntityInfo(type.getSimpleName(),
      table != null &&  ! table.name().isEmpty()
      ? table.name() : columnName(type.getSimpleName()), fields);
  }

  /**
   * Physical column name as produced by Spring Boot's default naming
   * strategy: camel case to lower-case snake case ({@code createdBy} →
   * {@code created_by}).
   */
  static String columnName(String logicalName)
  {
    return logicalName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
  }

  private static String entityTable(List<EntityInfo> entities)
  {
    StringBuilder sb = new StringBuilder(
      "| Entity | Tabelle | Spalten | verschlüsselt | personenbezogen |\n"
      + "|---|---|---|---|---|\n");
    for(EntityInfo e : entities)
    {
      sb.append("| ").append(e.name()).append(" | `").append(e.table())
        .append("` | ").append(e.fields().size()).append(" | ")
        .append(e.encryptedCount()).append(" | ").append(e.personalCount())
        .append(" |\n");
    }
    return sb.toString();
  }

  private static String encryptedFieldList(List<EntityInfo> entities)
  {
    StringBuilder sb = new StringBuilder();
    for(EntityInfo e : entities)
    {
      List<String> columns = e.fields().stream().filter(FieldInfo::encrypted)
        .map(f -> "`" + f.column() + "`").toList();
      if( ! columns.isEmpty())
      {
        sb.append("- **").append(e.name()).append("** (`").append(e.table())
          .append("`): ").append(String.join(", ", columns)).append("\n");
      }
    }
    return sb.length() == 0 ? "_keine_\n" : sb.toString();
  }

  private static String personalDataTable(List<EntityInfo> entities)
  {
    StringBuilder sb = new StringBuilder(
      "| Tabelle | Spalte | Kategorie | Art. 9 | Zweck | Schutz |\n"
      + "|---|---|---|---|---|---|\n");
    for(EntityInfo e : entities)
    {
      for(FieldInfo f : e.fields())
      {
        if(f.personal())
        {
          sb.append("| `").append(e.table()).append("` | `").append(f.column())
            .append("` | ").append(f.personalCategory()).append(" | ")
            .append(f.specialCategory() ? "**ja**" : "nein").append(" | ")
            .append(f.purpose()).append(" | ")
            .append(f.encrypted() ? "AES-256 feldverschlüsselt"
              : "Klartext, Zugriffskontrolle").append(" |\n");
        }
      }
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------ YAML
  /**
   * Loads a YAML file relative to the working directory. A missing or
   * unreadable file yields an empty map, so document generation never fails
   * because of an optional input; the documents then say what is missing.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> loadYaml(Path path)
  {
    if( ! Files.exists(path))
    {
      return Map.of();
    }
    try(InputStream in = Files.newInputStream(path))
    {
      Object yaml = new Yaml().load(in);
      return yaml instanceof Map<?, ?> map ? (Map<String, Object>)map : Map.of();
    }
    catch(IOException ex)
    {
      return Map.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static String processingActivities(Map<String, Object> yaml)
  {
    if( ! (yaml.get("activities") instanceof List<?> list) || list.isEmpty())
    {
      return "_Keine Verarbeitungstätigkeiten definiert (`"
        + PROCESSING_ACTIVITIES + "`)._\n";
    }
    StringBuilder sb = new StringBuilder();
    int i = 1;
    for(Object o : list)
    {
      Map<String, Object> activity = (Map<String, Object>)o;
      sb.append("### 4.").append(i ++).append(" ").append(activity.get("name"))
        .append("\n\n");
      for(String key : List.of("purpose", "legalBasis", "dataSubjects",
        "dataCategories", "recipients", "transfer", "retention", "measures"))
      {
        Object value = activity.get(key);
        if(value != null)
        {
          sb.append("- **").append(label(key)).append(":** ")
            .append(joined(value)).append("\n");
        }
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * One row per {@link SdbSecretType}, described by the {@code dataTypes}
   * section of the processing activities. Types without a description are
   * flagged so a new enum value cannot slip into a release undocumented.
   */
  @SuppressWarnings("unchecked")
  static String dataTypeTable(Map<String, Object> yaml)
  {
    Map<String, Object> descriptions = yaml.get("dataTypes") instanceof Map<?, ?> m
      ? (Map<String, Object>)m : Map.of();
    StringBuilder sb = new StringBuilder(
      "| Datentyp (`SdbSecretType`) | Inhalt | Ablage | Art. 9 |\n|---|---|---|---|\n");
    for(SdbSecretType type : SdbSecretType.values())
    {
      if(descriptions.get(type.name()) instanceof Map<?, ?> d)
      {
        sb.append("| `").append(type.name()).append("` | ").append(d.get("content"))
          .append(" | ").append(d.get("storage")).append(" | ")
          .append(Boolean.TRUE.equals(d.get("specialCategory")) ? "**ja**" : "nein")
          .append(" |\n");
      }
      else
      {
        sb.append("| `").append(type.name())
          .append("` | **⚠ nicht beschrieben** – in `").append(PROCESSING_ACTIVITIES)
          .append("` ergänzen | ? | ? |\n");
      }
    }
    return sb.toString();
  }

  /**
   * Reads the {@code risks} list of the risk register.
   *
   * @param yaml the loaded risk register
   *
   * @return the risks in file order
   */
  @SuppressWarnings("unchecked")
  static List<Risk> readRisks(Map<String, Object> yaml)
  {
    List<Risk> risks = new ArrayList<>();
    if(yaml.get("risks") instanceof List<?> list)
    {
      for(Object o : list)
      {
        Map<String, Object> r = (Map<String, Object>)o;
        risks.add(new Risk(String.valueOf(r.get("id")),
          String.valueOf(r.get("risk")), joined(r.getOrDefault("goals", "")),
          ((Number)r.get("likelihood")).intValue(),
          ((Number)r.get("severity")).intValue(),
          String.valueOf(r.getOrDefault("measures", "")),
          String.valueOf(r.getOrDefault("planned", ""))));
      }
    }
    return risks;
  }

  private static String riskTable(List<Risk> risks)
  {
    if(risks.isEmpty())
    {
      return "_Kein Risikoregister vorhanden (`" + RISK_REGISTER + "`)._\n";
    }
    StringBuilder sb = new StringBuilder(
      "| ID | Risiko | Schutzziel | W | S | Wert | Stufe | Umgesetzt | Geplant |\n"
      + "|---|---|---|---|---|---|---|---|---|\n");
    for(Risk r : risks)
    {
      sb.append("| ").append(r.id()).append(" | ").append(r.risk()).append(" | ")
        .append(r.goals()).append(" | ").append(r.likelihood()).append(" | ")
        .append(r.severity()).append(" | ").append(r.value()).append(" | ")
        .append("hoch".equals(r.level()) ? "**hoch**" : r.level()).append(" | ")
        .append(r.measures()).append(" | ")
        .append(r.planned().isEmpty() ? "–" : r.planned()).append(" |\n");
    }
    return sb.toString();
  }

  static String riskSummary(List<Risk> risks)
  {
    Map<String, List<String>> byLevel = new LinkedHashMap<>();
    for(String level : List.of("hoch", "mittel", "gering"))
    {
      byLevel.put(level, new ArrayList<>());
    }
    risks.forEach(r -> byLevel.get(r.level()).add(r.id()));
    StringBuilder sb = new StringBuilder("| Stufe | Anzahl | Risiken |\n|---|---|---|\n");
    byLevel.forEach((level, ids) -> sb.append("| ").append(level).append(" | ")
      .append(ids.size()).append(" | ")
      .append(ids.isEmpty() ? "–" : String.join(", ", ids)).append(" |\n"));
    return sb.toString();
  }

  private static String label(String key)
  {
    return switch(key)
    {
      case "purpose" ->
        "Zweck";
      case "legalBasis" ->
        "Rechtsgrundlage (durch DSB zu bestätigen)";
      case "dataSubjects" ->
        "Betroffene";
      case "dataCategories" ->
        "Datenkategorien";
      case "recipients" ->
        "Empfänger";
      case "transfer" ->
        "Drittlandtransfer";
      case "retention" ->
        "Löschfrist";
      case "measures" ->
        "TOM";
      default ->
        key;
    };
  }

  private static String joined(Object value)
  {
    return value instanceof List<?> list
      ? String.join("; ", list.stream().map(String::valueOf).toList())
      : String.valueOf(value);
  }

  // ------------------------------------------------------------------ access matrix
  private static final Pattern MATRIX_LINE =
    Pattern.compile("^GET\\s+(\\S+)\\s+(\\S+)\\s+->\\s+(.*)$");

  /**
   * Columns of the access matrix: matrix user name → heading.
   */
  private static final Map<String, String> MATRIX_USERS = new LinkedHashMap<>();

  static
  {
    MATRIX_USERS.put("ANON", "anonym");
    MATRIX_USERS.put("NOROLE", "ohne Rolle");
    MATRIX_USERS.put("PUBLISHER", "PUBLISHER");
    MATRIX_USERS.put("TABADMIN", "TABADMIN");
    MATRIX_USERS.put("VAULTADMIN", "VAULTADMIN");
    MATRIX_USERS.put("AUDITADMIN", "AUDITADMIN");
    MATRIX_USERS.put("ADMIN", "ADMIN");
  }

  /**
   * Condenses the GET rows of the recorded security matrix into a role ×
   * path table. A cell is {@code ✓} if the request passes the security filter
   * chain, {@code Login} if an anonymous request is redirected to the login,
   * {@code –} if it is denied, or names the vault states in which it passes.
   *
   * @param matrix path of {@code security-matrix.txt}
   *
   * @return Markdown table, or a hint if the matrix is missing
   */
  static String accessMatrix(Path matrix)
  {
    if( ! Files.exists(matrix))
    {
      return "_Keine Security-Matrix vorhanden (`" + matrix + "`)._\n";
    }
    Map<String, Map<String, String>> rows = new LinkedHashMap<>();
    try
    {
      for(String line : Files.readAllLines(matrix, StandardCharsets.UTF_8))
      {
        if(line.startsWith("# response headers"))
        {
          // the header section that follows uses a different line format
          break;
        }
        Matcher m = MATRIX_LINE.matcher(line);
        if(m.matches() && MATRIX_USERS.containsKey(m.group(2)))
        {
          rows.computeIfAbsent(m.group(1), k -> new LinkedHashMap<>())
            .put(m.group(2), accessCell(m.group(3)));
        }
      }
    }
    catch(IOException ex)
    {
      return "_Security-Matrix nicht lesbar: " + ex.getMessage() + "_\n";
    }
    StringBuilder sb = new StringBuilder("| Pfad (GET) |");
    MATRIX_USERS.values().forEach(h -> sb.append(" ").append(h).append(" |"));
    sb.append("\n|---|");
    MATRIX_USERS.values().forEach(h -> sb.append("---|"));
    sb.append("\n");
    rows.forEach((path, cells) ->
    {
      sb.append("| `").append(path).append("` |");
      MATRIX_USERS.keySet().forEach(user -> sb.append(" ")
        .append(cells.getOrDefault(user, "?")).append(" |"));
      sb.append("\n");
    });
    return sb.toString();
  }

  /**
   * Translates the outcome part of a matrix line ({@code all=404} or
   * {@code SEALED_KEYS=403 | UNSEALED_KEYS=404 | ...}) into a table cell.
   */
  static String accessCell(String outcome)
  {
    Map<String, String> byState = new TreeMap<>();
    if(outcome.startsWith("all="))
    {
      byState.put("all", outcome.substring(4));
    }
    else
    {
      for(String part : outcome.split("\\s*\\|\\s*"))
      {
        int eq = part.indexOf('=');
        byState.put(part.substring(0, eq), part.substring(eq + 1));
      }
    }
    if(byState.size() == 1)
    {
      String result = byState.values().iterator().next();
      return result.startsWith("404") ? "✓"
        : result.contains("/oauth2/authorization/") ? "Login"
        : result.startsWith("302") ? "↪" : "–";
    }
    Set<String> allowed = new TreeSet<>();
    byState.forEach((state, result) ->
    {
      if(result.startsWith("404"))
      {
        allowed.add(state);
      }
    });
    if(allowed.isEmpty())
    {
      return "–";
    }
    if(allowed.equals(Set.of("UNSEALED_KEYS", "UNSEALED_NOKEYS")))
    {
      return "✓ Vault offen";
    }
    if(allowed.equals(Set.of("UNSEALED_KEYS")))
    {
      return "✓ Vault offen, Keys vorhanden";
    }
    if(allowed.equals(Set.of("UNSEALED_KEYS", "SEALED_NOKEYS", "UNSEALED_NOKEYS")))
    {
      return "✓ Vault offen oder keine Keys";
    }
    return "✓ " + String.join(", ", allowed);
  }

  // ------------------------------------------------------------------ SBOM / dependency-check
  /**
   * Components whose versions are listed in the security audit, as
   * {@code group:name} → label.
   */
  private static final Map<String, String> KEY_COMPONENTS = new LinkedHashMap<>();

  static
  {
    KEY_COMPONENTS.put("org.springframework.boot:spring-boot", "Spring Boot");
    KEY_COMPONENTS.put("org.springframework:spring-core", "Spring Framework");
    KEY_COMPONENTS.put("org.springframework.security:spring-security-core", "Spring Security");
    KEY_COMPONENTS.put("org.hibernate.orm:hibernate-core", "Hibernate ORM");
    KEY_COMPONENTS.put("org.apache.tomcat.embed:tomcat-embed-core", "Tomcat");
    KEY_COMPONENTS.put("tools.jackson.core:jackson-databind", "Jackson 3");
    KEY_COMPONENTS.put("com.nimbusds:nimbus-jose-jwt", "Nimbus JOSE + JWT");
    KEY_COMPONENTS.put("de.l9g:crypto-core", "L9G Crypto");
    KEY_COMPONENTS.put("org.postgresql:postgresql", "PostgreSQL JDBC");
    KEY_COMPONENTS.put("com.unboundid:unboundid-ldapsdk", "UnboundID LDAP SDK");
    KEY_COMPONENTS.put("org.springdoc:springdoc-openapi-starter-webmvc-ui", "springdoc-openapi");
    KEY_COMPONENTS.put("org.webjars:swagger-ui", "Swagger UI");
  }

  /**
   * Summarises the CycloneDX SBOM: number of components and the versions of
   * the security-relevant frameworks.
   *
   * @param sbom SBOM JSON
   *
   * @return Markdown, or a hint if the SBOM is missing
   */
  static String sbomSummary(Path sbom)
  {
    if( ! Files.exists(sbom))
    {
      return "_Keine SBOM vorhanden (Profil `release` ausführen)._\n";
    }
    try
    {
      JsonNode root = JSON.readTree(Files.readString(sbom, StandardCharsets.UTF_8));
      Map<String, String> versions = new LinkedHashMap<>();
      int count = 0;
      for(JsonNode component : root.path("components"))
      {
        count ++;
        String key = component.path("group").asString("") + ":"
          + component.path("name").asString("");
        if(KEY_COMPONENTS.containsKey(key))
        {
          versions.put(key, component.path("version").asString(""));
        }
      }
      StringBuilder sb = new StringBuilder("SBOM (CycloneDX ")
        .append(root.path("specVersion").asString("?")).append(") mit **")
        .append(count).append(" Komponenten**: `sbom.json`\n\n")
        .append("| Komponente | Version |\n|---|---|\n");
      KEY_COMPONENTS.forEach((key, label) -> sb.append("| ").append(label)
        .append(" | ").append(versions.getOrDefault(key, "–")).append(" |\n"));
      return sb.toString();
    }
    catch(IOException | RuntimeException ex)
    {
      return "_SBOM nicht lesbar: " + ex.getMessage() + "_\n";
    }
  }

  /**
   * Reads every vulnerability of the OWASP dependency-check JSON report.
   *
   * @param report path of {@code dependency-check-report.json}
   *
   * @return findings, empty if the report does not exist
   */
  static List<Finding> readDependencyCheck(Path report)
  {
    List<Finding> findings = new ArrayList<>();
    if( ! Files.exists(report))
    {
      return findings;
    }
    try
    {
      JsonNode root = JSON.readTree(Files.readString(report, StandardCharsets.UTF_8));
      for(JsonNode dependency : root.path("dependencies"))
      {
        String packageId = dependency.path("packages").path(0).path("id").asString("");
        for(JsonNode vulnerability : dependency.path("vulnerabilities"))
        {
          JsonNode score = vulnerability.path("cvssv3").path("baseScore");
          if(score.isMissingNode())
          {
            score = vulnerability.path("cvssv4").path("baseScore");
          }
          findings.add(new Finding(dependency.path("fileName").asString(""),
            packageId, vulnerability.path("name").asString(""),
            vulnerability.path("severity").asString(""),
            score.isMissingNode() ? "" : score.asString(),
            vulnerability.has("knownExploitedVulnerability")));
        }
      }
    }
    catch(IOException | RuntimeException ex)
    {
      findings.add(new Finding("dependency-check-report.json", "", "nicht lesbar",
        ex.getMessage(), "", false));
    }
    return findings;
  }

  static String findingsTable(List<Finding> findings, boolean reportExists)
  {
    if( ! reportExists)
    {
      return "_Kein dependency-check-Report vorhanden (Profil `release` ausführen)._\n";
    }
    if(findings.isEmpty())
    {
      return "Keine bekannten Schwachstellen gefunden.\n";
    }
    Map<String, Integer> bySeverity = new TreeMap<>();
    findings.forEach(f -> bySeverity.merge(f.severity().toLowerCase(), 1, Integer::sum));
    StringBuilder sb = new StringBuilder("**").append(findings.size())
      .append(" Befund(e)**: ").append(bySeverity.entrySet().stream()
      .map(e -> e.getValue() + " × " + e.getKey()).toList()).append("\n\n")
      .append("| Datei | Paket | Schwachstelle | Schweregrad | CVSS | CISA KEV |\n")
      .append("|---|---|---|---|---|---|\n");
    for(Finding f : findings)
    {
      sb.append("| ").append(f.file()).append(" | `").append(f.packageId())
        .append("` | ").append(f.cve()).append(" | ").append(f.severity())
        .append(" | ").append(f.score().isEmpty() ? "–" : f.score()).append(" | ")
        .append(f.knownExploited() ? "**ja**" : "nein").append(" |\n");
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------ helpers
  private static String buildInfo(String key, String fallback)
  {
    if( ! Files.exists(BUILD_INFO))
    {
      return fallback;
    }
    Properties properties = new Properties();
    try(InputStream in = Files.newInputStream(BUILD_INFO))
    {
      properties.load(in);
      return properties.getProperty(key, fallback);
    }
    catch(IOException ex)
    {
      return fallback;
    }
  }

  /**
   * Minimal template engine: replaces every {@code {{key}}} with its value.
   * Lists and tables are pre-rendered to Markdown, so no loops or conditionals
   * are needed.
   *
   * @param template template text
   * @param vars placeholder values
   *
   * @return the rendered text
   */
  static String render(String template, Map<String, String> vars)
  {
    String out = template;
    for(Map.Entry<String, String> e : vars.entrySet())
    {
      out = out.replace("{{" + e.getKey() + "}}", e.getValue());
    }
    return out;
  }

  private static String readResource(String path)
    throws IOException
  {
    try(InputStream in = new ClassPathResource(path).getInputStream())
    {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

}
