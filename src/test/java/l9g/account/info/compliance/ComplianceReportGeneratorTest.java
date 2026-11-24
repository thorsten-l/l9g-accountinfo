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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import l9g.account.info.compliance.ComplianceReportGenerator.EntityInfo;
import l9g.account.info.compliance.ComplianceReportGenerator.FieldInfo;
import l9g.account.info.compliance.ComplianceReportGenerator.Finding;
import l9g.account.info.compliance.ComplianceReportGenerator.Risk;
import l9g.account.info.db.model.SdbSecretData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceReportGeneratorTest
{
  private final ComplianceReportGenerator generator = new ComplianceReportGenerator();

  @Test
  @DisplayName("every scanned table and column exists in the recorded database schema")
  void scannedColumnsMatchTheRecordedSchema()
    throws IOException
  {
    String schema;
    try(InputStream in = getClass().getResourceAsStream("/golden/schema-postgresql.sql"))
    {
      schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    List<EntityInfo> entities = generator.scanEntities();

    assertThat(entities).extracting(EntityInfo::table)
      .containsExactlyInAnyOrder("lastseen", "properties", "secretdata", "vaultadminkeys");
    for(EntityInfo entity : entities)
    {
      String createTable = schema.lines()
        .filter(line -> line.startsWith("create table " + entity.table() + " ("))
        .findFirst().orElseThrow();
      for(FieldInfo field : entity.fields())
      {
        assertThat(createTable)
          .as("%s.%s", entity.table(), field.column())
          .containsPattern("(\\(|, )" + field.column() + " ");
      }
    }
  }

  @Test
  @DisplayName("inherited columns are included and the secret column is encrypted Art. 9 data")
  void describesInheritedAndEncryptedColumns()
  {
    EntityInfo secretData = ComplianceReportGenerator.describe(SdbSecretData.class);

    assertThat(secretData.table()).isEqualTo("secretdata");
    assertThat(secretData.fields()).extracting(FieldInfo::column)
      .contains("id", "created_by", "modify_timestamp", "s_key", "secret")
      .doesNotContain("value");
    FieldInfo secret = secretData.fields().stream()
      .filter(f -> f.name().equals("secret")).findFirst().orElseThrow();
    assertThat(secret.encrypted()).isTrue();
    assertThat(secret.specialCategory()).isTrue();
  }

  @Test
  @DisplayName("column names follow Spring Boot's snake case naming")
  void columnNameIsSnakeCase()
  {
    assertThat(ComplianceReportGenerator.columnName("createTimestamp"))
      .isEqualTo("create_timestamp");
    assertThat(ComplianceReportGenerator.columnName("credentialId"))
      .isEqualTo("credential_id");
    assertThat(ComplianceReportGenerator.columnName("secret")).isEqualTo("secret");
  }

  @Test
  @DisplayName("every SdbSecretType is described in processing-activities.yaml")
  void everySecretTypeIsDescribed()
  {
    String table = ComplianceReportGenerator.dataTypeTable(
      ComplianceReportGenerator.loadYaml(ComplianceReportGenerator.PROCESSING_ACTIVITIES));

    assertThat(table).doesNotContain("nicht beschrieben");
  }

  @Test
  @DisplayName("risk value and level are computed from likelihood and severity")
  void riskRegisterIsEvaluated()
  {
    List<Risk> risks = ComplianceReportGenerator.readRisks(
      ComplianceReportGenerator.loadYaml(ComplianceReportGenerator.RISK_REGISTER));

    assertThat(risks).hasSize(14);
    assertThat(risks.get(0).id()).isEqualTo("R-01");
    assertThat(risks.get(0).value()).isEqualTo(6);
    assertThat(risks.get(0).level()).isEqualTo("hoch");
    assertThat(new Risk("X", "", "", 2, 2, "", "").level()).isEqualTo("mittel");
    assertThat(new Risk("X", "", "", 1, 2, "", "").level()).isEqualTo("gering");
    assertThat(ComplianceReportGenerator.riskSummary(risks))
      .contains("| hoch | 2 | R-01, R-09 |");
  }

  @Test
  @DisplayName("matrix outcomes are translated into access cells")
  void accessCells()
  {
    assertThat(ComplianceReportGenerator.accessCell("all=404")).isEqualTo("✓");
    assertThat(ComplianceReportGenerator.accessCell("all=403")).isEqualTo("–");
    assertThat(ComplianceReportGenerator.accessCell(
      "all=302 http://localhost/oauth2/authorization/app")).isEqualTo("Login");
    assertThat(ComplianceReportGenerator.accessCell(
      "SEALED_KEYS=403 | UNSEALED_KEYS=404 | SEALED_NOKEYS=403 | UNSEALED_NOKEYS=404"))
      .isEqualTo("✓ Vault offen");
    assertThat(ComplianceReportGenerator.accessCell(
      "SEALED_KEYS=403 | UNSEALED_KEYS=404 | SEALED_NOKEYS=403 | UNSEALED_NOKEYS=403"))
      .isEqualTo("✓ Vault offen, Keys vorhanden");
  }

  @Test
  @DisplayName("the access matrix shows the audit rule of the security configuration")
  void accessMatrixFromRecordedMatrix()
  {
    String matrix = ComplianceReportGenerator.accessMatrix(
      ComplianceReportGenerator.SECURITY_MATRIX);

    assertThat(matrix)
      // anonym | ohne Rolle | PUBLISHER | TABADMIN | VAULTADMIN | AUDITADMIN | ADMIN
      .contains("| `/api/v1/admin/secret/audit/x` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |")
      .contains("| `/app` | Login | – | ✓ | – | – | – | – |");
  }

  @Test
  @DisplayName("dependency-check findings are read per vulnerability")
  void readsDependencyCheckReport(@TempDir Path dir)
    throws IOException
  {
    Path report = dir.resolve("dependency-check-report.json");
    Files.writeString(report, """
      {"dependencies":[
        {"fileName":"clean.jar"},
        {"fileName":"swagger-ui.jar: bundle.js",
         "packages":[{"id":"pkg:javascript/DOMPurify@3.4.16"}],
         "vulnerabilities":[
           {"name":"CVE-1","severity":"critical","cvssv3":{"baseScore":9.8},
            "knownExploitedVulnerability":{}},
           {"name":"CVE-2","severity":"low"}]}]}
      """);

    List<Finding> findings = ComplianceReportGenerator.readDependencyCheck(report);

    assertThat(findings).containsExactly(
      new Finding("swagger-ui.jar: bundle.js", "pkg:javascript/DOMPurify@3.4.16",
        "CVE-1", "critical", "9.8", true),
      new Finding("swagger-ui.jar: bundle.js", "pkg:javascript/DOMPurify@3.4.16",
        "CVE-2", "low", "", false));
    assertThat(ComplianceReportGenerator.findingsTable(findings, true))
      .contains("**2 Befund(e)**");
    assertThat(ComplianceReportGenerator.findingsTable(List.of(), false))
      .contains("Kein dependency-check-Report");
  }

  @Test
  @DisplayName("generates all documents without unresolved placeholders and copies them")
  void generatesAndCopiesDocuments(@TempDir Path dir)
    throws IOException
  {
    Path out = dir.resolve("compliance");
    Map<String, String> documents = generator.generate("9.9.9", out);

    assertThat(documents.keySet()).containsExactly(
      "security-audit-9.9.9.md", "prozessbeschreibung-9.9.9.md", "dsfa-9.9.9.md");
    documents.values().forEach(text -> assertThat(text).doesNotContain("{{"));
    assertThat(documents.get("security-audit-9.9.9.md"))
      .contains("# Security-Audit – l9g-accountinfo 9.9.9")
      .contains("**SdbSecretData** (`secretdata`): `secret`");

    Path release = dir.resolve("release");
    List<String> copied = ComplianceReportGenerator.copyToRelease(
      out, release, documents.keySet());
    assertThat(copied).containsExactlyElementsOf(documents.keySet());
    assertThat(release.resolve("dsfa-9.9.9.md")).exists();
  }

}
