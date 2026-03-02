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
package l9g.account.info.vault;

import lombok.NoArgsConstructor;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@NoArgsConstructor
public class VaultSealedException extends RuntimeException
{
  private static final long serialVersionUID = -6712671588009299213L;
  
  public VaultSealedException(String message)
  {
    super(message);
  }
  
}
