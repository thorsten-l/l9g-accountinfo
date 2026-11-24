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
package l9g.account.info;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import l9g.account.info.dto.DtoEvent;
import l9g.account.info.dto.IdentificationStatus;
import l9g.account.info.dto.StorageObject;
import l9g.account.info.service.SignaturePad;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test for the JSON the application reads from and writes to the
 * database, the signature pads and external systems.
 * <p>
 * Several columns ({@code secretdata.secret}, {@code description},
 * {@code createdBy}, {@code modifiedBy}) hold JSON written by the Spring-managed
 * Jackson mapper. Rows written by earlier releases must stay readable, and new
 * rows must keep the same format. The WebSocket events to the pads are written
 * with a plain mapper and are part of the pad protocol.
 * <p>
 * The expected output in {@code src/test/resources/golden/json-format.txt} was
 * recorded with Spring Boot 3.5 / Jackson 2 before the upgrade to
 * Spring Boot 4 / Jackson 3; the migration must not change a single byte of it. The actual output is always written to
 * {@code target/golden/json-format.txt}.
 * <p>
 * The Spring-managed mapper is taken from {@link JacksonAutoConfiguration}
 * alone, without the application context, configured with the shipped
 * {@code application.yaml} only ({@code spring.jackson.use-jackson2-defaults});
 * neither {@code data/config.yaml} nor the key file is read. The plain mapper
 * is built like the one in {@code SignaturePadWebSocketHandler} and
 * {@code JwtService}.
 */
class JsonFormatGoldenTest
{
  private static final String GOLDEN = "/golden/json-format.txt";

  private static final Path ACTUAL = Path.of("target/golden/json-format.txt");

  /**
   * A signature pad row as written to {@code secretdata.secret} by release 2.5.
   */
  static final String STORED_SIGNATURE_PAD = """
    {"uuid":"5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f","name":"Pad Foyer",\
    "validated":true,"clientEnvironment":{"userAgent":"Mozilla/5.0",\
    "screen":{"width":1280,"height":800},"touch":true},"version":3,"ttl":0,\
    "publicJwk":{"kty":"RSA","e":"AQAB","use":"sig",\
    "kid":"5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f-3","alg":"RS256","n":"sXchDaQebHnPiGvyDOAT4saGEUetSyo9MKLOoWFsueri23bOdgWp4Dy1WlUzewbgBHod5pcM9H95GQRV3JDXboIRROSBigeC5yjU1hGzHHyXss8UDprecbAYxknTcQkhslANGRUZmdTOQ5qTRsLAt6BTYuyvVRdhS8exSZEy_c4gs_7svlJJQ4H9_NxsiIoLwAEk7-Q3UXERGYw_75IDrGA84-lA_-Ct4eTlXHBIY2EaV7t7LjJaynVJCpkv4LKjTTAumiGUIuQhrNhZLuF_RJLqHpM2kgWFLU7-VTdL1VbC2tejvcI2BlMkEpk1BzBZI0KQB0GaDWFLN-aEAw3vRw"}}
    """.strip();

  /**
   * An identification status as delivered by the external identification
   * provider, with fields the application does not know and a download token
   * that must never be written back.
   */
  static final String PROVIDER_STATUS = """
    {"transactionId":"tx-4711","responseType":"identification",\
    "actionId":"a-1","actionType":"ident","identifiedOn":"2026-03-14T10:15:30+01:00",\
    "finishedOn":"2026-03-14T10:20:00.123+01:00","success":true,\
    "user":{"title":"Dr.","sex":"F","firstname":"Marie","lastname":"Muster",\
    "dateOfBirth":"1990-02-03","address":{"street":"Salzdahlumer Str.",\
    "streetNo":"46/48","zip":"38302","city":"Wolfenbüttel","country":"DE"},\
    "contact":{"email":"m.muster@example.org","mobile":"+49 151 0000000"},\
    "customFields":{"matrikel":"123456"}},\
    "idDocument":{"type":"IDCARD","number":"L01X00T47","issuingCountry":"DE",\
    "validUntil":"2031-01-01"},\
    "product":{"name":"autoident"},"mismatch":false,"isTrueid":true,\
    "rejectionReason":null,"customParameters":{"campus":"WF"},\
    "passImages":[],"portraitImages":["p1"],"customLegalDecisionsForIdent":[],\
    "qesContinue":{"url":"https://idp.example.org/qes","validUntil":"2026-03-15T00:00:00Z"},\
    "fileResponseDownload":{"downloadToken":"SECRET-TOKEN","validUntil":"2026-03-15T00:00:00Z"},\
    "unknownField":{"nested":1}}
    """.strip();

  @Test
  void jsonFormatIsUnchanged()
    throws Exception
  {
    List<String> actual = new ArrayList<>();

    List<PropertySource<?>> applicationYaml = new YamlPropertySourceLoader()
      .load("application.yaml", new ClassPathResource("application.yaml"));

    new ApplicationContextRunner()
      .withInitializer(context -> applicationYaml.forEach(
        source -> context.getEnvironment().getPropertySources().addLast(source)))
      .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
      .run(context ->
      {
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        springMapperCases(mapper, actual);
      });

    plainMapperCases(JsonMapper.builderWithJackson2Defaults().build(), actual);

    Files.createDirectories(ACTUAL.getParent());
    Files.write(ACTUAL, actual, StandardCharsets.UTF_8);

    assertThat(actual).containsExactlyElementsOf(readGolden());
  }

  private static void springMapperCases(ObjectMapper mapper, List<String> out)
    throws Exception
  {
    // signature pad row: read as stored by release 2.5, written back unchanged
    SignaturePad pad = mapper.readValue(STORED_SIGNATURE_PAD, SignaturePad.class);
    out.add("signature-pad.uuid: " + pad.getUuid());
    out.add("signature-pad.keyId: " + pad.getKeyId());
    out.add("signature-pad.write: " + mapper.writeValueAsString(pad));

    // identification status: provider JSON in, secretdata.secret out
    IdentificationStatus status =
      mapper.readValue(PROVIDER_STATUS, IdentificationStatus.class);
    out.add("identification-status.write: " + mapper.writeValueAsString(status));
    out.add("identification-status.fileResponseDownload: "
      + status.fileResponseDownload());

    // storage object as posted to /api/v1/storage/objects (trailing newline)
    String body = "{\"type\":\"EXT_IDENTIFICATION_STATUS\",\"user\":{\"pid\":\"p-1\","
      + "\"mail\":\"m.muster@example.org\",\"name\":\"ignored\",\"username\":\"mmuster\","
      + "\"givenName\":\"Marie\",\"surname\":\"Muster\",\"extra\":1},"
      + "\"status\":" + PROVIDER_STATUS + ",\"data\":\"AAECAwQ=\",\"other\":true}\n";
    StorageObject object = mapper.readValue(body, StorageObject.class);
    out.add("storage-object.read: " + object);
    out.add("storage-object.description: "
      + mapper.writeValueAsString(object.user().description()));
    out.add("storage-object.status: " + mapper.writeValueAsString(object.status()));

    // description / createdBy / modifiedBy columns
    Map<String, String> publisher = new HashMap<>();
    publisher.put("name", "Marie Muster");
    publisher.put("username", "mmuster");
    publisher.put("mail", "m.muster@example.org");
    out.add("publisher.write: " + mapper.writeValueAsString(publisher));

    Map<String, String> description = new HashMap<>();
    description.put("name", "Marie Muster");
    description.put("mail", "m.muster@example.org");
    String descriptionJson = mapper.writeValueAsString(description);
    out.add("description.write: " + descriptionJson);
    out.add("description.read: "
      + new TreeMap<>(mapper.readValue(descriptionJson, Map.class)));

    // archive export (ApiAdminController): pretty printed tree
    JsonNode node = mapper.readTree(descriptionJson);
    Map<String, Object> export = new LinkedHashMap<>();
    export.put("id", "row-1");
    export.put("type", "ID_SIGNATURE_JWT");
    export.put("size", 42L);
    export.put("description", node);
    export.put("secret", mapper.getNodeFactory().stringNode("not json"));
    out.add("export.pretty:");
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(export)
      .lines().forEach(line -> out.add("| " + line));
  }

  private static void plainMapperCases(ObjectMapper mapper, List<String> out)
    throws Exception
  {
    // WebSocket events to the signature pads (SignaturePadWebSocketHandler)
    out.add("event.show: " + mapper.writeValueAsString(
      event(new DtoEvent(DtoEvent.EVENT_SHOW, "{\"name\":\"Marie\"}"))));
    out.add("event.heartbeat: " + mapper.writeValueAsString(
      event(new DtoEvent(DtoEvent.EVENT_HEARTBEAT))));

    // JWT payload parsing (JwtService)
    String payload = "{\"sub\":\"u-1\",\"exp\":1767225600,\"roles\":[\"a\",\"b\"],"
      + "\"resource_access\":{\"accountinfo\":{\"roles\":[\"ADMIN\"]}}}";
    out.add("jwt-payload.read: "
      + new TreeMap<>(mapper.readValue(payload, HashMap.class)));
  }

  /**
   * Pins the otherwise time-dependent event timestamp.
   */
  private static DtoEvent event(DtoEvent event)
    throws ReflectiveOperationException
  {
    Field timestamp = DtoEvent.class.getDeclaredField("timestamp");
    timestamp.setAccessible(true);
    timestamp.setLong(event, 1767225600000L);
    return event;
  }

  private static List<String> readGolden()
    throws IOException
  {
    try(InputStream in = JsonFormatGoldenTest.class.getResourceAsStream(GOLDEN))
    {
      assertThat(in)
        .as("golden file %s missing; review %s and copy it there", GOLDEN, ACTUAL)
        .isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }
  }

}
