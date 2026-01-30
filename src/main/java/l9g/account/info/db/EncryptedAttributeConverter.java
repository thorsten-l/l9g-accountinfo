/*
 * Copyright 2025 Thorsten Ludewig <t.ludewig@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package l9g.account.info.db;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import l9g.account.info.crypto.CryptoHandler;

/**
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Converter
public class EncryptedAttributeConverter implements
  AttributeConverter<String, String>
{

  @Override
  public String convertToDatabaseColumn(String attribute)
  {
    return attribute == null ? null : CryptoHandler.getInstance().encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData)
  {
    return dbData == null ? null : CryptoHandler.getInstance().decrypt(dbData);
  }

}
