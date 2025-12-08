package com.zaenrotech.playwright.playwrightpycharm

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

// 1. STORAGE
@State(
    name = "PlaywrightSettings",
    storages = [Storage("playwright_plugin_settings.xml")]
)
class PlaywrightSettings : PersistentStateComponent<PlaywrightSettings> {
    // Default: Collapsed (false), but icon visible
    var openInspectorOnStart: Boolean = false
    var openLibraryOnStart: Boolean = false

    // Code generation settings
    var selectedFramework: String = TemplateFramework.PYTHON_SYNC.name
    var indentSize: Int = 4
    var useTabs: Boolean = false
    var quoteStyle: String = QuoteStyle.DOUBLE.name

    // Test suite configuration
    var suiteConfigPath: String = ""  // Empty = use project root default

    // Browser & execution settings
    var defaultBrowser: String = "chromium"  // chromium, firefox, webkit
    var defaultViewportWidth: Int = 1280
    var defaultViewportHeight: Int = 720
    var headlessMode: Boolean = false

    override fun getState(): PlaywrightSettings = this
    override fun loadState(state: PlaywrightSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        val instance: PlaywrightSettings
            get() = service()
    }
}

class PlaywrightConfigurable : Configurable {
    private val inspectorCheckbox = JBCheckBox("Expand 'ZÆCTOR Inspector' automatically")
    private val libraryCheckbox = JBCheckBox("Expand 'ZÆCTOR Library' automatically")

    // Code generation settings
    private val frameworkComboBox = JComboBox(TemplateFramework.values().map { it.displayName }.toTypedArray())
    private val indentSizeSpinner = JSpinner(SpinnerNumberModel(4, 1, 8, 1))
    private val useTabsCheckbox = JBCheckBox("Use tabs instead of spaces")
    private val quoteStyleComboBox = JComboBox(arrayOf("Double quotes", "Single quotes"))

    // Test suite configuration
    private val suiteConfigPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Suite Configuration Directory",
            "Choose directory for .playwright-suites.json file",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    // Browser & execution settings
    private val browserComboBox = JComboBox(arrayOf("chromium", "firefox", "webkit"))
    private val viewportWidthSpinner = JSpinner(SpinnerNumberModel(1280, 800, 3840, 10))
    private val viewportHeightSpinner = JSpinner(SpinnerNumberModel(720, 600, 2160, 10))
    private val headlessModeCheckbox = JBCheckBox("Run browser in headless mode")

    override fun getDisplayName(): String = "ZÆCTOR"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        var row = 0

        // Tool Window Settings Section
        gbc.gridx = 0
        gbc.gridy = row++
        gbc.gridwidth = 2
        val toolWindowLabel = JBLabel("<html><b>Tool Window Settings</b></html>")
        panel.add(toolWindowLabel, gbc)

        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.gridy = row++
        gbc.gridwidth = 2
        panel.add(inspectorCheckbox, gbc)

        gbc.gridy = row++
        panel.add(libraryCheckbox, gbc)

        // Spacer
        gbc.gridy = row++
        gbc.insets = Insets(15, 5, 5, 5)
        panel.add(JSeparator(), gbc)

        // Code Generation Settings Section
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = row++
        val codeGenLabel = JBLabel("<html><b>Code Generation Settings</b></html>")
        panel.add(codeGenLabel, gbc)

        // Framework selection
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.gridy = row
        panel.add(JBLabel("Framework:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(frameworkComboBox, gbc)

        // Indent size
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.weightx = 0.0
        panel.add(JBLabel("Indent size:"), gbc)

        gbc.gridx = 1
        panel.add(indentSizeSpinner, gbc)

        // Use tabs
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        panel.add(useTabsCheckbox, gbc)

        // Quote style
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.gridy = ++row
        panel.add(JBLabel("Quote style:"), gbc)

        gbc.gridx = 1
        panel.add(quoteStyleComboBox, gbc)

        // Spacer
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        gbc.insets = Insets(15, 5, 5, 5)
        panel.add(JSeparator(), gbc)

        // Test Suite Settings Section
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = ++row
        val testSuiteLabel = JBLabel("<html><b>Test Suite Settings</b></html>")
        panel.add(testSuiteLabel, gbc)

        // Suite config path
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.gridy = ++row
        panel.add(JBLabel("Config directory:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(suiteConfigPathField, gbc)

        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        val helpLabel = JBLabel("<html><i>Leave empty to use project root directory</i></html>")
        panel.add(helpLabel, gbc)

        // Spacer
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        gbc.insets = Insets(15, 5, 5, 5)
        panel.add(JSeparator(), gbc)

        // Browser & Execution Settings Section
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = ++row
        val browserLabel = JBLabel("<html><b>Browser & Execution Settings</b></html>")
        panel.add(browserLabel, gbc)

        // Default browser
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.gridy = ++row
        panel.add(JBLabel("Default browser:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(browserComboBox, gbc)

        // Viewport width
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.weightx = 0.0
        panel.add(JBLabel("Viewport width:"), gbc)

        gbc.gridx = 1
        panel.add(viewportWidthSpinner, gbc)

        // Viewport height
        gbc.gridx = 0
        gbc.gridy = ++row
        panel.add(JBLabel("Viewport height:"), gbc)

        gbc.gridx = 1
        panel.add(viewportHeightSpinner, gbc)

        // Headless mode
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        panel.add(headlessModeCheckbox, gbc)

        // Filler to push everything to top
        gbc.gridx = 0
        gbc.gridy = ++row
        gbc.gridwidth = 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = PlaywrightSettings.instance
        val selectedFrameworkName = TemplateFramework.values()[frameworkComboBox.selectedIndex].name
        val selectedQuoteStyle = if (quoteStyleComboBox.selectedIndex == 0) QuoteStyle.DOUBLE.name else QuoteStyle.SINGLE.name

        return inspectorCheckbox.isSelected != settings.openInspectorOnStart ||
                libraryCheckbox.isSelected != settings.openLibraryOnStart ||
                selectedFrameworkName != settings.selectedFramework ||
                (indentSizeSpinner.value as Int) != settings.indentSize ||
                useTabsCheckbox.isSelected != settings.useTabs ||
                selectedQuoteStyle != settings.quoteStyle ||
                suiteConfigPathField.text != settings.suiteConfigPath ||
                browserComboBox.selectedItem != settings.defaultBrowser ||
                (viewportWidthSpinner.value as Int) != settings.defaultViewportWidth ||
                (viewportHeightSpinner.value as Int) != settings.defaultViewportHeight ||
                headlessModeCheckbox.isSelected != settings.headlessMode
    }

    override fun apply() {
        val settings = PlaywrightSettings.instance
        settings.openInspectorOnStart = inspectorCheckbox.isSelected
        settings.openLibraryOnStart = libraryCheckbox.isSelected

        // Save code generation settings
        settings.selectedFramework = TemplateFramework.values()[frameworkComboBox.selectedIndex].name
        settings.indentSize = indentSizeSpinner.value as Int
        settings.useTabs = useTabsCheckbox.isSelected
        settings.quoteStyle = if (quoteStyleComboBox.selectedIndex == 0) QuoteStyle.DOUBLE.name else QuoteStyle.SINGLE.name

        // Save test suite settings
        settings.suiteConfigPath = suiteConfigPathField.text

        // Save browser & execution settings
        settings.defaultBrowser = browserComboBox.selectedItem as String
        settings.defaultViewportWidth = viewportWidthSpinner.value as Int
        settings.defaultViewportHeight = viewportHeightSpinner.value as Int
        settings.headlessMode = headlessModeCheckbox.isSelected

        val openProjects = ProjectManager.getInstance().openProjects
        for (project in openProjects) {
            val toolWindowManager = ToolWindowManager.getInstance(project)

            val inspector = toolWindowManager.getToolWindow("ZÆCTOR Inspector")
            if (inspector != null) {
                inspector.isShowStripeButton = true
                if (settings.openInspectorOnStart) {
                    inspector.activate(null)
                } else {
                    inspector.hide(null)
                }
            }

            val library = toolWindowManager.getToolWindow("ZÆCTOR Library")
            if (library != null) {
                library.isShowStripeButton = true
                if (settings.openLibraryOnStart) {
                    library.activate(null)
                } else {
                    library.hide(null)
                }
            }
        }
    }

    override fun reset() {
        val settings = PlaywrightSettings.instance
        inspectorCheckbox.isSelected = settings.openInspectorOnStart
        libraryCheckbox.isSelected = settings.openLibraryOnStart

        // Reset code generation settings
        val frameworkIndex = TemplateFramework.values().indexOfFirst { it.name == settings.selectedFramework }
        frameworkComboBox.selectedIndex = if (frameworkIndex >= 0) frameworkIndex else 0
        indentSizeSpinner.value = settings.indentSize
        useTabsCheckbox.isSelected = settings.useTabs
        quoteStyleComboBox.selectedIndex = if (settings.quoteStyle == QuoteStyle.DOUBLE.name) 0 else 1

        // Reset test suite settings
        suiteConfigPathField.text = settings.suiteConfigPath

        // Reset browser & execution settings
        browserComboBox.selectedItem = settings.defaultBrowser
        viewportWidthSpinner.value = settings.defaultViewportWidth
        viewportHeightSpinner.value = settings.defaultViewportHeight
        headlessModeCheckbox.isSelected = settings.headlessMode
    }
}

// 3. STARTUP TRIGGER
class PlaywrightStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val settings = PlaywrightSettings.instance
        val toolWindowManager = ToolWindowManager.getInstance(project)

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {

            val inspector = toolWindowManager.getToolWindow("ZÆCTOR Inspector")
            if (inspector != null) {
                inspector.isShowStripeButton = true
                if (settings.openInspectorOnStart) {
                    inspector.activate(null)
                } else {
                    inspector.hide(null)
                }
            }

            val library = toolWindowManager.getToolWindow("ZÆCTOR Library")
            if (library != null) {
                library.isShowStripeButton = true
                if (settings.openLibraryOnStart) {
                    library.activate(null)
                } else {
                    library.hide(null)
                }
            }

            val testExplorer = toolWindowManager.getToolWindow("ZÆCTOR Tests")
            if (testExplorer != null) {
                testExplorer.isShowStripeButton = true
                testExplorer.activate(null)
            }
        }
    }
}