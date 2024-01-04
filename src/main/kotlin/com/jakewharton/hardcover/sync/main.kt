@file:JvmName("Main")

package com.jakewharton.hardcover.sync

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.system.exitProcess
import kotlin.time.measureTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okio.buffer
import okio.sink

fun main(vararg args: String) {
	MainCommand(
		fileSystem = FileSystems.getDefault(),
		clock = Clock.systemDefaultZone(),
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
	|      }
	|    }
	|  }
	|}
	|
	|fragment booksFragment on books {
	|  id
	|  title
	|  default_edition {
	|    ...editionsFragment
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

private val json = Json {
	prettyPrint = true
	prettyPrintIndent = "\t"
}

private class MainCommand(
	fileSystem: FileSystem,
	private val clock: Clock,
) : CliktCommand(
	name = "hardcover-data-sync",
	help = "Download all user data from Hardcover into a folder for backup",
) {
	private val debug by option(hidden = true)
		.flag()

	private val bearer by option("--bearer", metavar = "token")
		.help("Bearer token for HTTP 'Authorization' header")
		.required()

	private val data by argument(name = "dir")
		.help("Directory into which the data will be written")
		.path(canBeFile = false, fileSystem = fileSystem)

	override fun run() {
		val body = buildJsonObject {
			put("query", query)
			put("operationName", OPERATION_NAME)
		}

		val request = Request.Builder()
			.url("https://hardcover-production.hasura.app/v1/graphql")
			.header("Authorization", "Bearer $bearer")
			.post(body.toString().toRequestBody("application/json".toMediaType()))
			.build()

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

		try {
			val took = measureTime {
				val response = client.newCall(request).execute()
				check(response.isSuccessful) { "HTTP ${response.code} ${response.message}" }

				val responseSource = response.body!!.source()
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

				if (data.exists()) {
					// Delete contents of folder, if any.
					data.listDirectoryEntries().forEach(Path::deleteRecursively)
				} else {
					data.createDirectories()
				}

				data.resolve("data.json").sink().buffer().use { fileSink ->
					json.encodeToBufferedSink(JsonElement.serializer(), responseMe, fileSink)

					// Add trailing newline which kotlinx.serialization JSON will not produce.
					fileSink.writeByte('\n'.code)
				}
			}

			val now = ISO_LOCAL_DATE_TIME.format(LocalDateTime.now(clock))
			println("Done at $now took $took")
		} finally {
			client.apply {
				dispatcher.executorService.shutdown()
				connectionPool.evictAll()
			}
		}
	}
}
