package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.utils.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/**
 * Immutable value object representing a fully-formed external process command.
 *
 * Build one via [Builder], then call [execute] or [executeCapture].
 * Building and executing are intentionally separate: the same [ProcessCommand]
 * can be executed multiple times (e.g. retry logic in [CmdGenerate]).
 */
class ProcessCommand(
    val args: List<String>,
    val workingDir: File? = null,
    /**
     * Exit codes that are considered successful. Defaults to {0}.
     * Use this to accept known-quirky exit codes (e.g. osmium on Windows exits 15).
     */
    val acceptedExitCodes: Set<Int> = setOf(0)
) {

    fun getCmdLine(): String = args.joinToString(" ")

    /**
     * Execute the command, logging all output. Returns the last output line.
     * Throws [IllegalArgumentException] on unexpected exit code.
     */
    fun execute(): String? = runProcess(printError = true)

    /**
     * Like [execute] but does not throw on a non-zero exit code.
     */
    fun executeQuietly(): String? = runProcess(printError = false)

    /**
     * Execute the command and return ALL output lines. Does not throw on non-zero exit.
     */
    fun executeCapture(): List<String> {
        val lines = mutableListOf<String>()
        val process = buildProcessBuilder().start()
        process.inputStream.bufferedReader().useLines { seq -> seq.forEach { lines.add(it) } }
        process.waitFor()
        return lines
    }

    private fun buildProcessBuilder(): ProcessBuilder {
        val pb = ProcessBuilder(args)
        pb.redirectErrorStream(true)
        if (workingDir != null) pb.directory(workingDir)
        return pb
    }

    private fun runProcess(printError: Boolean): String? {
        var lastOutputLine: String? = null
        Logger.i(TAG, getCmdLine() + "\n")
        val process = buildProcessBuilder().start()
        val stdInput = BufferedReader(InputStreamReader(process.inputStream))
        try {
            stdInput.use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Logger.i(TAG, line)
                    lastOutputLine = line
                }
            }
        } catch (ex: IOException) {
            throw IOException(ex.toString())
        }
        val exitVal = process.waitFor()
        if (exitVal !in acceptedExitCodes) {
            Logger.e(TAG, "Wrong return value from sub command, exit value: $exitVal")
            if (printError) {
                throw IllegalArgumentException("Exception running cmd:\n${getCmdLine()}")
            }
        }
        return lastOutputLine
    }

    // -- Builder--

    class Builder(baseArgs: List<String> = emptyList()) {

        private val args = baseArgs.toMutableList()

        fun add(vararg a: String) = apply { args.addAll(a) }

        fun addIf(condition: Boolean, vararg a: String) = apply { if (condition) args.addAll(a) }

        fun addNotBlank(arg: String?) = apply { if (!arg.isNullOrBlank()) args.add(arg) }

        fun getCmdLine(): String = args.joinToString(" ")

        fun build(
            workingDir: File? = null,
            acceptedExitCodes: Set<Int> = setOf(0)
        ) = ProcessCommand(args.toList(), workingDir, acceptedExitCodes)

        fun execute(
            workingDir: File? = null,
            acceptedExitCodes: Set<Int> = setOf(0)
        ): String? = build(workingDir, acceptedExitCodes).execute()

        fun executeQuietly(
            workingDir: File? = null,
            acceptedExitCodes: Set<Int> = setOf(0)
        ): String? = build(workingDir, acceptedExitCodes).executeQuietly()
    }

    companion object {
        private val TAG: String = ProcessCommand::class.java.simpleName
    }
}