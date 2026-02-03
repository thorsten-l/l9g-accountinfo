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
package l9g.account.info.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark a field as an encrypted value.
 * This annotation is used in conjunction with {@link EncryptedValueProcessor}
 * to automatically encrypt and decrypt string values during data access.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Target(
  {
    ElementType.FIELD
  })
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptedValue
{
  /**
   * Represents the encrypted value as a String.
   * This method defines the default attribute for the annotation.
   *
   * @return The encrypted string value.
   */
  String value();

}
