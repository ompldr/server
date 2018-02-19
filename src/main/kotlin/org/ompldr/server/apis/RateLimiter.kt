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

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.request.path
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.util.AttributeKey
import mu.KotlinLogging
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisException

private val logger = KotlinLogging.logger {}

class RateLimiter(config: Configuration) {
  val redisHost = config.redisHost
  val redisPort = config.redisPort
  val countLimit = config.countLimit
  val period = config.period
  val burst = config.burst

  class Configuration {
    var redisHost = "localhost"
    var redisPort = 6379
    var countLimit = 30
    var period = 60
    var burst = 15
  }

  private val ignoredPaths = setOf("/ping")

  var pool = JedisPool(JedisPoolConfig(), redisHost, redisPort)

  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, RateLimiter> {
    override val key = AttributeKey<RateLimiter>("RateLimiter")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): RateLimiter {
      val feature = RateLimiter(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
        if (call.request.path() !in feature.ignoredPaths && call.request.headers.contains("X-Forwarded-For")) {
          feature.pool.resource.use { jedis ->
            try {
              val forwardedFor =
                  call.request.header("X-Forwarded-For")
                      ?.split(",")
                      ?.firstOrNull()
                      ?.trim()
                      ?: "local"

              val keys = listOf(forwardedFor)
              val args = listOf(
                  feature.burst.toString(),
                  feature.countLimit.toString(),
                  feature.period.toString()
              )
              @Suppress("UNCHECKED_CAST")
              val response = jedis.eval(
                  "return {redis.call('CL.THROTTLE',KEYS[1],ARGV[1],ARGV[2],ARGV[3])}",
                  keys,
                  args
              ) as ArrayList<ArrayList<Long>>

              val wasLimited = response[0][0]
              val limit = response[0][1]
              val limitRemaining = response[0][2]
              val retryAfter = response[0][3]
              val limitReset = response[0][4]

              call.response.header("X-RateLimit-Limit", limit)
              call.response.header("X-RateLimit-Remaining", limitRemaining)
              call.response.header("X-RateLimit-Reset", limitReset)

              if (wasLimited == 1L) {
                call.response.header("Retry-After", retryAfter)
                call.respond(HttpStatusCode.TooManyRequests)
                finish()
              }
            } catch (e: JedisException) {
              logger.error("Got an exception from Redis", e)
            }
          }
        }
      }
      return feature
    }
  }
}
