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

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

fun DateTime.toISOFormat(): String {
  val fmt = ISODateTimeFormat.dateTime()
  return fmt.print(this)
}

fun DateTime.toDateFormat(): String {
  val fmt = ISODateTimeFormat.date()
  return fmt.print(this)
}

fun String.toDateTime(): DateTime {
  val fmt = ISODateTimeFormat.dateTime()
  return fmt.parseDateTime(this)
}
