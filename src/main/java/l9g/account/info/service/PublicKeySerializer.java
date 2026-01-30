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

public class PublicKeySerializer extends StdSerializer<PublicKey>
{
  private static final long serialVersionUID = 5878221140870733911L;

  public PublicKeySerializer()
  {
    super(PublicKey.class);
  }

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
