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
package org.ompldr.server.models

import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.ompldr.server.RestApi.settings
import javax.sql.DataSource

object Db {
  private val dbWriterUrl = settings.property("ompldr.database.url.writer").getString()
  private val dbReaderUrl = settings.property("ompldr.database.url.reader").getString()
  private val dbDriver = settings.property("ompldr.database.driver").getString()
  private val dbUser = settings.property("ompldr.database.user").getString()
  private val dbPassword = settings.property("ompldr.database.password").getString()

  fun setupDataSource(
      dbUrl: String,
      dbDriver: String,
      dbUser: String,
      dbPassword: String
  ): DataSource {
    val cpds = DriverAdapterCPDS()
    cpds.driver = dbDriver
    cpds.url = dbUrl
    cpds.user = dbUser
    cpds.password = dbPassword
    val tds = SharedPoolDataSource()
    tds.connectionPoolDataSource = cpds
    tds.maxTotal = 10
    return tds
  }

  val writerDs =
      setupDataSource(dbWriterUrl, dbDriver, dbUser, dbPassword)
  val readerDs =
      setupDataSource(dbReaderUrl, dbDriver, dbUser, dbPassword)

  fun <T> dbExecWrite(statement: Transaction.() -> T): T {
    Database.connect(writerDs)
    return transaction(statement)
  }

  fun <T> dbExecRead(statement: Transaction.() -> T): T {
    Database.connect(readerDs)
    return transaction(statement)
  }

  fun initializeDbs() {
    Files.initializeDb()
    CoinData.initializeDb()
    LndRpcClient.initializeDb()
  }
}
