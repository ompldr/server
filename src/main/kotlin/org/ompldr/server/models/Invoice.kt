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

import com.github.debop.kodatimes.seconds
import com.google.protobuf.ByteString
import io.grpc.Attributes
import io.grpc.CallCredentials
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NegotiationType
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import kotlinx.coroutines.experimental.time.delay
import lnrpc.LightningGrpc
import lnrpc.Rpc
import mu.KotlinLogging
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.joda.time.DateTime
import org.ompldr.server.RestApi
import org.ompldr.server.models.Db.dbExecRead
import org.ompldr.server.models.Db.dbExecWrite
import org.ompldr.server.models.Files.getUnpaidInvoiceFileIds
import org.ompldr.server.models.Files.markFileInvoicePaid
import org.ompldr.server.utils.toISOFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Executor

/**
 *
 * @param fileInfo File information object.
 * @param invoiceExpiresAt The date at which this invoice expires, in UTC. The file will also be deleted if there is no remaining time (if, for example, this invoice was generated by a refresh request).
 * @param bolt11 The bech32 Bitcoin invoice for this file. See https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md for details.
 * @param privateKey The AES-128 private key for this file's server side encryption. This key is not stored anywhere on Omploader's servers. Clients are responsible for managing the private keys.
 */
data class Invoice(
    /* File information object. */
    val fileInfo: FileInfo = FileInfo(),
    /* The date at which this invoice expires, in UTC.  */
    val invoiceExpiresAt: String = "",
    /* The bech32 Bitcoin invoice for this file. See https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md for details.  */
    val bolt11: String = "",
    /* The AES-128 private key for this file's server side encryption. This key is not stored anywhere on Omploader's servers. Clients are responsible for managing the private keys.  */
    val privateKey: String = ""
)

private val logger = KotlinLogging.logger {}

class MacaroonCallCredential(private val macaroon: String) : CallCredentials {
  override fun thisUsesUnstableApi() {
    // intentionally blank
  }

  override fun applyRequestMetadata(methodDescriptor: MethodDescriptor<*, *>, attributes: Attributes, executor: Executor, metadataApplier: CallCredentials.MetadataApplier) {
    executor.execute {
      try {
        val headers = Metadata()
        val macaroonKey = Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER)
        headers.put(macaroonKey, macaroon)
        metadataApplier.apply(headers)
      } catch (e: Throwable) {
        metadataApplier.fail(Status.UNAUTHENTICATED.withCause(e))
      }
    }
  }
}

object LndRpcClient {
  private val lndRpcCafile = RestApi.settings.property("ompldr.lndrpc.cafile").getString()
  private val lndRpcHost = RestApi.settings.property("ompldr.lndrpc.host").getString()
  private val lndRpcPort = RestApi.settings.property("ompldr.lndrpc.port").getString().toInt()
  private val lndRpcMacaroonPath = RestApi.settings.property("ompldr.lndrpc.macaroonPath").getString()
  private val channel = NettyChannelBuilder.forAddress(lndRpcHost, lndRpcPort)
      .negotiationType(NegotiationType.TLS)
      .sslContext(buildSslContext(lndRpcCafile))
      .build()
  private val stub = LightningGrpc.newBlockingStub(channel).withCallCredentials(
      MacaroonCallCredential(
          Hex.encodeHexString(
              Files.readAllBytes(Paths.get(lndRpcMacaroonPath))
          )
      )
  )

  private fun buildSslContext(trustCertCollectionFilePath: String): SslContext {
    return GrpcSslContexts.forClient()
        .trustManager(File(trustCertCollectionFilePath))
        .build()
  }

  private object InvoiceModel : LongIdTable() {
    override val tableName: String
      get() = "Invoices"
    val memo = varchar("memo", 100)
    val bolt11 = varchar("bolt11", 500)
    val rhash = varchar("rhash", 150)
  }

  fun initializeDb(): Int {
    dbExecWrite {
      create(InvoiceModel)
    }
    return dbExecRead {
      InvoiceModel.selectAll().count()
    }
  }

  private fun addInvoice(
      memo: String,
      bolt11: String,
      rhash: String
  ) {
    dbExecWrite {
      InvoiceModel.insert {
        it[InvoiceModel.memo] = memo
        it[InvoiceModel.bolt11] = bolt11
        it[InvoiceModel.rhash] = rhash
      }
    }
  }

  fun makeInvoice(
      fileInfo: FileInfo,
      price: Long,
      privateKey: String = ""
  ): Pair<Invoice, String> {
    val expirySeconds = 3600L
    val invoiceExpiresAt = DateTime.now() + expirySeconds.seconds()
    val invoice = lnrpc.Rpc.Invoice.newBuilder()
        .setValue(price)
        .setMemo(fileInfo.fileId)
        .setExpiry(expirySeconds)
        .build()
    val response = stub.addInvoice(invoice)

    val rhash = Hex.encodeHexString(response.rHash.toByteArray())
    addInvoice(
        fileInfo.fileId,
        response.paymentRequest,
        rhash
    )

    return Pair(Invoice(
        fileInfo = fileInfo.copy(),
        invoiceExpiresAt = invoiceExpiresAt.toISOFormat(),
        privateKey = privateKey,
        bolt11 = response.paymentRequest
    ), rhash)
  }

  private fun markInvoicePaid(invoice: Rpc.Invoice) {
    if (invoice.settled) {
      markFileInvoicePaid(
          invoice.memo,
          Hex.encodeHexString(invoice.rHash.toByteArray())
      )
    }
  }

  suspend fun subscribeToInvoices() {
    while (true) {
      logger.info("Starting new invoice subscription")
      val subscription = stub.subscribeInvoices(
          lnrpc.Rpc.InvoiceSubscription.newBuilder().build()
      )
      try {
        subscription.forEach {
          // invoice was settled, update DB
          logger.info {
            "invoice received: $it"
          }
          markInvoicePaid(it)
        }
      } catch (e: Exception) {
        logger.error("Caught except in subscription loop", e)
        delay(Duration.ofMillis(100))
      }
    }
  }

  fun checkIfInvoicesUnpaid() {
    logger.info("Checking for unpaid invoices")
    val unpaidFileIds = getUnpaidInvoiceFileIds()
    unpaidFileIds.forEach { fileId ->
      val rHash = dbExecRead {
        InvoiceModel.select {
          InvoiceModel.memo.eq(fileId)
        }.mapNotNull {
              it[InvoiceModel.rhash]
            }
      }.firstOrNull()
      if (rHash != null) {
        logger.info {
          "Found unpaid invoice with rhash=$rHash"
        }
        val response = stub.lookupInvoice(
            lnrpc.Rpc.PaymentHash.newBuilder()
                .setRHash(ByteString.copyFrom(Hex.decodeHex(rHash)))
                .build()
        )
        markInvoicePaid(response)
      }
    }
  }
}
