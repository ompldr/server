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
 * @param downloadCount Additional number of downloads.
 * @param expiresAfterSeconds Additional number of seconds after which this file will expire.
 */
data class RefreshRequest(
    /* Additional number of downloads. */
    val downloadCount: Long = 100,
    /* Additional number of seconds after which this file will expire. */
    val expiresAfterSeconds: Long = 24 * 3600
)
