package com.dougie.cli

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.runtime.TaskManager
import com.jakewharton.mosaic.runMosaic
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.default
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal fun parseLogOnly(args: Array<String>): Boolean {
    val parser = ArgParser("dougie-cli")
    val logOnly by parser.option(
        ArgType.Boolean,
        fullName = "log-only",
        description = "Print task status to stdout (no mosaic TTY)",
    ).default(false)
    parser.parse(args)
    return logOnly
}

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val logOnly = parseLogOnly(args)

    runBlocking {
        val dispatcher = Dispatchers.Default
        val manager = fakeBatteryManager(dispatcher, this, stepDelayMs = 40L)
        val terminal = if (logOnly) {
            runUntilDone(manager, print = true)
        } else {
            runMosaicOrLog(manager)
        }
        if (terminal.status != TaskStatus.COMPLETED) {
            System.err.println(formatSnapshot(terminal))
            exitProcess(1)
        }
    }
}

/**
 * Mosaic 0.14 has no NonInteractivePolicy (added in 0.17+). Try mosaic;
 * if TTY/raw-mode setup fails, print the same snapshot lines.
 */
private suspend fun runMosaicOrLog(manager: TaskManager): AgentTask {
    var mosaicFailed = false
    return coroutineScope {
        val mosaicJob = launch {
            try {
                runMosaic {
                    MosaicConsole(manager.task)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mosaicFailed = true
            }
        }
        val printed = mutableListOf<String>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            manager.task.collect { snapshot ->
                if (snapshot != null) {
                    printed += formatSnapshot(snapshot)
                }
            }
        }
        val terminal = runUntilDone(manager, print = false)
        mosaicJob.cancelAndJoin()
        collector.cancelAndJoin()
        if (mosaicFailed) {
            printed.forEach(::println)
        }
        terminal
    }
}

@Composable
private fun MosaicConsole(taskFlow: StateFlow<AgentTask?>) {
    var task by remember { mutableStateOf(taskFlow.value) }
    LaunchedEffect(taskFlow) {
        taskFlow.collect { task = it }
    }
    Column {
        Text("Dougie CLI (fake battery loop)")
        Text(formatSnapshot(task))
    }
}

private suspend fun runUntilDone(manager: TaskManager, print: Boolean): AgentTask {
    return coroutineScope {
        val printer = if (print) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                manager.task.collect { snapshot ->
                    if (snapshot != null) {
                        println(formatSnapshot(snapshot))
                    }
                }
            }
        } else {
            null
        }
        manager.submit(FAKE_BATTERY_PROMPT)
        val terminal = manager.task.filterNotNull().first { snapshot ->
            snapshot.status == TaskStatus.COMPLETED || snapshot.status == TaskStatus.FAILED
        }
        printer?.cancelAndJoin()
        if (print) {
            println(formatSnapshot(terminal))
        }
        terminal
    }
}

internal fun formatSnapshot(task: AgentTask?): String {
    if (task == null) return "status=IDLE"
    val traces = task.toolTrace.takeLast(3).joinToString(";") { entry ->
        "${entry.toolName}:${entry.status}"
    }
    val end = task.finalAnswer ?: task.lastError.orEmpty()
    return "taskId=${task.taskId} status=${task.status} loop=${task.loopCount} tools=$traces end=$end"
}
