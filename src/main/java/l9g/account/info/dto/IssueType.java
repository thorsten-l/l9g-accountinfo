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

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@ToString
@Getter
public enum IssueType
{
  UNKNOWN("UNKNOWN"),
  ACCOUNT("ACCOUNT"),
  ACCOUNT_CARD("ACCOUNT_CARD"),
  CARD("CARD");
  
  private final String issueType;

  private IssueType(String issueType)
  {
    this.issueType = issueType.toUpperCase();
  }

  public static IssueType fromString(String text)
  {
    if (text != null)
    {
      for (IssueType it : IssueType.values())
      {
        if (text.equalsIgnoreCase(it.issueType))
        {
          return it;
        }
      }
    }
    return UNKNOWN;
  }
}
