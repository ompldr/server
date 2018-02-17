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
package org.ompldr.server

import io.ktor.application.Application
import io.ktor.application.ApplicationStopping
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import org.ompldr.server.models.Db.initializeDbs
import org.ompldr.server.models.LndRpcClient.checkIfInvoicesUnpaid
import org.ompldr.server.models.LndRpcClient.subscribeToInvoices

object InvoiceService {
  private val logger = KotlinLogging.logger {}

  object HTTP {
    val client = HttpClient(Apache)
  }

  fun Application.main() {
    logger.info {
      "Starting invoice service"
    }
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
      get("/") {
        call.respondText("Hello, World!")
      }
      get("/ping") {
        call.respondText("pong")
      }
    }
    initializeDbs()
    launch {
      while (true) {
        launch {
          checkIfInvoicesUnpaid()
        }
        delay(5 * 60 * 1000)
      }
    }
    launch {
      subscribeToInvoices()
    }
    environment.monitor.subscribe(ApplicationStopping) {
      HTTP.client.close()
    }
  }
}
