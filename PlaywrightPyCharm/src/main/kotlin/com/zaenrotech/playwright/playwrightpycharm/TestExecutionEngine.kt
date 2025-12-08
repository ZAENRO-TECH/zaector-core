package com.zaenrotech.playwright.playwrightpycharm

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test Execution Engine
 * Manages running tests with various frameworks (Pytest, Playwright Test, Robot Framework)
 */
class TestExecutionEngine(private val project: Project) {

    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val executionCallbacks = mutableListOf<ExecutionCallback>()
    private var currentExecutionId: String? = null

    /**
     * Run a single test
     */
    fun runTest(testCase: TestCase, framework: TestFramework, callback: ExecutionCallback) {
        val executionId = "test_${System.currentTimeMillis()}"
        currentExecutionId = executionId
        executionCallbacks.clear()
        executionCallbacks.add(callback)

        callback.onStart(executionId)

        Thread {
            try {
                when (framework) {
                    TestFramework.PYTEST -> runPytestTest(executionId, testCase)
                    TestFramework.PLAYWRIGHT_TEST -> runPlaywrightTest(executionId, testCase)
                    TestFramework.ROBOT_FRAMEWORK -> runRobotTest(executionId, testCase)
                    TestFramework.UNITTEST -> runUnittestTest(executionId, testCase)
                }
            } catch (e: Exception) {
                callback.onError(executionId, e.message ?: "Unknown error")
            } finally {
                activeProcesses.remove(executionId)
                callback.onComplete(executionId)
            }
        }.start()
    }

    /**
     * Run all tests in a file
     */
    fun runTestFile(testFile: TestFile, callback: ExecutionCallback) {
        val executionId = "file_${System.currentTimeMillis()}"
        currentExecutionId = executionId
        executionCallbacks.clear()
        executionCallbacks.add(callback)

        callback.onStart(executionId)

        Thread {
            try {
                when (testFile.framework) {
                    TestFramework.PYTEST -> runPytestFile(executionId, testFile)
                    TestFramework.PLAYWRIGHT_TEST -> runPlaywrightFile(executionId, testFile)
                    TestFramework.ROBOT_FRAMEWORK -> runRobotFile(executionId, testFile)
                    TestFramework.UNITTEST -> runUnittestFile(executionId, testFile)
                }
            } catch (e: Exception) {
                callback.onError(executionId, e.message ?: "Unknown error")
            } finally {
                activeProcesses.remove(executionId)
                callback.onComplete(executionId)
            }
        }.start()
    }

    /**
     * Run entire test suite
     */
    fun runTestSuite(testSuite: TestSuite, callback: ExecutionCallback) {
        val executionId = "suite_${System.currentTimeMillis()}"
        currentExecutionId = executionId
        executionCallbacks.clear()
        executionCallbacks.add(callback)

        callback.onStart(executionId)

        Thread {
            try {
                // Collect all test files
                val allFiles = mutableListOf<TestFile>()
                collectFiles(testSuite.folders, allFiles)

                // Group by framework
                val filesByFramework = allFiles.groupBy { it.framework }

                // Run each framework's tests
                filesByFramework.forEach { (framework, files) ->
                    when (framework) {
                        TestFramework.PYTEST -> runPytestSuite(executionId, files, callback)
                        TestFramework.PLAYWRIGHT_TEST -> runPlaywrightSuite(executionId, files, callback)
                        TestFramework.ROBOT_FRAMEWORK -> runRobotSuite(executionId, files, callback)
                        TestFramework.UNITTEST -> runUnittestSuite(executionId, files, callback)
                    }
                }
            } catch (e: Exception) {
                callback.onError(executionId, e.message ?: "Unknown error")
            } finally {
                activeProcesses.remove(executionId)
                callback.onComplete(executionId)
            }
        }.start()
    }

    /**
     * Stop current execution
     */
    fun stopExecution() {
        currentExecutionId?.let { id ->
            activeProcesses[id]?.let { process ->
                process.destroy()
                activeProcesses.remove(id)
                executionCallbacks.forEach { it.onStopped(id) }
            }
        }
    }

    // ============================================
    // PYTEST EXECUTION
    // ============================================

    private fun runPytestTest(executionId: String, testCase: TestCase) {
        val basePath = project.basePath ?: return
        val testPath = "${testCase.filePath}::${testCase.name}"

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("pytest")
            .withParameters("-v", "-s", testPath)

        executeCommand(executionId, command)
    }

    private fun runPytestFile(executionId: String, testFile: TestFile) {
        val basePath = project.basePath ?: return

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("pytest")
            .withParameters("-v", "-s", testFile.path)

        executeCommand(executionId, command)
    }

    private fun runPytestSuite(executionId: String, files: List<TestFile>, callback: ExecutionCallback) {
        val basePath = project.basePath ?: return
        val paths = files.map { it.path }

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("pytest")
            .withParameters("-v", "-s")
            .withParameters(paths)

        executeCommand(executionId, command)
    }

    // ============================================
    // PLAYWRIGHT TEST EXECUTION
    // ============================================

    private fun runPlaywrightTest(executionId: String, testCase: TestCase) {
        val basePath = project.basePath ?: return
        val testPath = testCase.filePath
        val testName = testCase.name

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("npx")
            .withParameters("playwright", "test", testPath, "-g", testName)

        executeCommand(executionId, command)
    }

    private fun runPlaywrightFile(executionId: String, testFile: TestFile) {
        val basePath = project.basePath ?: return

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("npx")
            .withParameters("playwright", "test", testFile.path)

        executeCommand(executionId, command)
    }

    private fun runPlaywrightSuite(executionId: String, files: List<TestFile>, callback: ExecutionCallback) {
        val basePath = project.basePath ?: return
        val paths = files.map { it.path }

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("npx")
            .withParameters("playwright", "test")
            .withParameters(paths)

        executeCommand(executionId, command)
    }

    // ============================================
    // ROBOT FRAMEWORK EXECUTION
    // ============================================

    private fun runRobotTest(executionId: String, testCase: TestCase) {
        val basePath = project.basePath ?: return
        val testPath = testCase.filePath
        val testName = testCase.name

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("robot")
            .withParameters("-t", testName, testPath)

        executeCommand(executionId, command)
    }

    private fun runRobotFile(executionId: String, testFile: TestFile) {
        val basePath = project.basePath ?: return

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("robot")
            .withParameters(testFile.path)

        executeCommand(executionId, command)
    }

    private fun runRobotSuite(executionId: String, files: List<TestFile>, callback: ExecutionCallback) {
        val basePath = project.basePath ?: return
        val paths = files.map { it.path }

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("robot")
            .withParameters(paths)

        executeCommand(executionId, command)
    }

    // ============================================
    // UNITTEST EXECUTION
    // ============================================

    private fun runUnittestTest(executionId: String, testCase: TestCase) {
        val basePath = project.basePath ?: return
        val testPath = "${testCase.filePath.replace("/", ".").removeSuffix(".py")}.${testCase.name}"

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("python")
            .withParameters("-m", "unittest", testPath)

        executeCommand(executionId, command)
    }

    private fun runUnittestFile(executionId: String, testFile: TestFile) {
        val basePath = project.basePath ?: return
        val modulePath = testFile.path.replace("/", ".").removeSuffix(".py")

        val command = GeneralCommandLine()
            .withWorkDirectory(basePath)
            .withExePath("python")
            .withParameters("-m", "unittest", modulePath)

        executeCommand(executionId, command)
    }

    private fun runUnittestSuite(executionId: String, files: List<TestFile>, callback: ExecutionCallback) {
        val basePath = project.basePath ?: return

        // Run each file separately for unittest
        files.forEach { file ->
            runUnittestFile(executionId, file)
        }
    }

    // ============================================
    // COMMAND EXECUTION
    // ============================================

    private fun executeCommand(executionId: String, command: GeneralCommandLine) {
        try {
            val process = command.createProcess()
            activeProcesses[executionId] = process

            // Read stdout
            Thread {
                val reader = BufferedReader(process.inputStream.reader())
                reader.useLines { lines ->
                    lines.forEach { line ->
                        executionCallbacks.forEach { callback ->
                            callback.onOutput(executionId, "$line\n")
                            parseTestResult(executionId, line, callback)
                        }
                    }
                }
            }.start()

            // Read stderr
            Thread {
                val reader = BufferedReader(process.errorStream.reader())
                reader.useLines { lines ->
                    lines.forEach { line ->
                        executionCallbacks.forEach { callback ->
                            callback.onOutput(executionId, "[ERROR] $line\n")
                        }
                    }
                }
            }.start()

            // Wait for completion
            val exitCode = process.waitFor()

            executionCallbacks.forEach { callback ->
                if (exitCode == 0) {
                    callback.onSuccess(executionId)
                } else {
                    callback.onError(executionId, "Process exited with code $exitCode")
                }
            }

        } catch (e: Exception) {
            executionCallbacks.forEach { callback ->
                callback.onError(executionId, e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Parse test output to extract results
     */
    private fun parseTestResult(executionId: String, line: String, callback: ExecutionCallback) {
        // Pytest patterns
        when {
            line.contains("PASSED") -> {
                val testName = extractTestName(line)
                if (testName != null) {
                    callback.onTestPassed(executionId, testName)
                }
            }
            line.contains("FAILED") -> {
                val testName = extractTestName(line)
                if (testName != null) {
                    callback.onTestFailed(executionId, testName, line)
                }
            }
            line.contains("SKIPPED") -> {
                val testName = extractTestName(line)
                if (testName != null) {
                    callback.onTestSkipped(executionId, testName)
                }
            }
        }
    }

    /**
     * Extract test name from output line
     */
    private fun extractTestName(line: String): String? {
        // Pytest format: test_file.py::test_name PASSED
        val pytestRegex = """([\w/]+\.py)::([\w_]+)\s+(PASSED|FAILED|SKIPPED)""".toRegex()
        val match = pytestRegex.find(line)
        if (match != null) {
            return match.groupValues[2]
        }

        // Playwright Test format: [chromium] › test_file.spec.ts:10:5 › test name
        val playwrightRegex = """\[[\w]+\]\s+›\s+[\w./]+:\d+:\d+\s+›\s+([\w\s]+)""".toRegex()
        val pwMatch = playwrightRegex.find(line)
        if (pwMatch != null) {
            return pwMatch.groupValues[1].trim()
        }

        return null
    }

    /**
     * Collect all files from folders recursively
     */
    private fun collectFiles(folders: List<TestFolder>, result: MutableList<TestFile>) {
        folders.forEach { folder ->
            result.addAll(folder.files)
            collectFiles(folder.subfolders, result)
        }
    }
}

/**
 * Execution callback interface
 */
interface ExecutionCallback {
    fun onStart(executionId: String)
    fun onOutput(executionId: String, output: String)
    fun onTestPassed(executionId: String, testName: String)
    fun onTestFailed(executionId: String, testName: String, error: String)
    fun onTestSkipped(executionId: String, testName: String)
    fun onSuccess(executionId: String)
    fun onError(executionId: String, error: String)
    fun onStopped(executionId: String)
    fun onComplete(executionId: String)
}
