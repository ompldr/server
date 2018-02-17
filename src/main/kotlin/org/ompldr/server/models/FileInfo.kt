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
 * @param fileId Unique identifier for file object.
 * @param length Length in bytes of the file.
 * @param invoicePaid True if the invoice has been paid.
 * @param contentType MIME type of this file.
 * @param downloadsRemaining Number of downloads remaining. Once this number reaches 0, the file will be deleted.
 * @param expiresAt The time at which this file will expire. Once this date is reached, the file will be deleted.
 */
data class FileInfo(
    /* Unique identifier for file object. */
    val fileId: String = "",
    /* Length in bytes of the file. */
    val length: Long = 0,
    /* True if the invoice has been paid. */
    val invoicePaid: Boolean = false,
    /* Content type of this file. */
    val contentType: String = "application/octet-stream",
    /* Number of downloads remaining. Once this number reaches 0, the file will be deleted.  */
    val downloadsRemaining: Long = 100,
    /* The time at which this file will expire. Once this date is reached, the file will be deleted.  */
    val expiresAt: String = "2018-02-04T03:18:00.040Z"
)
