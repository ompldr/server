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

// Use this file to hold package-level internal functions that return receiver object passed to the `install` method.

import io.ktor.features.Compression
import io.ktor.features.deflate
import io.ktor.features.gzip
import io.ktor.features.minimumSize
import mu.KotlinLogging
import org.ompldr.server.apis.BremHSTS
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Application block for [BremHSTS] configuration.
 *
 * This file may be excluded in .swagger-codegen-ignore,
 * and application specific configuration can be applied in this function.
 *
 * See http://ktor.io/features/hsts.html
 */
internal fun ApplicationHstsConfiguration(): BremHSTS.Configuration.() -> Unit {
  return {
    maxAge = Duration.ofDays(365)
    includeSubDomains = true
    preload = true
  }
}

/**
 * Application block for [Compression] configuration.
 *
 * This file may be excluded in .swagger-codegen-ignore,
 * and application specific configuration can be applied in this function.
 *
 * See http://ktor.io/features/compression.html
 */
internal fun ApplicationCompressionConfiguration(): Compression.Configuration.() -> Unit {
  return {
    gzip {
      priority = 1.0
    }
    deflate {
      priority = 10.0
      minimumSize(1024) // condition
    }
  }
}
