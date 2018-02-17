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
package org.ompldr.server.models

import com.github.debop.kodatimes.seconds
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.ompldr.server.AES
import org.ompldr.server.RestApi.settings
import org.ompldr.server.models.Db.dbExecRead
import org.ompldr.server.models.Db.dbExecWrite
import org.ompldr.server.utils.toDateTime
import org.ompldr.server.utils.toISOFormat
import java.util.Base64
import java.util.UUID

object Files {
  private object FilesModel : Table() {
    override val tableName: String
      get() = "Files"
    val id = long("id").autoIncrement().primaryKey() // Column<Int>
    val length = long("length")
    val invoicePaid = bool("invoicePaid")
    val extension = varchar("extension", 50)
    val uuid = uuid("uuid")
    val contentType = varchar("contentType", 100)
    val downloadsRemaining = long("downloadsRemaining")
    val expiresAt = datetime("expiresAt")
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")
  }

  private object RefreshRequestModel : LongIdTable() {
    override val tableName: String
      get() = "RefreshRequest"
    val fileId = long("fileId") references FilesModel.id
    val invoicePaid = bool("invoicePaid")
    val downloadsRemaining = long("downloadsRemaining")
    val expiresAt = datetime("expiresAt")
    val rhash = varchar("rhash", 150)
  }

  private val secretKey = settings.property("ompldr.secrets.aes_secret").getString()

  fun addFileEntry(fileInfo: FileInfo = FileInfo(),
                   uuid: UUID = UUID.randomUUID(),
                   extension: String = ""): Pair<FileInfo, DateTime> {
    val dateTime = DateTime(DateTimeZone.UTC)
    val fileId = dbExecWrite {
      FilesModel.insert {
        it[this.length] = fileInfo.length
        it[this.invoicePaid] = false
        it[this.extension] = extension
        it[this.uuid] = uuid
        it[this.contentType] = fileInfo.contentType
        it[this.downloadsRemaining] = fileInfo.downloadsRemaining
        it[this.expiresAt] = fileInfo.expiresAt.toDateTime()
        it[this.createdAt] = dateTime
        it[this.updatedAt] = dateTime
      } get FilesModel.id
    }
    return Pair(fileInfo.copy(fileId = fileIdEncode(fileId)), dateTime)
  }

  fun markFileInvoicePaid(fileId: String, rhash: String, paid: Boolean = true): Int {
    logger.info {
      "marking invoice for fileId=$fileId paid=$paid with rhash=$rhash"
    }
    // First case: determine if there was a pending refresh, and if so, mark that as paid and get the downloads + expiry time
    val refreshRequest = dbExecRead {
      RefreshRequestModel.select {
        RefreshRequestModel.rhash.eq(rhash)
      }.firstOrNull()
    }
    return if (refreshRequest != null) {
      dbExecWrite {
        val rows = RefreshRequestModel.update({
          RefreshRequestModel.rhash.eq(rhash) and
              RefreshRequestModel.invoicePaid.eq(false)
        }) {
          it[this.invoicePaid] = true
        }
        if (rows > 0) {
          FilesModel.update({ FilesModel.id eq fileIdDecode(fileId) }) {
            it[this.updatedAt] = DateTime.now()
            it[this.expiresAt] = refreshRequest[RefreshRequestModel.expiresAt]
            with(SqlExpressionBuilder) {
              it.update(
                  FilesModel.downloadsRemaining,
                  FilesModel.downloadsRemaining + refreshRequest[RefreshRequestModel.downloadsRemaining]
              )
            }
          }
        } else {
          0
        }
      }
    } else {
      dbExecWrite {
        FilesModel.update({ FilesModel.id eq fileIdDecode(fileId) }) {
          it[this.invoicePaid] = paid
          it[this.updatedAt] = DateTime.now()
        }
      }
    }
  }

  fun decrementDownloads(fileId: String, amount: Long = 1) {
    dbExecWrite {
      FilesModel.update({ FilesModel.id eq fileIdDecode(fileId) }) {
        with(SqlExpressionBuilder) {
          it.update(FilesModel.downloadsRemaining, FilesModel.downloadsRemaining - amount)
        }
      }
    }
  }

  fun initializeDb(): Int {
    dbExecWrite {
      create(FilesModel)
      create(RefreshRequestModel)
    }
    return listOf(
        dbExecRead {
          FilesModel.selectAll().count()
        },
        dbExecRead {
          RefreshRequestModel.selectAll().count()
        }
    ).sum()
  }

  fun fileIdDecode(fileId: String): Long {
    val b64 = Base64.getUrlDecoder()
    val cipherText = b64.decode(fileId)
    return AES.decrypt(cipherText, secretKey).toLong()
  }

  fun fileIdEncode(fileId: Long): String {
    val b64 = Base64.getUrlEncoder().withoutPadding()
    val cipherText = AES.encrypt(fileId.toString(), secretKey)
    return b64.encodeToString(cipherText)
  }

  data class FileInfoWrapper(
      val fileInfo: FileInfo,
      val uuid: UUID,
      val createdAt: DateTime
  )

  private fun getSelectQuery(decodedId: Long, requireInvoicePaid: Boolean): Query {
    return when (requireInvoicePaid) {
      true -> FilesModel.select {
        FilesModel.id.eq(decodedId) and
            FilesModel.expiresAt.greater(DateTime.now()) and
            FilesModel.invoicePaid.eq(true)
      }
      false -> FilesModel.select {
        FilesModel.id.eq(decodedId) and
            FilesModel.expiresAt.greater(DateTime.now())
      }
    }
  }

  fun getFileInfo(fileId: String, requireInvoicePaid: Boolean = false): FileInfoWrapper? {
    val decodedId = fileIdDecode(fileId)
    val result = dbExecRead {
      getSelectQuery(decodedId, requireInvoicePaid).map {
        FileInfoWrapper(
            FileInfo(
                fileId = fileId,
                length = it[FilesModel.length],
                invoicePaid = it[FilesModel.invoicePaid],
                contentType = it[FilesModel.contentType],
                downloadsRemaining = it[FilesModel.downloadsRemaining],
                expiresAt = it[FilesModel.expiresAt].toDateTime(DateTimeZone.UTC).toISOFormat()
            ),
            it[FilesModel.uuid],
            it[FilesModel.createdAt]
        )
      }
    }
    return result.firstOrNull()
  }

  fun getExpiredFiles(): List<FileInfoWrapper> {
    val now = DateTime(DateTimeZone.UTC)
    return dbExecRead {
      FilesModel.select {
        FilesModel.expiresAt.lessEq(now) or (
            FilesModel.invoicePaid.eq(false) and FilesModel.createdAt.less(now - Duration.standardHours(1))
            ) or
            FilesModel.downloadsRemaining.lessEq(0)
      }.map {
            FileInfoWrapper(
                FileInfo(
                    fileId = fileIdEncode(it[FilesModel.id]),
                    length = it[FilesModel.length],
                    invoicePaid = it[FilesModel.invoicePaid],
                    contentType = it[FilesModel.contentType],
                    downloadsRemaining = it[FilesModel.downloadsRemaining],
                    expiresAt = it[FilesModel.expiresAt].toDateTime(DateTimeZone.UTC).toISOFormat()
                ),
                it[FilesModel.uuid],
                it[FilesModel.createdAt]
            )
          }
    }
  }

  fun getUnpaidInvoiceFileIds(): List<String> {
    val now = DateTime(DateTimeZone.UTC)
    return dbExecRead {
      FilesModel.select {
        FilesModel.expiresAt.greater(now) and
            FilesModel.invoicePaid.eq(false) and
            FilesModel.downloadsRemaining.greater(0)
      }.map {
            fileIdEncode(it[FilesModel.id])
          }
    }
  }

  fun deleteFileRecords(fileIdList: List<String>) {
    dbExecWrite {
      fileIdList.forEach {
        RefreshRequestModel.deleteWhere {
          RefreshRequestModel.fileId eq fileIdDecode(it)
        }
        FilesModel.deleteWhere {
          FilesModel.id eq fileIdDecode(it)
        }
      }
    }
  }

  fun addRefreshRequest(
      fileInfo: FileInfo,
      refreshRequest: RefreshRequest,
      rhash: String
  ): Long? {
    return dbExecWrite {
      RefreshRequestModel.insertAndGetId {
        it[this.downloadsRemaining] = refreshRequest.downloadCount
        it[this.expiresAt] = fileInfo.expiresAt.toDateTime() + Math.min(refreshRequest.expiresAfterSeconds, 3600L).seconds()
        it[this.fileId] = fileIdDecode(fileInfo.fileId)
        it[this.rhash] = rhash
        it[this.invoicePaid] = false
      }
    }?.value
  }
}
