package com.zaenrotech.playwright.playwrightpycharm

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Manages test suite configurations
 * Loads suite definitions from .playwright-suites.json
 */
class TestSuiteConfigManager(private val project: Project) {

    private val gson = Gson()
    private val configFileName = ".playwright-suites.json"
    private var cachedConfigs: List<TestSuiteConfig>? = null

    /**
     * Get all configured test suites
     */
    fun getSuiteConfigs(): List<TestSuiteConfig> {
        if (cachedConfigs != null) {
            return cachedConfigs!!
        }

        val configs = loadConfigsFromFile() ?: getDefaultConfigs()
        cachedConfigs = configs
        return configs
    }

    /**
     * Get the config file location (from settings or project root default)
     */
    private fun getConfigFile(): File? {
        val settings = PlaywrightSettings.instance
        val configPath = settings.suiteConfigPath

        return if (configPath.isNotEmpty()) {
            // Use configured path
            File(configPath, configFileName)
        } else {
            // Use project root as default
            val basePath = project.basePath ?: return null
            File(basePath, configFileName)
        }
    }

    /**
     * Load suite configs from .playwright-suites.json
     */
    private fun loadConfigsFromFile(): List<TestSuiteConfig>? {
        val configFile = getConfigFile() ?: return null

        if (!configFile.exists()) {
            println("[SuiteConfig] No config file found at: ${configFile.absolutePath}")
            return null
        }

        return try {
            val json = configFile.readText()
            val configWrapper: ConfigWrapper = gson.fromJson(json, ConfigWrapper::class.java)
            println("[SuiteConfig] Loaded ${configWrapper.suites.size} suite configs")
            configWrapper.suites
        } catch (e: Exception) {
            println("[SuiteConfig] Error loading config: ${e.message}")
            null
        }
    }

    /**
     * Get default suite configurations
     */
    private fun getDefaultConfigs(): List<TestSuiteConfig> {
        return listOf(
            TestSuiteConfig(
                name = "All Tests",
                pattern = "**/*",
                exclude = emptyList(),
                browsers = listOf("chromium"),
                tags = emptyList(),
                parallel = 1,
                timeout = 30000,
                retries = 0
            ),
            TestSuiteConfig(
                name = "Smoke Tests",
                pattern = "**/*smoke*.py",
                exclude = emptyList(),
                browsers = listOf("chromium"),
                tags = listOf("@smoke", "smoke"),
                parallel = 1,
                timeout = 30000,
                retries = 0
            ),
            TestSuiteConfig(
                name = "E2E Tests",
                pattern = "**/e2e/**/*.py",
                exclude = emptyList(),
                browsers = listOf("chromium", "firefox"),
                tags = listOf("@e2e", "e2e"),
                parallel = 2,
                timeout = 60000,
                retries = 1
            ),
            TestSuiteConfig(
                name = "Regression",
                pattern = "**/*.py",
                exclude = listOf("**/wip_*.py", "**/*_draft.py"),
                browsers = listOf("chromium", "firefox", "webkit"),
                tags = emptyList(),
                parallel = 4,
                timeout = 30000,
                retries = 2
            )
        )
    }

    /**
     * Save suite configs to file
     */
    fun saveConfigs(configs: List<TestSuiteConfig>) {
        val configFile = getConfigFile() ?: return

        try {
            val wrapper = ConfigWrapper(configs)
            val json = gson.toJson(wrapper)
            configFile.writeText(json)
            cachedConfigs = configs
            println("[SuiteConfig] Saved ${configs.size} suite configs to: ${configFile.absolutePath}")
        } catch (e: Exception) {
            println("[SuiteConfig] Error saving config: ${e.message}")
        }
    }

    /**
     * Create example config file
     */
    fun createExampleConfigFile() {
        val configFile = getConfigFile() ?: return

        if (configFile.exists()) {
            println("[SuiteConfig] Config file already exists at: ${configFile.absolutePath}")
            return
        }

        val exampleConfigs = listOf(
            TestSuiteConfig(
                name = "Smoke Tests",
                pattern = "tests/**/*smoke*.py",
                exclude = emptyList(),
                browsers = listOf("chromium"),
                tags = listOf("@smoke"),
                parallel = 1,
                timeout = 30000,
                retries = 0
            ),
            TestSuiteConfig(
                name = "E2E Tests",
                pattern = "tests/e2e/**/*.py",
                exclude = listOf("**/wip_*.py"),
                browsers = listOf("chromium", "firefox"),
                tags = listOf("@e2e"),
                parallel = 2,
                timeout = 60000,
                retries = 1
            ),
            TestSuiteConfig(
                name = "Regression",
                pattern = "tests/**/*.py",
                exclude = listOf("**/wip_*.py", "**/*_draft.py"),
                browsers = listOf("chromium", "firefox", "webkit"),
                tags = emptyList(),
                parallel = 4,
                timeout = 30000,
                retries = 2
            )
        )

        saveConfigs(exampleConfigs)
        println("[SuiteConfig] Created example config file at: ${configFile.absolutePath}")
    }

    /**
     * Filter tests based on suite config
     */
    fun filterTestsBySuite(suite: TestSuite, config: TestSuiteConfig): TestSuite {
        val filtered = TestSuite(
            name = config.name,
            path = suite.path
        )

        // Filter folders recursively
        suite.folders.forEach { folder ->
            val filteredFolder = filterFolder(folder, config)
            if (filteredFolder != null && filteredFolder.totalTests > 0) {
                filtered.folders.add(filteredFolder)
            }
        }

        filtered.updateStats()
        return filtered
    }

    /**
     * Filter folder by suite config
     */
    private fun filterFolder(folder: TestFolder, config: TestSuiteConfig): TestFolder? {
        val filtered = TestFolder(
            name = folder.name,
            path = folder.path
        )

        // Filter subfolders
        folder.subfolders.forEach { subfolder ->
            val filteredSubfolder = filterFolder(subfolder, config)
            if (filteredSubfolder != null && filteredSubfolder.totalTests > 0) {
                filtered.subfolders.add(filteredSubfolder)
            }
        }

        // Filter files
        folder.files.forEach { file ->
            val filteredFile = filterFile(file, config)
            if (filteredFile != null && filteredFile.totalTests > 0) {
                filtered.files.add(filteredFile)
            }
        }

        filtered.updateStats()
        return if (filtered.totalTests > 0) filtered else null
    }

    /**
     * Filter file by suite config
     */
    private fun filterFile(file: TestFile, config: TestSuiteConfig): TestFile? {
        // Check exclude patterns
        if (isExcluded(file.path, config.exclude)) {
            return null
        }

        // Check pattern match
        if (!matchesPattern(file.path, config.pattern)) {
            return null
        }

        val filtered = TestFile(
            name = file.name,
            path = file.path,
            framework = file.framework
        )

        // Filter test cases by tags
        file.testCases.forEach { testCase ->
            if (matchesTags(testCase, config.tags)) {
                filtered.testCases.add(testCase)
            }
        }

        filtered.updateStats()
        return if (filtered.testCases.isNotEmpty()) filtered else null
    }

    /**
     * Check if path is excluded
     */
    private fun isExcluded(path: String, excludePatterns: List<String>): Boolean {
        return excludePatterns.any { pattern ->
            matchesPattern(path, pattern)
        }
    }

    /**
     * Check if path matches pattern (simple glob-like matching)
     */
    private fun matchesPattern(path: String, pattern: String): Boolean {
        if (pattern == "**/*") return true

        val normalized = path.replace('\\', '/')
        val patternNormalized = pattern.replace('\\', '/')

        // Convert glob pattern to regex
        val regex = patternNormalized
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .toRegex()

        return regex.containsMatchIn(normalized)
    }

    /**
     * Check if test case matches tags
     */
    private fun matchesTags(testCase: TestCase, tags: List<String>): Boolean {
        if (tags.isEmpty()) return true

        return testCase.markers.any { marker ->
            tags.any { tag ->
                marker.contains(tag.removePrefix("@"), ignoreCase = true)
            }
        }
    }

    /**
     * Refresh cached configs
     */
    fun refresh() {
        cachedConfigs = null
    }
}

/**
 * Wrapper for JSON serialization
 */
private data class ConfigWrapper(
    val suites: List<TestSuiteConfig>
)
