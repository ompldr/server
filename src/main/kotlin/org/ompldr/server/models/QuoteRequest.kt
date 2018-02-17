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
package org.ompldr.server.models

/**
 *
 * @param downloadCount Number of downloads.
 * @param expiresAfterSeconds Number of seconds after which this file expires.
 * @param length Size in bytes of the file to be stored.
 */
data class QuoteRequest(
    /* Number of downloads. */
    val downloadCount: Long = 100,
    /* Number of seconds after which this file expires. */
    val expiresAfterSeconds: Long = 24 * 3600,
    /* Size in bytes of the file to be tored. */
    val length: Long = 0
)

fun FileInfo.toQuoteRequest(expiresAfterSeconds: Long): QuoteRequest {
  return QuoteRequest(
      downloadCount = this.downloadsRemaining,
      expiresAfterSeconds = expiresAfterSeconds,
      length = this.length
  )
}

fun RefreshRequest.toQuoteRequest(length: Long): QuoteRequest {
  return QuoteRequest(
      downloadCount = this.downloadCount,
      expiresAfterSeconds = this.expiresAfterSeconds,
      length = length
  )
}
