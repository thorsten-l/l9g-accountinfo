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
 * Data Transfer Object (DTO) representing an address.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@Schema(name = "Address", description = "Represents a postal address")
public record DtoAddress(@Schema(description = "\"Care Of\" or alternative recipient information")
  String co, @Schema(description = "Street name and house number")
  String street, @Schema(description = "Postal code")
  String zip, @Schema(description = "City or town")
  String city, @Schema(description = "State, province, or region")
  String state, @Schema(description = "Country")
  String country)
  {

}
