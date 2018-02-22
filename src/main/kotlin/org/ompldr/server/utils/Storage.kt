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
package org.ompldr.server.utils

import com.github.debop.kodatimes.hours
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.io.jvm.javaio.copyTo
import kotlinx.coroutines.experimental.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.ompldr.server.AES
import org.ompldr.server.RestApi
import org.ompldr.server.models.CoinData
import org.ompldr.server.models.Files.deleteFileRecords
import org.ompldr.server.models.Files.getExpiredFiles
import org.ompldr.server.models.QuoteRequest
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.channels.Channels
import kotlin.math.pow

private val gceProjectId = RestApi.settings.property("ompldr.gce.projectId").getString()
private val gceBucketNamePrefix = RestApi.settings.property("ompldr.gce.bucketNamePrefix").getString()
private val gceBucketPrefix = RestApi.settings.property("ompldr.gce.bucketPrefix").getString()

private val gceStorageRegions = RestApi.settings.property("ompldr.gce.storage.regions").getList()
private val currentStorageRegion = RestApi.settings.property("ompldr.gce.storage.currentRegion").getString()

private val gceStorage = StorageOptions
    .newBuilder()
    .setProjectId(gceProjectId)
    .build().service

private val logger = KotlinLogging.logger {}

fun makeBlobId(blobId: String, createdAt: DateTime, region: String): BlobId {
  return BlobId.of(
      gceBucketNamePrefix + region,
      java.nio.file.Paths.get(gceBucketPrefix,
          "final",
          createdAt.toDateFormat(),
          blobId
      ).toString()
  )
}

fun makeTempBlobId(blobId: String, region: String): BlobId {
  return BlobId.of(
      gceBucketNamePrefix + region,
      java.nio.file.Paths.get(gceBucketPrefix,
          "tmp",
          blobId
      ).toString()
  )
}

fun uploadToTemporaryBlobStorage(
    blobId: String,
    privateKey: String,
    streamProvider: InputStream
): Long {
  val blobInfo = BlobInfo
      .newBuilder(makeTempBlobId(blobId, currentStorageRegion))
      .setContentType("application/octet-stream")
      .build()
  val writer = gceStorage.writer(blobInfo)
  val outputStream = Channels.newOutputStream(writer)
  val cipherStream = AES.getEncryptStream(privateKey, outputStream)
  try {
    streamProvider.use { its ->
      its.buffered().use {
        its.copyTo(cipherStream)
      }
    }
  } finally {
    cipherStream.close()
    outputStream.close()
    writer.close()
  }
  return gceStorage.get(blobInfo.blobId).size
}

fun finalizeStorage(blobId: String,
                    createdAt: DateTime) {
  // Copy data from local (current) region bucket to other region buckets
  launch {
    val tmpBlob = makeTempBlobId(blobId, currentStorageRegion)
    gceStorageRegions.map { region ->
      val destBlob = makeBlobId(blobId, createdAt, region)
      gceStorage.copy(
          Storage.CopyRequest.of(
              tmpBlob,
              destBlob
          )
      )
    }.forEach { it.result }
    gceStorage.delete(tmpBlob)
  }
}

suspend fun downloadFromBlobStorageEncrypted(blobId: String,
                                             createdAt: DateTime,
                                             writer: ByteWriteChannel) {
  val reader = gceStorage.reader(
      makeBlobId(blobId, createdAt, currentStorageRegion)
  )
  val inputStream = Channels.newInputStream(reader)
  try {
    inputStream.use { its ->
      its.buffered().use {
        its.copyTo(writer)
      }
    }
  } finally {
    writer.flush()
    inputStream.close()
    writer.close()
  }
}

fun downloadFromBlobStorage(blobId: String,
                            createdAt: DateTime,
                            privateKey: String,
                            writer: ByteWriteChannel) {
  val reader = gceStorage.reader(
      makeBlobId(blobId, createdAt, currentStorageRegion)
  )
  val cipherStream = AES.getDecryptStream(privateKey, writer.toOutputStream())
  val inputStream = Channels.newInputStream(reader)
  try {
    inputStream.use { its ->
      its.buffered().use {
        its.copyTo(cipherStream)
      }
    }
  } finally {
    cipherStream.flush()
    writer.flush()
    inputStream.close()
    cipherStream.close()
    writer.close()
  }
}

fun deleteBlob(blobId: String, createdAt: DateTime) {
  for (region in gceStorageRegions) {
    gceStorage.delete(
        makeBlobId(blobId, createdAt, region)
    )
  }
}

private val pricePerByteOfStoragePerSecond =
    BigDecimal("0.05") // price per GB in USD
        .divide(BigDecimal(2.0.pow(30))) // convert to bytes
        .divide(BigDecimal(31 * 24 * 3600), RoundingMode.HALF_UP) // seconds in a month
private val pricePerByteofTransfer =
    BigDecimal("0.15") // price per TB in USD
        .divide(BigDecimal(2.0.pow(40))) // convert to bytes

fun calculatePriceInUSD(quoteRequest: QuoteRequest): BigDecimal {
  val totalBytesToServe = BigDecimal(quoteRequest.length * quoteRequest.downloadCount)
  return BigDecimal(0.1) // minimum price, $0.10
      .plus(pricePerByteOfStoragePerSecond
          .multiply(
              totalBytesToServe.multiply(BigDecimal(quoteRequest.expiresAfterSeconds))))
      .plus(pricePerByteofTransfer.multiply(totalBytesToServe))
}

fun calculatePriceInSatoshis(quoteRequest: QuoteRequest): Long {
  val price = CoinData.getPrice("BTC") ?: BigDecimal.valueOf(10000, 0)
  // convert price to $/sat
  val satPrice = price.multiply(BigDecimal.valueOf(1, 8))
  val costInUSD = calculatePriceInUSD(quoteRequest)
  val costInSatoshis = costInUSD.divide(satPrice, RoundingMode.HALF_UP)
  val longRounded = costInSatoshis.round(MathContext(0)).toLong()
  return longRounded
}

fun removeExpiredFiles() {
  val expiredFiles = getExpiredFiles()
  val idsToDelete = expiredFiles.mapNotNull {
    try {
      deleteBlob(it.fileInfo.fileId, it.createdAt)
      it.fileInfo.fileId
    } catch (e: com.google.cloud.storage.StorageException) {
      logger.error("Caught exception when removing expired files", e)
      null
    }
  }
  deleteFileRecords(idsToDelete)
}

fun cleanupTmp() {
  val oneHourAgo = DateTime.now() - 1.hours()
  gceStorageRegions.forEach { region ->
    try {
      gceStorage.list(gceBucketNamePrefix + region,
          Storage.BlobListOption.prefix(
              java.nio.file.Paths.get(gceBucketPrefix, "tmp").toString()
          )
      ).iterateAll().forEach {
        val updatedAt = DateTime(it.updateTime, DateTimeZone.UTC)
        if (updatedAt < oneHourAgo) {
          logger.info {
            "deleting ${it.blobId} from tmp storage in $region"
          }
          gceStorage.delete(it.blobId)
        }
      }
    } catch (e: com.google.cloud.storage.StorageException) {
      logger.error("Caught exception in cleanup", e)
    }
  }
}
