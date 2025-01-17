/*
 * Copyright (C) 2019 Anton Malinskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.malinskiy.adam.request.transform

import com.malinskiy.adam.Const
import com.malinskiy.adam.request.testrunner.*

class InstrumentationResponseTransformer : ResponseTransformer<List<TestEvent>?> {
    var buffer = StringBuffer()

    var startReported = false
    var finishReported = false
    var finished = false
    var testsExpected = 0
    var testsExecuted = 0

    override suspend fun process(bytes: ByteArray, offset: Int, limit: Int) {
        buffer.append(String(bytes, offset, limit, Const.DEFAULT_TRANSPORT_ENCODING))
    }

    override fun transform(): List<TestEvent>? {
        val lines = buffer.lines()

        val tokenPosition = lines.indexOfFirst {
            it.startsWith(TokenType.INSTRUMENTATION_STATUS_CODE.name) ||
                    it.startsWith(TokenType.INSTRUMENTATION_CODE.name)
        }

        if (tokenPosition == -1) {
            return null
        }

        val atom = lines.subList(0, tokenPosition + 1)
        buffer = buffer.delete(0, atom.map { it.length }.reduce { acc, i -> acc + i + 1 })

        return parse(atom)
    }

    fun close(): List<TestEvent>? {
        if (finishReported) return null

        return if (!startReported && !finished) {
            listOf(TestRunFailed("No test results"))
        } else if (testsExpected > testsExecuted) {
            listOf(TestRunFailed("Test run failed to complete. Expected $testsExpected tests, executed $testsExecuted"))
        } else {
            val events = mutableListOf<TestEvent>()
            if (!startReported) {
                events.add(TestRunStartedEvent(0))
            }

            events.add(TestRunEnded(0, emptyMap()))
            finishReported = true
            events
        }
    }

    private fun parse(atom: List<String>): List<TestEvent>? {
        val last = atom.last()
        return when {
            last.startsWith(TokenType.INSTRUMENTATION_STATUS_CODE.name) -> {
                parseStatusCode(last, atom.subList(0, atom.size - 1))
            }
            last.startsWith(TokenType.INSTRUMENTATION_CODE.name) -> {
                finished = true
                parseInstrumentationCode(last, atom)
            }
            last.startsWith(TokenType.INSTRUMENTATION_FAILED.name) -> {
                finished = true
                null
            }
            else -> null
        }
    }

    private fun parseStatusCode(last: String, atom: List<String>): List<TestEvent>? {
        val value = last.substring(TokenType.INSTRUMENTATION_STATUS_CODE.name.length + 1).trim()
        val parameters: Map<String, String> = atom.toMap()

        val events = mutableListOf<TestEvent>()
        /**
         * Send [TestRunStartedEvent] if not done yet
         */
        if (!startReported) {
            val tests = parameters["numtests"]?.toInt()
            tests?.let {
                events.add(TestRunStartedEvent(it))
                testsExpected = tests
            }
            startReported = true
        }

        val metrics = emptyMap<String, String>()

        when (Status.valueOf(value.toIntOrNull())) {
            Status.SUCCESS -> {
                val className = parameters["class"]
                val testName = parameters["test"]
                if (className != null && testName != null) {
                    events.add(TestEnded(TestIdentifier(className, testName), metrics))
                }
                testsExecuted += 1
            }
            Status.START -> {
                val className = parameters["class"]
                val testName = parameters["test"]
                if (className != null && testName != null) {
                    events.add(TestStarted(TestIdentifier(className, testName)))
                }
            }
            Status.IN_PROGRESS -> Unit
            Status.ERROR, Status.FAILURE -> {
                val className = parameters["class"]
                val testName = parameters["test"]
                val stack = parameters["stack"]
                if (className != null && testName != null && stack != null) {
                    val id = TestIdentifier(className, testName)
                    events.add(TestFailed(id, stack))
                    events.add(TestEnded(id, metrics))
                }
                testsExecuted += 1
            }
            Status.IGNORED -> {
                val className = parameters["class"]
                val testName = parameters["test"]
                if (className != null && testName != null) {
                    val id = TestIdentifier(className, testName)
                    events.add(TestStarted(id))
                    events.add(TestIgnored(id))
                    events.add(TestEnded(id, metrics))
                }
                testsExecuted += 1
            }
            Status.ASSUMPTION_FAILURE -> {
                val className = parameters["class"]
                val testName = parameters["test"]
                val stack = parameters["stack"]
                if (className != null && testName != null && stack != null) {
                    val id = TestIdentifier(className, testName)
                    events.add(TestAssumptionFailed(id, stack))
                    events.add(TestEnded(id, metrics))
                }
                testsExecuted += 1
            }
            Status.UNKNOWN -> TODO()
        }

        return if (events.isNotEmpty()) {
            events
        } else {
            null
        }
    }

    private fun parseInstrumentationCode(
        last: String,
        atom: List<String>
    ): List<TestEvent>? {
        val value = last.substring(TokenType.INSTRUMENTATION_CODE.name.length + 1).trim()
        val code = value.toIntOrNull()
        return when (Status.valueOf(code)) {
            Status.ERROR -> {
                var time = 0L
                val metrics = mutableMapOf<String, String>()

                atom.forEach { line ->
                    when {
                        line.startsWith("Time: ") -> {
                            time = line.substring(6).toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
                        }
                    }
                }
                finishReported = true
                listOf(TestRunEnded(time, metrics))
            }
            else -> null
            //                    Status.SUCCESS -> {
            //                        TestRunEnded()
            //                    }
        }
    }
}

private fun List<String>.toMap(): Map<String, String> {
    return this.filter { it.isNotEmpty() }.joinToString(separator = "\n").split("INSTRUMENTATION_STATUS: ").mapNotNull {
        val split = it.trim().split("=")
        if (split.size != 2) return@mapNotNull null
        Pair(split[0], split[1].trim())
    }.toMap()
}

enum class TokenType {
    INSTRUMENTATION_STATUS,
    INSTRUMENTATION_STATUS_CODE,
    INSTRUMENTATION_RESULT,
    INSTRUMENTATION_CODE,
    INSTRUMENTATION_FAILED
}

enum class Status(val value: Int) {
    SUCCESS(0),
    START(1),
    IN_PROGRESS(2),
    /**
     * JUnit3 runner code, treated as FAILURE
     */
    ERROR(-1),
    FAILURE(-2),
    IGNORED(-3),
    ASSUMPTION_FAILURE(-4),
    UNKNOWN(6666);

    companion object {
        fun valueOf(value: Int?): Status {
            return when (value) {
                0 -> SUCCESS
                1 -> START
                2 -> IN_PROGRESS
                -1 -> ERROR
                -2 -> FAILURE
                -3 -> IGNORED
                -4 -> ASSUMPTION_FAILURE
                else -> UNKNOWN
            }
        }
    }
}