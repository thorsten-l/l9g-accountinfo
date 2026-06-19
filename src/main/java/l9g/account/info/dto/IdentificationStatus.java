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
package l9g.account.info.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
/**
 * Record representing the WebID Ident Status Response.
 * This DTO is used when the AutoID process is completed (success or failure).
 *
 * Corresponds to the JSON structure with responseType: "unified_ident".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentificationStatus(
  String transactionId,
  String responseType,
  String actionId,
  String actionType,
  OffsetDateTime identifiedOn,
  OffsetDateTime finishedOn,
  Boolean success,
  User user,
  IdDocument idDocument,
  @JsonProperty("product")
  ProductInfo product,
  Boolean mismatch,
  Boolean isTrueid,
  String rejectionReason,
  Map<String, Object> customParameters,
  List<Object> passImages,
  List<Object> portraitImages,
  List<Object> customLegalDecisionsForIdent,
  QesContinue qesContinue,
  // WRITE_ONLY: the incoming WebID callback still populates this (the
  // download token is needed to fetch the result archive), but it is never
  // serialised back out — keeping the one-time token out of logs, the
  // persisted audit file and the state-machine context.
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  FileResponseDownload fileResponseDownload
  ) implements Serializable
  {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record ProductInfo(
    String name
    ) implements Serializable
    {
  }

  /**
   * Hand-off link to resume a pending QES (qualified electronic signature)
   * step, valid until {@link #validUntil()}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record QesContinue(
    String url,
    OffsetDateTime validUntil
    ) implements Serializable
    {
  }

  /**
   * Token to download the identification result file, valid until
   * {@link #validUntil()}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record FileResponseDownload(
    String downloadToken,
    OffsetDateTime validUntil
    ) implements Serializable
    {
  }

  /**
   * Personal details of the identified user as returned by WebID.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record User(
    String title,
    String sex,
    String firstname,
    String lastname,
    LocalDate dateOfBirth,
    Address address,
    Contact contact,
    Map<String, Object> customFields
    ) implements Serializable
    {
  }

  /**
   * Physical address of the identified user.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record Address(
    String street,
    String streetNo,
    String addressLine1,
    String addressLine2,
    String region,
    String zip,
    String city,
    String country
    ) implements Serializable
    {
  }

  /**
   * Contact information of the identified user.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record Contact(
    String email,
    String cell,
    String phone
    ) implements Serializable
    {
  }

  /**
   * Identity document details extracted during the identification.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record IdDocument(
    String documentType,
    String authority,
    LocalDate dateOfIssue,
    LocalDate dateOfExpiry,
    String nationality,
    String issuingCountry,
    String idNumber,
    String mrz,
    String mrzLine1,
    String mrzLine2,
    String mrzLine3,
    String nameAtBirth,
    String placeOfBirth,
    String optionalDataElements1,
    String optionalDataElements2,
    List<String> drivingLicenseCategories
    ) implements Serializable
    {
  }

  /**
   * Helper method to quickly determine if the verification was successful.
   *
   * @return true if the identity check passed, false otherwise.
   */
  public boolean isVerified()
  {
    return Boolean.TRUE.equals(success);
  }

}
