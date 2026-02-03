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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Custom Jackson serializer for {@link PublicKey} objects.
 * This class handles the conversion of a Java {@link PublicKey} object into a Base64 encoded string.
 * It serializes the public key in its X.509 encoded form.
 */
public class PublicKeySerializer extends StdSerializer<PublicKey>
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 5878221140870733911L;

  /**
   * Constructs a new {@code PublicKeySerializer}.
   */
  public PublicKeySerializer()
  {
    super(PublicKey.class);
  }

  /**
   * Serializes a {@link PublicKey} object into a Base64 encoded string.
   * The public key is first encoded into its X.509 format.
   *
   * @param key The {@link PublicKey} to serialize.
   * @param gen The JSON generator to write the serialized value to.
   * @param provider The serializer provider.
   *
   * @throws IOException If an I/O error occurs during serialization.
   */
  @Override
  public void serialize(
    PublicKey key,
    JsonGenerator gen,
    SerializerProvider provider
  )
    throws IOException
  {
    // Kodieren als Base64 der X.509-Encoded Form
    byte[] encoded = key.getEncoded();
    String b64 = Base64.getEncoder().encodeToString(encoded);
    gen.writeString(b64);
  }

}
