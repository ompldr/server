/**
 * Copyright 2008 Brenden Matthews <brenden@diddyinc.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ompldr.server.apis

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.escapeIfNeeded
import io.ktor.response.header
import io.ktor.util.AttributeKey
import java.time.Duration
import java.util.HashMap

class BremHSTS(config: Configuration) {
  class Configuration {
    var preload = true
    var includeSubDomains = true
    var maxAge: Duration = Duration.ofDays(365)

    val customDirectives: MutableMap<String, String?> = HashMap()
  }

  // see RFC 6797 https://tools.ietf.org/html/rfc6797
  private val headerValue = buildString {
    append("max-age=")
    append(config.maxAge.toMillis() / 1000L)

    if (config.includeSubDomains) {
      append("; includeSubDomains")
    }
    if (config.preload) {
      append("; preload")
    }

    if (config.customDirectives.isNotEmpty()) {
      config.customDirectives.entries.joinTo(this, separator = "; ", prefix = "; ") {
        if (it.value != null) {
          "${it.key.escapeIfNeeded()}=${it.value?.escapeIfNeeded()}"
        } else {
          it.key.escapeIfNeeded()
        }
      }
    }
  }

  fun intercept(call: ApplicationCall) {
    call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
  }

  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, BremHSTS> {
    override val key = AttributeKey<BremHSTS>("BremHSTS")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): BremHSTS {
      val feature = BremHSTS(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
      return feature
    }
  }
}
