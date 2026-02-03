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
package l9g.account.info.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAPI (Swagger) documentation.
 * <p>
 * This class uses annotations to define the global properties of the OpenAPI
 * documentation, including API metadata, server information, and security
 * schemes.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Configuration
@OpenAPIDefinition(
  info =
  @Info(
    title = "L9G Accountinfo API",
    version = "v1",
    description = "API for publishing account information and campus card details using a tablet running Google Chrome.",
    contact =
    @Contact(
      name = "Thorsten Ludewig",
      email = "t.ludewig@gmail.com"
    ),
    license =
    @License(
      name = "Apache 2.0",
      url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    )
  )
)
public class OpenApiConfig
{
}
