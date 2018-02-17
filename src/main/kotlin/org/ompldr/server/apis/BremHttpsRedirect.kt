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

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.URLProtocol
import io.ktor.request.header
import io.ktor.response.respondRedirect
import io.ktor.util.AttributeKey
import io.ktor.util.url

class BremHttpsRedirect(config: Configuration) {
  val redirectPort = config.sslPort
  val permanent = config.permanentRedirect

  class Configuration {
    var sslPort = URLProtocol.HTTPS.defaultPort
    var permanentRedirect = true
  }

  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, BremHttpsRedirect> {
    override val key = AttributeKey<BremHttpsRedirect>("BremHttpsRedirect")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): BremHttpsRedirect {
      val feature = BremHttpsRedirect(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
        val forwardedFor = call.request.header("X-Forwarded-Proto") ?: "https"
        if (forwardedFor == "http") {
          val redirectUrl = call.url { protocol = URLProtocol.HTTPS; port = feature.redirectPort }
          call.respondRedirect(redirectUrl, feature.permanent)
          finish()
        }
      }
      return feature
    }
  }
}
