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

import com.github.debop.kodatimes.days
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.ompldr.server.models.Db.dbExecRead
import org.ompldr.server.models.Db.dbExecWrite
import java.math.BigDecimal

object CoinData {
  private object CoinDataModel : LongIdTable() {
    override val tableName: String
      get() = "CoinData"
    val ticker = varchar("ticker", 50)
    val name = varchar("name", 100)
    val price_usd = decimal("price_usd", 20, 3)
    val created_at = datetime("created_at")
    val updated_at = datetime("updated_at")
  }

  fun initializeDb(): Int {
    dbExecWrite {
      create(CoinDataModel)
    }
    return dbExecRead {
      CoinDataModel.selectAll().count()
    }
  }

  fun insertPrice(ticker: String, name: String, price_usd: BigDecimal): Long {
    val result = dbExecWrite {
      // Don't need more than 1 day worth of data in the DB
      CoinDataModel.deleteWhere {
        CoinDataModel.created_at.less(DateTime.now().minus(1.days()))
      }
      CoinDataModel.insertAndGetId {
        it[this.ticker] = ticker
        it[this.name] = name
        it[this.price_usd] = price_usd
        it[this.created_at] = DateTime(DateTimeZone.UTC)
        it[this.updated_at] = DateTime(DateTimeZone.UTC)
      }
    }
    return result?.value ?: 0
  }

  fun getPrice(ticker: String): BigDecimal? {
    return dbExecRead {
      return@dbExecRead CoinDataModel.select { CoinDataModel.ticker eq ticker }
          .orderBy(CoinDataModel.updated_at to false)
          .limit(1)
          .map {
            it[CoinDataModel.price_usd]
          }.firstOrNull()
    }
  }
}
