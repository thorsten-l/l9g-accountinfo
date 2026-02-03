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
package l9g.account.info.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object representing user information for signature pad operations.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@Schema(name = "UserInfo", description = "Comprehensive user information for display on signature pads")
public record DtoUserInfo(
  @Schema(description = "Status of the user information retrieval")
  String status,
  @Schema(description = "Base64-encoded JPEG photo of the user (with data URI prefix)")
  String jpegPhoto,
  @Schema(description = "User's first name")
  String firstname,
  @Schema(description = "User's last name")
  String lastname,
  @Schema(description = "Unique user identifier")
  String uid,
  @Schema(description = "User's email address")
  String mail,
  @Schema(description = "User's birth date as string (format might vary, e.g., YYYY-MM-DD)")
  String birthday,
  @Schema(description = "Semester or temporary address information")
  DtoAddress semester,
  @Schema(description = "Home address information")
  DtoAddress home
  )
  {

  /**
   * Constructs a DtoUserInfo with only a status, typically used for error responses.
   * All other fields are initialized to null.
   *
   * @param status The status message.
   */
  public DtoUserInfo(String status)
  {
    this(status, null, null, null, null, null, null, null, null);
  }

}
