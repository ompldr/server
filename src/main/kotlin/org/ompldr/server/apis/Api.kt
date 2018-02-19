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
package org.ompldr.server.apis

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.github.debop.kodatimes.seconds
import io.ktor.application.call
import io.ktor.content.OutgoingContent
import io.ktor.content.PartData
import io.ktor.content.forEachPart
import io.ktor.content.resolveResource
import io.ktor.features.UnsupportedMediaTypeException
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.get
import io.ktor.request.isMultipart
import io.ktor.request.receive
import io.ktor.request.receiveMultipart
import io.ktor.response.cacheControl
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.ompldr.server.Paths
import org.ompldr.server.models.FileInfo
import org.ompldr.server.models.Files.addFileEntry
import org.ompldr.server.models.Files.addRefreshRequest
import org.ompldr.server.models.Files.decrementDownloads
import org.ompldr.server.models.Files.getFileInfo
import org.ompldr.server.models.LndRpcClient.makeInvoice
import org.ompldr.server.models.Quote
import org.ompldr.server.models.QuoteRequest
import org.ompldr.server.models.RefreshRequest
import org.ompldr.server.models.Response
import org.ompldr.server.models.toQuoteRequest
import org.ompldr.server.utils.calculatePriceInSatoshis
import org.ompldr.server.utils.calculatePriceInUSD
import org.ompldr.server.utils.downloadFromBlobStorage
import org.ompldr.server.utils.downloadFromBlobStorageEncrypted
import org.ompldr.server.utils.finalizeStorage
import org.ompldr.server.utils.toISOFormat
import org.ompldr.server.utils.uploadToTemporaryBlobStorage
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

fun Route.defaultApi() {
  get { _: Paths.indexGet ->
    call.response.cacheControl(CacheControl.MaxAge(100))
    call.respondText {
      "hey, nothing here but thanks for stopping by\n"
    }
  }

  get { _: Paths.pingGet ->
    call.response.cacheControl(CacheControl.MaxAge(100))
    call.respondText { "pong\n" }
  }

  get { _: Paths.swaggerYamlGet ->
    call.response.cacheControl(CacheControl.MaxAge(24 * 3600 * 7))
    call.respond(call.resolveResource("swagger.yaml")!!)
  }

  route("/v2") {
    get { it: Paths.getEncryptedFile ->
      try {
        val result = getFileInfo(it.id, requireInvoicePaid = true)
        if (result != null) {
          call.response.cacheControl(CacheControl.MaxAge(24 * 3600))
          call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType?
              get() = ContentType.parse(result.fileInfo.contentType)

            suspend override fun writeTo(channel: ByteWriteChannel) {
              downloadFromBlobStorageEncrypted(
                  result.uuid.toString(),
                  result.createdAt,
                  channel)
              channel.flush()
              channel.close()
            }
          })
          launch {
            decrementDownloads(it.id)
          }
        } else {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        }
      } catch (e: java.lang.IllegalArgumentException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: java.security.GeneralSecurityException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: Exception) {
        logger.error("Caught exception", e)
        call.respond(HttpStatusCode.InternalServerError, Response("Exception caught D:"))
      }
    }

    get { it: Paths.getFile ->
      try {
        val result = getFileInfo(it.id, requireInvoicePaid = true)
        if (result != null) {
          call.response.cacheControl(CacheControl.MaxAge(24 * 3600))
          call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType?
              get() = ContentType.parse(result.fileInfo.contentType)

            suspend override fun writeTo(channel: ByteWriteChannel) {
              downloadFromBlobStorage(
                  result.uuid.toString(),
                  result.createdAt,
                  it.privateKey,
                  channel)
              channel.flush()
              channel.close()
            }
          })
          launch {
            decrementDownloads(it.id)
          }
        } else {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        }
      } catch (e: java.lang.IllegalArgumentException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: java.security.GeneralSecurityException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: Exception) {
        logger.error("Caught exception", e)
        call.respond(HttpStatusCode.InternalServerError, Response("Exception caught D:"))
      }
    }

    get { it: Paths.getInfo ->
      try {
        val result = getFileInfo(it.id)
        if (result != null) {
          val (info, _) = result
          call.response.cacheControl(CacheControl.MaxAge(1))
          call.respond(info)
        } else {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        }
      } catch (e: java.lang.IllegalArgumentException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: java.security.GeneralSecurityException) {
        call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
      } catch (e: Exception) {
        logger.error("Caught exception", e)
        call.respond(HttpStatusCode.InternalServerError, Response("Exception caught D:"))
      }
    }

    route("/refresh/{id}") {
      put {
        try {
          val refreshRequest = call.receive<RefreshRequest>()
          val id = call.parameters["id"] ?: ""
          val result = getFileInfo(id)
          if (result != null) {
            val price = calculatePriceInSatoshis(
                refreshRequest.toQuoteRequest(result.fileInfo.length)
            )
            val (invoice, rhash) = makeInvoice(
                result.fileInfo,
                price
            )
            if (addRefreshRequest(result.fileInfo, refreshRequest, rhash) != null) {
              call.respond(invoice)
            } else {
              call.respond(HttpStatusCode.InternalServerError, Response("Couldn't write request into the DB :("))
            }
          } else {
            call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
          }
        } catch (e: MismatchedInputException) {
          call.respond(HttpStatusCode.BadRequest, Response("That doesn't look right to me."))
        } catch (e: UnsupportedMediaTypeException) {
          call.respond(HttpStatusCode.BadRequest, Response("That doesn't look right to me."))
        } catch (e: java.lang.IllegalArgumentException) {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        } catch (e: java.security.GeneralSecurityException) {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        } catch (e: Exception) {
          logger.error("Caught exception", e)
          call.respond(HttpStatusCode.InternalServerError, Response("Exception caught D:"))
        }
      }
    }

    route("/upload") {
      post {
        try {
          if (call.request.isMultipart()) {
            val randomInstance = SecureRandom.getInstance("NativePRNGNonBlocking")
            val randomBytes = ByteArray(18)
            randomInstance.nextBytes(randomBytes)

            var fileInfo = FileInfo()
            var expiresAfterSeconds = 3600L
            var extension = ""

            val currentDate = DateTime.now(DateTimeZone.UTC)
            val b64 = Base64.getUrlEncoder().withoutPadding()
            val privateKey = b64.encodeToString(randomBytes)
            val uuid = UUID.randomUUID()

            val multipart = call.receiveMultipart()

            multipart.forEachPart {
              if (it is PartData.FormItem) {
                when (it.partName) {
                  "downloadCount" -> {
                    val downloadsRemaining = it.value.toLong()
                    fileInfo = fileInfo.copy(downloadsRemaining = downloadsRemaining)
                  }
                  "expiresAfterSeconds" -> {
                    expiresAfterSeconds = Math.max(it.value.toLong(), expiresAfterSeconds)
                  }
                }
              } else if (it is PartData.FileItem && it.partName == "file" && fileInfo.length == 0L) {
                extension = File(it.originalFileName).extension
                if (it.contentType != null) {
                  fileInfo = fileInfo.copy(contentType = it.contentType.toString())
                }
                fileInfo = fileInfo.copy(length = uploadToTemporaryBlobStorage(
                    uuid.toString(),
                    privateKey,
                    it.streamProvider())
                )
              }
              it.dispose()
            }
            val expiresAt = (currentDate + expiresAfterSeconds.seconds()).toISOFormat()

            // Write record in DB
            val fileInfoResult = addFileEntry(
                fileInfo.copy(expiresAt = expiresAt),
                uuid,
                extension)

            // DB write succeeded
            fileInfo = fileInfoResult.first
            val createdAt = fileInfoResult.second
            val price = calculatePriceInSatoshis(fileInfo.toQuoteRequest(expiresAfterSeconds))
            val (invoice, _) = makeInvoice(
                fileInfo,
                price,
                privateKey
            )

            finalizeStorage(uuid.toString(), createdAt)

            call.respond(invoice)
          } else {
            call.respond(HttpStatusCode.BadRequest, Response(message = "Expected multipart form"))
          }
        } catch (e: Exception) {
          logger.error("Caught exception", e)
          call.respond(HttpStatusCode.InternalServerError, Response(message = "Unknown error :("))
        }
      }
    }

    route("/quote") {
      post {
        try {
          val quoteRequest = call.receive<QuoteRequest>()
          call.respond(Quote(
              quoteRequest = quoteRequest,
              satoshis = calculatePriceInSatoshis(quoteRequest),
              usd = calculatePriceInUSD(quoteRequest).toDouble()
          ))
        } catch (e: MismatchedInputException) {
          call.respond(HttpStatusCode.BadRequest, Response("That doesn't look right to me."))
        } catch (e: UnsupportedMediaTypeException) {
          call.respond(HttpStatusCode.BadRequest, Response("That doesn't look right to me."))
        } catch (e: java.lang.IllegalArgumentException) {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        } catch (e: java.security.GeneralSecurityException) {
          call.respond(HttpStatusCode.NotFound, Response("Not found ¯\\_(ツ)_/¯"))
        } catch (e: Exception) {
          logger.error("Caught exception", e)
          call.respond(HttpStatusCode.InternalServerError, Response("Exception caught D:"))
        }
      }
    }
  }
}
