/**
 * Copyright 2018 Brenden Matthews <brenden@diddyinc.com>
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
import io.ktor.response.header
import io.ktor.util.AttributeKey

class BremCORS(config: Configuration) {
  class Configuration
  fun intercept(call: ApplicationCall) {
    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
    call.response.header(HttpHeaders.AccessControlAllowMethods, "*")
  }

  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, BremCORS> {
    override val key = AttributeKey<BremCORS>("BremCORS")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): BremCORS {
      val feature = BremCORS(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
      return feature
    }
  }
}
