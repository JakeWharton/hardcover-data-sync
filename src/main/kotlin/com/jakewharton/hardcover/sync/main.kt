@file:JvmName("Main")

package com.jakewharton.hardcover.sync

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.kevincianfarini.cardiologist.PulseBackpressureStrategy.Companion.SkipNext
import io.github.kevincianfarini.cardiologist.PulseSchedule
import io.github.kevincianfarini.cardiologist.schedulePulse
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createParentDirectories
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.measureTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC

suspend fun main(vararg args: String) {
	MainCommand(
		fileSystem = FileSystems.getDefault(),
		clock = Clock.System,
		timeZone = TimeZone.currentSystemDefault(),
	).main(args)
}

private const val OPERATION_NAME = "MyData"
private val query = """
	|query $OPERATION_NAME {
	|  me {
	|    user_books {
	|      id
	|      book {
	|        ...booksFragment
	|      }
	|      user_book_reads {
	|        id
	|        started_at
	|        paused_at
	|        finished_at
	|        edition {
	|          ...editionsFragment
	|        }
	|      }
	|      rating
	|      reviewed_at
	|      review_raw
	|      review_has_spoilers
	|      private_notes
	|    }
	|    lists {
	|      id
	|      name
	|      list_books {
	|        id
	|        book {
	|          ...booksFragment
	|        }
	|        edition {
	|          ...editionsFragment
	|        }
	|      }
	|    }
	|  }
	|}
	|
	|fragment booksFragment on books {
	|  id
	|  title
	|  contributions {
	|    author {
	|      id
	|      name
	|    }
	|  }
	|}
	|
	|fragment editionsFragment on editions {
	|  id
	|  isbn_13
	|  isbn_10
	|}
	|
""".trimMargin()

private val requestBody = buildJsonObject {
	put("query", query)
	put("operationName", OPERATION_NAME)
}

private val json = Json {
	prettyPrint = true
	prettyPrintIndent = "\t"
}

private class MainCommand(
	private val fileSystem: FileSystem,
	private val clock: Clock,
	private val timeZone: TimeZone,
) : SuspendingCliktCommand("hardcover-data-sync") {
	override fun help(context: Context) = "Output all user data from Hardcover for backup"

	private val debug by option(hidden = true)
		.flag()

	private val bearer by option("--bearer", metavar = "token", envvar = "HARDCOVER_SYNC_TOKEN")
		.help("Bearer token for HTTP 'Authorization' header")
		.required()

	private val output by option("--output", metavar = "file", envvar = "HARDCOVER_SYNC_OUTPUT")
		.default("-")
		.help("Backup destination file, or '-' to write to stdout (default)")

	private val schedule by option("--cron", metavar = "expression", envvar = "HARDCOVER_SYNC_CRON")
		.help("Run command forever and perform sync on this schedule")
		.convert { PulseSchedule.parseCron(it) }

	private val healthCheckId by option("--hc-id", metavar = "id", envvar = "HARDCOVER_SYNC_HC_ID")
		.help("ID of Healthchecks.io service to notify")

	private val healthCheckHost by option("--hc-host", metavar = "url", envvar = "HARDCOVER_SYNC_HC_HOST")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())
		.help("Host of Healthchecks.io service to notify. Requires --hc-id")

	override suspend fun run() {
		val client = OkHttpClient.Builder()
			.apply {
				if (debug) {
					addNetworkInterceptor(
						HttpLoggingInterceptor(::println)
							.setLevel(BASIC),
					)
				}
			}
			.build()

		val healthCheckService = HealthCheckService(healthCheckHost, client)
		val healthCheck = healthCheckId?.let(healthCheckService::newCheck)

		try {
			val schedule = schedule
			if (schedule != null) {
				println("Sync schedule: $schedule")
				val pulse = clock.schedulePulse(schedule, timeZone)
				pulse.beat(strategy = SkipNext) {
					val took = measureTime {
						sync(client, healthCheck)
					}
					val now = clock.now().toLocalDateTime(timeZone).toString()
					println("Done at $now took $took")
				}
				error("unreachable") // https://github.com/kevincianfarini/cardiologist/issues/117
			} else {
				sync(client, healthCheck)
			}
		} finally {
			client.dispatcher.executorService.shutdown()
			client.connectionPool.evictAll()
		}
	}

	private fun sync(client: OkHttpClient, healthCheck: HealthCheck?) {
		val started = healthCheck?.start()

		val request = Request.Builder()
			.url("https://api.hardcover.app/v1/graphql")
			.header("Authorization", "Bearer $bearer")
			.post(requestBody.toString().toRequestBody("application/json".toMediaType()))
			.build()

		val response = client.newCall(request).execute()
		check(response.isSuccessful) { "HTTP ${response.code} ${response.message}" }

		val responseSource = response.body.source()
		val responseJson = json.decodeFromBufferedSource(JsonObject.serializer(), responseSource)

		// GraphQL over HTTP puts _all_ errors into the response because… reasons.
		responseJson["errors"]?.let { errors ->
			System.err.println(errors.toString())
			exitProcess(1)
		}

		// Unwrap GraphQL 'data' envelope and Hardcover 'me' single-element array.
		val responseMe = responseJson
			.getValue("data")
			.jsonObject
			.getValue("me")
			.jsonArray
			.single()

		// Add trailing newline which kotlinx.serialization JSON will not produce.
		val report = json.encodeToString(JsonElement.serializer(), responseMe) + "\n"

		if (output == "-") {
			print(report)
		} else {
			fileSystem.getPath(output).apply {
				createParentDirectories()
				bufferedWriter().use { f ->
					f.write(report)
				}
			}
		}

		started?.complete()
	}
}
