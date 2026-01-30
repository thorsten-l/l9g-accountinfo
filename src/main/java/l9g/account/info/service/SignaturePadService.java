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
package l9g.account.info.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import l9g.account.info.db.DbService;
import lombok.RequiredArgsConstructor;

/**
 * Service class for managing signature pad operations and data persistence.
 * Handles creation, storage, and retrieval of signature pad configurations
 * using JSON file-based storage system.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignaturePadService
{  
  private final DbService dbService;

  /**
   * Creates a new signature pad with the specified name.
   * Generates a unique UUID for the signature pad and stores it persistently.
   * 
   * @param name the display name for the new signature pad
   * @return the newly created signature pad instance
   * @throws IOException if storage operation fails
   */
  public SignaturePad createSignaturePad(String publisher, String name)
    throws IOException
  {
    SignaturePad signaturePad = new SignaturePad(name);
    saveSignaturePad(publisher, signaturePad);
    return signaturePad;
  }

  /**
   * Retrieves a signature pad by its unique identifier.
   * 
   * @param uuid the unique identifier of the signature pad
   * @return the signature pad instance or null if not found
   * @throws IOException if file access fails
   */
  public SignaturePad findSignaturePadByUUID(String uuid)
    throws IOException
  {
    return dbService.findSignaturePadbyUUID(uuid);
  }

  /**
   * Stores the given signature pad object as a JSON file.
   * The file is saved as {storeDir}/{uuid}.json with pretty-printed formatting.
   * 
   * @param pad the signature pad to store
   * @throws IOException if file writing fails
   */
  public void saveSignaturePad(String publisher, SignaturePad pad)
    throws IOException
  {
    dbService.saveSignaturePad(publisher, pad);
  }
}
