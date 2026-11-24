/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
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
package l9g.account.info.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Custom Jackson deserializer for {@link PublicKey} objects.
 * This class handles the conversion of a Base64 encoded public key string into a Java {@link PublicKey} object.
 * It expects the public key to be in X.509 format and uses RSA algorithm.
 */
public class PublicKeyDeserializer extends StdDeserializer<PublicKey>
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 8318750956675564611L;

  /**
   * Constructs a new {@code PublicKeyDeserializer}.
   */
  public PublicKeyDeserializer()
  {
    super(PublicKey.class);
  }

  /**
   * Deserializes a Base64 encoded string into a {@link PublicKey} object.
   * The input string is expected to be an X.509 encoded public key.
   *
   * @param p The JSON parser.
   * @param ctxt The deserialization context.
   *
   * @return A {@link PublicKey} object.
   *
   * @throws JacksonException If the public key cannot be deserialized.
   */
  @Override
  public PublicKey deserialize(
    JsonParser p,
    DeserializationContext ctxt
  )
    throws JacksonException
  {
    String b64 = p.getValueAsString();
    byte[] data = Base64.getDecoder().decode(b64);
    try
    {
      X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      return kf.generatePublic(spec);
    }
    catch(Exception e)
    {
      throw DatabindException.from(p, "Failed to deserialize PublicKey", e);
    }
  }

}
