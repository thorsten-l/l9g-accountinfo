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
package l9g.account.info.controller.api;

/**
 * Represents a standardized response payload for API calls, especially for signature capture.
 * This record provides a status and optionally a base64 encoded PNG image of a signature.
 *
 * @param status The status of the operation (e.g., "ok", "error", "timeout", "cancel").
 * @param sigpng A base64 encoded string of the signature PNG image.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public record ResponsePayload(String status, String sigpng)
  {
}
