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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity attribute that contains personal data within the meaning
 * of Art. 4 No. 1 GDPR.
 * <p>
 * The annotation has no effect on persistence or on the database schema. It is
 * read reflectively by {@link ComplianceReportGenerator} at release time to
 * build the personal-data inventory of the process description and the DPIA,
 * together with the information whether the column is encrypted (presence of
 * the L9G {@code EncryptedAttributeConverter}). Annotating a new personal
 * attribute is therefore enough to make it appear in the compliance documents.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PersonalData
{
  /**
   * Data category used in the documents, e.g. {@code "Identifikation"},
   * {@code "Kontakt"}, {@code "Beschäftigtendaten"}.
   *
   * @return the category
   */
  String category();

  /**
   * Short description of the processing purpose, printed verbatim.
   *
   * @return the purpose, may be empty
   */
  String purpose() default "";

  /**
   * Whether the attribute may hold special categories of personal data
   * (Art. 9 GDPR), e.g. biometric data such as signatures or ID photos.
   *
   * @return {@code true} for Art. 9 data
   */
  boolean specialCategory() default false;

}
