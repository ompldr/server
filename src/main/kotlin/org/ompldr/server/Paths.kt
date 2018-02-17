/**
 * Copyright 2018 Brenden Matthews <brenden@diddyinc.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ompldr.server

import io.ktor.locations.Location

object Paths {
  /**
   * Get encrypted file data
   * Fetch and return encrypted file contents. Data is encrypted with AES-128.

   * @param id ID of file to fetch
   */
  @Location("/get/{id}")
  class getEncryptedFile(val id: kotlin.String)

  /**
   * Get file
   * Fetch, decrypt, and return file contents
   * @param id ID of file to fetch
   * @param privateKey Private key to decrypt file on server side.
   */
  @Location("/get/{id}/{privateKey}")
  class getFile(val id: kotlin.String, val privateKey: kotlin.String)

  /**
   * Get file info
   * Fetch the current file info.
   * @param id ID of file to update
   */
  @Location("/info/{id}")
  class getInfo(val id: kotlin.String)

  /**
   * Health check
   * API health check endpoint, which returns 200 when everything&#39;s a o.k.
   */
  @Location("/ping")
  class pingGet()

  @Location("/")
  class indexGet()

  @Location("/swagger.json")
  class swaggerJsonGet()

  @Location("/swagger.yaml")
  class swaggerYamlGet()
}
