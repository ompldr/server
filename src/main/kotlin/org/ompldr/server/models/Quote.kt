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
 * @param request The quote request object for this request.
 * @param satoshis Price in Satoshis for this request.
 */
data class Quote(
    /* The quote request object for this request. */
    val quoteRequest: QuoteRequest = QuoteRequest(),
    /* Price in Satoshis for this request. */
    val satoshis: Long = 0,
    /* Price in USD for this request. */
    val usd: Double = 0.0
)
