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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.typesafe.config.ConfigFactory
import io.ktor.application.Application
import io.ktor.application.ApplicationStopping
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.config.HoconApplicationConfig
import io.ktor.features.AutoHeadResponse
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.locations.Locations
import io.ktor.metrics.Metrics
import io.ktor.routing.Routing
import mu.KotlinLogging
import org.ompldr.server.apis.BremCORS
import org.ompldr.server.apis.BremHSTS
import org.ompldr.server.apis.BremHttpsRedirect
import org.ompldr.server.apis.RateLimiter
import org.ompldr.server.apis.defaultApi
import org.ompldr.server.models.Db.initializeDbs

object RestApi {
  internal val settings = HoconApplicationConfig(ConfigFactory.defaultApplication(HTTP::class.java.classLoader))

  private val logger = KotlinLogging.logger {}

  object HTTP {
    val client = HttpClient(Apache)
  }

  fun Application.main() {
    logger.info {
      "Starting REST API service"
    }
    initializeDbs()
    install(DefaultHeaders)
    val redirectToHttps = settings.propertyOrNull("ompldr.redirectToHttps")?.getString()?.toBoolean()
        ?: false
    if (redirectToHttps) {
      logger.info {
        "installing https filters (redirect & HSTS)"
      }
      install(RateLimiter) {
        this.burst = settings.propertyOrNull("ompldr.ratelimiter.burst")?.getString()?.toInt() ?: this.burst
        this.period = settings.propertyOrNull("ompldr.ratelimiter.period")?.getString()?.toInt() ?: this.period
        this.countLimit = settings.propertyOrNull("ompldr.ratelimiter.countLimit")?.getString()?.toInt() ?: this.countLimit
        this.redisHost = settings.propertyOrNull("ompldr.ratelimiter.redis.host")?.getString() ?: this.redisHost
        this.redisPort = settings.propertyOrNull("ompldr.ratelimiter.redis.port")?.getString()?.toInt() ?: this.redisPort
      }
      install(BremHttpsRedirect)
      install(BremCORS)
      install(BremHSTS, ApplicationHstsConfiguration())
    }
    install(CallLogging)
    install(Metrics) {
      /*      val reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(log)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()
            reporter.start(60, TimeUnit.SECONDS)*/
    }
    install(ContentNegotiation) {
      jackson {
        configure(SerializationFeature.INDENT_OUTPUT, false)
        setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
      }
    }
    install(AutoHeadResponse) // see http://ktor.io/features/autoheadresponse.html
    //install(Compression, ApplicationCompressionConfiguration()) // see http://ktor.io/features/compression.html
    install(Locations) // see http://ktor.io/features/locations.html
    install(Routing) {
      defaultApi()
    }

    environment.monitor.subscribe(ApplicationStopping) {
      HTTP.client.close()
    }
  }
}
