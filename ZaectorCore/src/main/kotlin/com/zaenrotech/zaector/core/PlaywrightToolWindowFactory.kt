package com.zaenrotech.zaector.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

// ==========================================
// WINDOW 1: INSPECTOR (Default: Bottom)
// ==========================================
class PlaywrightInspectorFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = PlaywrightInspectorContent(project)
        val contentObj = ContentFactory.getInstance().createContent(content.panel, "", false)
        toolWindow.contentManager.addContent(contentObj)
        Disposer.register(contentObj, content)
    }

    private class PlaywrightInspectorContent(private val project: Project) : Disposable {
        val panel = JPanel(BorderLayout())

        // UI Components
        private val urlField = JTextField("https://google.com")
        private val scanButton = JButton("Scan", AllIcons.Actions.Refresh)
        private val headlessCheckbox = JCheckBox("Headless", false)
        private val keepOpenCheckbox = JCheckBox("Keep Open", true)

        private val rootNode = DefaultMutableTreeNode("Elements")
        private val treeModel = DefaultTreeModel(rootNode)
        private val elementTree = Tree(treeModel)

        private val codeArea = JBTextArea()
        private val propertyTable = JBTable()
        private val tableModel = DefaultTableModel(arrayOf("Property", "Value"), 0)

        // Element Info Labels
        private val domPathLabel = JBTextArea()
        private val dimensionsLabel = JLabel(" ")
        private val selectorScoreLabel = JLabel(" ")

        private val insertSkeletonButton = JButton("Init Test")
        private val actionTypeComboBox = JComboBox(arrayOf("click", "fill", "type", "press", "check", "uncheck", "select", "hover"))
        private val insertActionButton = JButton("Insert Action")
        private val insertAssertionButton = JButton("Insert Assertion")
        private val copySelectorButton = JButton("Copy Selector", AllIcons.Actions.Copy)
        private val findUsageButton = JButton("Find in Code", AllIcons.Actions.Find)
        private val highlightInBrowserButton = JButton("Highlight in Browser", AllIcons.Actions.Show)

        private var currentElement: ScannedElement? = null
        @Volatile private var activePythonProcess: Process? = null
        private val browserHighlighter = BrowserHighlighter()
        private var lastHighlightedSelector: String? = null
        private var highlightTimer: javax.swing.Timer? = null

        // Recorder components
        private val recorderManager = RecorderManager()
        private val startRecordingButton = JButton("Start Recording", AllIcons.Actions.Execute)
        private val stopRecordingButton = JButton("Stop", AllIcons.Actions.Suspend)
        private val clearRecordingButton = JButton("Clear", AllIcons.Actions.GC)
        private val generateCodeButton = JButton("Generate Code", AllIcons.FileTypes.Any_type)
        private val generatePageObjectsButton = JButton("Generate Page Objects", AllIcons.Nodes.Class)
        private val parameterizeCheckbox = javax.swing.JCheckBox("Generate Parameterized Functions", true)
        private val recordingStatusLabel = JLabel("Not recording")
        private val eventListModel = DefaultListModel<String>()
        private val eventList = JBList(eventListModel)

        init {
            elementTree.setCellRenderer(PlaywrightTreeCellRenderer())
            propertyTable.model = tableModel
            propertyTable.setShowGrid(true)

            // Configure DOM path label
            domPathLabel.isEditable = false
            domPathLabel.lineWrap = true
            domPathLabel.wrapStyleWord = true
            domPathLabel.background = propertyTable.background
            domPathLabel.font = propertyTable.font.deriveFont(10f)

            // Header (everything in one line for space efficiency)
            val headerPanel = JPanel(BorderLayout())

            // Left: URL (grows)
            headerPanel.add(urlField, BorderLayout.CENTER)

            // Right: Buttons & Settings (fixed width)
            val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
            controlsPanel.add(scanButton)
            controlsPanel.add(JSeparator(SwingConstants.VERTICAL))
            controlsPanel.add(headlessCheckbox)
            controlsPanel.add(keepOpenCheckbox)

            headerPanel.add(controlsPanel, BorderLayout.EAST)

            panel.add(headerPanel, BorderLayout.NORTH)

            // Splitter (Horizontal for Bottom View: Left Tree | Right Details)
            val mainSplitter = JBSplitter(false, 0.3f)

            // Left: Tree
            mainSplitter.firstComponent = JBScrollPane(elementTree)

            // Right: Details (Code + Table)
            val detailsPanel = JPanel(BorderLayout())

            // Element Info Panel (Top)
            val infoPanel = JPanel(BorderLayout())
            infoPanel.border = BorderFactory.createTitledBorder("Element Info")

            val infoContent = JPanel()
            infoContent.layout = BoxLayout(infoContent, BoxLayout.Y_AXIS)

            // DOM Path
            val pathPanel = JPanel(BorderLayout())
            pathPanel.add(JLabel("DOM Path: "), BorderLayout.WEST)
            pathPanel.add(JBScrollPane(domPathLabel), BorderLayout.CENTER)
            infoContent.add(pathPanel)

            // Dimensions
            val dimPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            dimPanel.add(JLabel(AllIcons.General.LayoutEditorPreview))
            dimPanel.add(dimensionsLabel)
            infoContent.add(dimPanel)

            // Selector Score
            val scorePanel = JPanel(FlowLayout(FlowLayout.LEFT))
            scorePanel.add(JLabel("Selector Quality: "))
            scorePanel.add(selectorScoreLabel)
            infoContent.add(scorePanel)

            // Browser status indicator
            val browserStatusLabel = JLabel()
            val testConnectionButton = JButton("Test Connection", AllIcons.Actions.Refresh)
            testConnectionButton.toolTipText = "Check if browser is accessible on port 9222"
            val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            statusPanel.add(JLabel("Browser: "))
            statusPanel.add(browserStatusLabel)
            statusPanel.add(testConnectionButton)
            infoContent.add(statusPanel)

            testConnectionButton.addActionListener {
                Thread {
                    val isConnected = browserHighlighter.isBrowserAvailable()
                    ApplicationManager.getApplication().invokeLater {
                        val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Zactor")

                        if (isConnected) {
                            notificationGroup.createNotification(
                                "Browser Connection",
                                "✓ Browser is connected on port 9222<br/>Hover highlighting is available!",
                                com.intellij.notification.NotificationType.INFORMATION
                            ).notify(project)
                        } else {
                            notificationGroup.createNotification(
                                "Browser Connection",
                                "✗ Browser is NOT connected on port 9222<br/><br/>" +
                                "To enable highlighting:<br/>" +
                                "1. Check 'Keep Open' checkbox<br/>" +
                                "2. Click 'Scan' button<br/>" +
                                "3. Browser will start with remote debugging enabled<br/>" +
                                "4. Status should change to 'Connected ✓'",
                                com.intellij.notification.NotificationType.WARNING
                            ).notify(project)
                        }
                    }
                }.start()
            }

            // Update browser status indicator periodically
            val statusTimer = javax.swing.Timer(1000) {
                val isConnected = browserHighlighter.isBrowserAvailable()
                if (isConnected) {
                    browserStatusLabel.text = "Connected ✓"
                    browserStatusLabel.icon = AllIcons.RunConfigurations.TestPassed
                    browserStatusLabel.foreground = java.awt.Color(0, 150, 0)
                } else {
                    browserStatusLabel.text = "Not connected (Scan with 'Keep Open')"
                    browserStatusLabel.icon = AllIcons.RunConfigurations.TestIgnored
                    browserStatusLabel.foreground = java.awt.Color(128, 128, 128)
                }
            }
            statusTimer.start()

            infoPanel.add(infoContent, BorderLayout.CENTER)
            detailsPanel.add(infoPanel, BorderLayout.NORTH)

            val detailSplitter = JBSplitter(false, 0.4f)

            // Code Preview
            val codePanel = JPanel(BorderLayout())
            codePanel.add(JLabel(" Action Preview:"), BorderLayout.NORTH)
            codeArea.isEditable = false
            codePanel.add(JBScrollPane(codeArea), BorderLayout.CENTER)
            detailSplitter.firstComponent = codePanel

            // Properties Table
            val tablePanel = JPanel(BorderLayout())
            tablePanel.add(JLabel(" Object Properties:"), BorderLayout.NORTH)
            tablePanel.add(JBScrollPane(propertyTable), BorderLayout.CENTER)
            detailSplitter.secondComponent = tablePanel

            detailsPanel.add(detailSplitter, BorderLayout.CENTER)

            // Buttons (Bottom, context-sensitive)
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
            buttonPanel.add(insertSkeletonButton)
            buttonPanel.add(JSeparator(SwingConstants.VERTICAL))
            buttonPanel.add(JLabel("Action:"))
            actionTypeComboBox.isEnabled = false
            buttonPanel.add(actionTypeComboBox)
            insertActionButton.isEnabled = false
            buttonPanel.add(insertActionButton)
            insertAssertionButton.isEnabled = false
            buttonPanel.add(insertAssertionButton)
            buttonPanel.add(JSeparator(SwingConstants.VERTICAL))
            copySelectorButton.isEnabled = false
            buttonPanel.add(copySelectorButton)
            findUsageButton.isEnabled = false
            buttonPanel.add(findUsageButton)
            highlightInBrowserButton.isEnabled = false
            buttonPanel.add(highlightInBrowserButton)
            detailsPanel.add(buttonPanel, BorderLayout.SOUTH)

            mainSplitter.secondComponent = detailsPanel

            // Recorder Panel (Bottom)
            val recorderPanel = JPanel(BorderLayout())
            recorderPanel.border = BorderFactory.createTitledBorder("Action Recorder")

            // Recorder controls
            val recorderControlPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            stopRecordingButton.isEnabled = false
            generateCodeButton.isEnabled = false
            generatePageObjectsButton.isEnabled = false
            recorderControlPanel.add(startRecordingButton)
            recorderControlPanel.add(stopRecordingButton)
            recorderControlPanel.add(clearRecordingButton)
            recorderControlPanel.add(generateCodeButton)
            recorderControlPanel.add(generatePageObjectsButton)
            recorderControlPanel.add(JSeparator(SwingConstants.VERTICAL))
            recorderControlPanel.add(parameterizeCheckbox)
            recorderControlPanel.add(JSeparator(SwingConstants.VERTICAL))
            recorderControlPanel.add(JLabel("Status:"))
            recorderControlPanel.add(recordingStatusLabel)

            recorderPanel.add(recorderControlPanel, BorderLayout.NORTH)

            // Event list
            eventList.setCellRenderer(object : ColoredListCellRenderer<String>() {
                override fun customizeCellRenderer(list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    if (value != null) {
                        append("${index + 1}. ", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        append(value)
                    }
                }
            })
            recorderPanel.add(JBScrollPane(eventList), BorderLayout.CENTER)

            // Main layout with TABS instead of splitter
            val tabbedPane = javax.swing.JTabbedPane()

            // Tab 1: Inspector
            val inspectorTabPanel = JPanel(BorderLayout())
            inspectorTabPanel.add(mainSplitter, BorderLayout.CENTER)
            tabbedPane.addTab("Inspector", AllIcons.Actions.Find, inspectorTabPanel, "Scan and inspect page elements")

            // Tab 2: Recorder
            val recorderTabPanel = JPanel(BorderLayout())
            recorderTabPanel.add(recorderPanel, BorderLayout.CENTER)
            tabbedPane.addTab("Recorder", AllIcons.Actions.Execute, recorderTabPanel, "Record browser actions and generate code")

            panel.add(tabbedPane, BorderLayout.CENTER)

            setupEvents()
        }

        private fun setupEvents() {
            scanButton.addActionListener { performScan() }
            insertSkeletonButton.addActionListener { insertCodeIntoEditor(getSkeletonCode()) }
            insertActionButton.addActionListener { insertCodeIntoEditor(getActionCode()) }
            insertAssertionButton.addActionListener { insertCodeIntoEditor(getAssertionCodeFromTable()) }
            copySelectorButton.addActionListener { copyCurrentSelectorToClipboard() }
            findUsageButton.addActionListener { findSelectorInCode() }
            highlightInBrowserButton.addActionListener { highlightCurrentElementInBrowser() }

            // Recorder event handlers
            startRecordingButton.addActionListener { startRecording() }
            stopRecordingButton.addActionListener { stopRecording() }
            clearRecordingButton.addActionListener { clearRecording() }
            generateCodeButton.addActionListener { generateRecordedCode() }
            generatePageObjectsButton.addActionListener { generatePageObjectCode() }

            elementTree.addTreeSelectionListener {
                val node = elementTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                val element = node?.userObject as? ScannedElement
                currentElement = element
                if (element != null) {
                    updateDetails(element)
                    actionTypeComboBox.isEnabled = true
                    insertActionButton.isEnabled = true
                    copySelectorButton.isEnabled = true
                    findUsageButton.isEnabled = true
                    highlightInBrowserButton.isEnabled = browserHighlighter.isBrowserAvailable()
                } else {
                    actionTypeComboBox.isEnabled = false
                    insertActionButton.isEnabled = false
                    insertAssertionButton.isEnabled = false
                    copySelectorButton.isEnabled = false
                    findUsageButton.isEnabled = false
                    highlightInBrowserButton.isEnabled = false
                }
            }
            propertyTable.selectionModel.addListSelectionListener {
                insertAssertionButton.isEnabled = propertyTable.selectedRow != -1
            }
        }

        // Recorder functions
        private fun startRecording() {
            if (!browserHighlighter.isBrowserAvailable()) {
                val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Zactor")
                notificationGroup.createNotification(
                    "Browser Not Connected",
                    "Please scan with 'Keep Open' enabled before recording.",
                    com.intellij.notification.NotificationType.WARNING
                ).notify(project)
                return
            }

            println("[Inspector] Starting recording session...")
            eventListModel.clear()
            recordingStatusLabel.text = "Recording..."
            recordingStatusLabel.foreground = java.awt.Color.RED
            startRecordingButton.isEnabled = false
            stopRecordingButton.isEnabled = true
            clearRecordingButton.isEnabled = false
            generateCodeButton.isEnabled = false
            generatePageObjectsButton.isEnabled = false

            recorderManager.startRecording { action ->
                // Called when new action is recorded
                ApplicationManager.getApplication().invokeLater {
                    eventListModel.addElement(action.toString())
                    eventList.ensureIndexIsVisible(eventListModel.size() - 1)
                }
            }
        }

        private fun stopRecording() {
            println("[Inspector] Stopping recording session...")
            recorderManager.stopRecording()
            recordingStatusLabel.text = "Stopped (${recorderManager.getActions().size} actions)"
            recordingStatusLabel.foreground = java.awt.Color.DARK_GRAY
            startRecordingButton.isEnabled = true
            stopRecordingButton.isEnabled = false
            clearRecordingButton.isEnabled = true
            generateCodeButton.isEnabled = recorderManager.getActions().isNotEmpty()
            generatePageObjectsButton.isEnabled = recorderManager.getActions().isNotEmpty()
        }

        private fun clearRecording() {
            println("[Inspector] Clearing recorded actions...")
            recorderManager.clearActions()
            eventListModel.clear()
            recordingStatusLabel.text = "Not recording"
            recordingStatusLabel.foreground = java.awt.Color.BLACK
            generateCodeButton.isEnabled = false
            generatePageObjectsButton.isEnabled = false
        }

        private fun generateRecordedCode() {
            val actions = recorderManager.getActions()
            if (actions.isEmpty()) {
                val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Zactor")
                notificationGroup.createNotification(
                    "No Actions Recorded",
                    "Please record some actions first.",
                    com.intellij.notification.NotificationType.WARNING
                ).notify(project)
                return
            }

            println("[Inspector] Generating code from ${actions.size} recorded actions...")

            // Get settings
            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)

            // Generate code (parameterized or regular based on checkbox)
            val code = if (parameterizeCheckbox.isSelected) {
                println("[Inspector] Generating PARAMETERIZED code (checkbox is selected)")
                recorderManager.generateParameterizedCode(template, formatting, urlField.text)
            } else {
                println("[Inspector] Generating REGULAR code (checkbox is NOT selected)")
                recorderManager.generateCode(template, formatting, urlField.text)
            }

            // Insert into editor
            insertCodeIntoEditor(code)

            // Show notification
            val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Zactor")
            notificationGroup.createNotification(
                "Code Generated",
                "Generated test code from ${actions.size} recorded actions.",
                com.intellij.notification.NotificationType.INFORMATION
            ).notify(project)
        }

        private fun generatePageObjectCode() {
            val actions = recorderManager.getActions()
            if (actions.isEmpty()) {
                val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Zactor")
                notificationGroup.createNotification(
                    "No Actions Recorded",
                    "Please record some actions first.",
                    com.intellij.notification.NotificationType.WARNING
                ).notify(project)
                return
            }

            println("[Inspector] Generating Page Object Model from ${actions.size} recorded actions...")

            // Get settings
            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)

            // Generate Page Object code
            val code = recorderManager.generatePageObjectCode(template, formatting, urlField.text)

            // Insert into editor
            insertCodeIntoEditor(code)

            // Show notification
            val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Zactor")
            notificationGroup.createNotification(
                "Page Objects Generated",
                "Generated Page Object Model with test from ${actions.size} recorded actions.",
                com.intellij.notification.NotificationType.INFORMATION
            ).notify(project)
        }

        override fun dispose() {
            activePythonProcess?.let { if (it.isAlive) it.destroyForcibly() }
            highlightTimer?.stop()
            browserHighlighter.dispose()
            recorderManager.dispose()
        }

        // Helper methods
        private fun insertCodeIntoEditor(codeToInsert: String?) {
            if (codeToInsert == null) return
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val caretOffset = editor.caretModel.offset
                val lineNumber = document.getLineNumber(caretOffset)
                val lineStartOffset = document.getLineStartOffset(lineNumber)
                val lineEndOffset = document.getLineEndOffset(lineNumber)
                val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
                val indentation = lineText.takeWhile { it.isWhitespace() && it != '\n' }
                val textToInsert = if (lineText.isBlank()) codeToInsert else "\n$indentation$codeToInsert"
                document.insertString(caretOffset, textToInsert)
                editor.caretModel.moveToOffset(caretOffset + textToInsert.length)
            }
        }

        private fun getSkeletonCode(): String {
            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)
            return template.generateSkeleton(urlField.text, formatting)
        }

        private fun getActionCode(): String {
            val el = currentElement ?: return ""
            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)
            val actionType = actionTypeComboBox.selectedItem as? String ?: "click"
            return template.generateAction(el.selector ?: "", actionType, formatting)
        }

        private fun copyCurrentSelectorToClipboard() {
            val el = currentElement ?: return
            val selector = el.selector ?: return
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(selector)
            clipboard.setContents(stringSelection, null)
        }

        private fun highlightCurrentElementInBrowser() {
            val el = currentElement ?: return
            val selector = el.selector ?: return

            if (!browserHighlighter.isBrowserAvailable()) {
                val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Zactor")
                notificationGroup.createNotification(
                    "Browser Not Connected",
                    "Please scan with 'Keep Open' enabled to use highlighting.",
                    com.intellij.notification.NotificationType.WARNING
                ).notify(project)
                return
            }

            Thread {
                browserHighlighter.highlightElement(selector, urlField.text)
                Thread.sleep(100) // Small delay to ensure highlight is visible

                ApplicationManager.getApplication().invokeLater {
                    val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Zactor")
                    notificationGroup.createNotification(
                        "Element Highlighted",
                        "Element '$selector' is highlighted in the browser.",
                        com.intellij.notification.NotificationType.INFORMATION
                    ).notify(project)
                }
            }.start()
        }

        private fun findSelectorInCode() {
            val el = currentElement ?: return
            val selector = el.selector ?: return

            // Copy to clipboard and show notification
            ApplicationManager.getApplication().invokeLater {
                // Copy selector to clipboard
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val stringSelection = java.awt.datatransfer.StringSelection(selector)
                clipboard.setContents(stringSelection, null)

                // Show notification with instructions
                val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Zactor")

                notificationGroup.createNotification(
                    "Find Selector in Code",
                    "Selector '$selector' copied to clipboard.<br/>Use Ctrl+Shift+F (Find in Files) and paste to search.",
                    com.intellij.notification.NotificationType.INFORMATION
                ).notify(project)
            }
        }

        private fun getAssertionCodeFromTable(): String {
            val el = currentElement ?: return ""
            val row = propertyTable.selectedRow
            if (row == -1) return ""
            val propName = tableModel.getValueAt(row, 0) as String
            val propValue = tableModel.getValueAt(row, 1) as String

            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)

            // Handle special cases
            if (propName.startsWith("style.")) {
                return template.generateAssertion(el.selector ?: "", "css", propValue, formatting)
                    .replace("property", propName.removePrefix("style."))
            }
            if (propName.startsWith("geometry.")) return "# Verify $propName: $propValue"

            val assertionType = when (propName) {
                "innerText", "text" -> "text"
                "value" -> "value"
                "visible" -> "visible"
                "checked" -> "checked"
                "disabled" -> "disabled"
                "class" -> "class"
                else -> "attribute"
            }

            return template.generateAssertion(el.selector ?: "", assertionType, propValue, formatting)
        }

        private fun updateDetails(el: ScannedElement) {
            val settings = PlaywrightSettings.instance
            val framework = try {
                TemplateFramework.valueOf(settings.selectedFramework)
            } catch (e: Exception) {
                TemplateFramework.PYTHON_SYNC
            }
            val formatting = CodeFormatting(
                indentSize = settings.indentSize,
                useTabs = settings.useTabs,
                quoteStyle = if (settings.quoteStyle == QuoteStyle.SINGLE.name) QuoteStyle.SINGLE else QuoteStyle.DOUBLE
            )
            val template = CodeTemplateFactory.getTemplate(framework)

            val code = StringBuilder()
            val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
            code.append("# Selector:\n${el.selector}\n\n")
            code.append("# Example Actions:\n")
            code.append(template.generateAction(el.selector ?: "", "click", formatting))
            codeArea.text = code.toString()

            // Update DOM Path
            val domPath = el.attributes?.get("domPath") ?: "N/A"
            domPathLabel.text = domPath

            // Update Dimensions
            val width = el.attributes?.get("geometry.width") ?: "0"
            val height = el.attributes?.get("geometry.height") ?: "0"
            val x = el.attributes?.get("geometry.x") ?: "0"
            val y = el.attributes?.get("geometry.y") ?: "0"
            val visible = el.attributes?.get("visible") ?: "true"

            dimensionsLabel.text = "Size: ${width}x${height}px | Position: ($x, $y) | Visible: $visible"

            // Update Selector Score
            val score = el.attributes?.get("selectorScore")?.toIntOrNull() ?: 0
            val rating = el.attributes?.get("selectorRating") ?: "Unknown"

            selectorScoreLabel.text = "$score/10 - $rating"
            selectorScoreLabel.icon = when {
                score >= 8 -> AllIcons.RunConfigurations.TestPassed
                score >= 5 -> AllIcons.RunConfigurations.TestNotRan
                else -> AllIcons.RunConfigurations.TestFailed
            }

            // Color code the score
            selectorScoreLabel.foreground = when {
                score >= 8 -> java.awt.Color(0, 150, 0)  // Green
                score >= 5 -> java.awt.Color(200, 100, 0)  // Orange
                else -> java.awt.Color(200, 0, 0)  // Red
            }

            tableModel.rowCount = 0
            if (el.text != null && el.text.isNotBlank()) tableModel.addRow(arrayOf("innerText", el.text))
            el.attributes?.toSortedMap()?.forEach { (k, v) ->
                // Skip already displayed info
                if (k != "innerText" && k != "domPath" && k != "selectorScore" && k != "selectorRating" && !k.startsWith("geometry.")) {
                    tableModel.addRow(arrayOf(k, v))
                }
            }

            // Show warning for weak selectors
            if (score < 5) {
                val warningText = "⚠ Warning: This selector has low stability (score: $score/10). Consider adding data-testid attribute."
                tableModel.insertRow(0, arrayOf("⚠ SELECTOR WARNING", warningText))
            }
        }

        private fun performScan() {
            scanButton.isEnabled = false
            rootNode.removeAllChildren()
            treeModel.reload()
            rootNode.userObject = "Scanning..."
            treeModel.nodeChanged(rootNode)
            runPythonScanner { jsonResult ->
                ApplicationManager.getApplication().invokeLater {
                    scanButton.isEnabled = true
                    if (jsonResult != null) populateTree(jsonResult) else {
                        rootNode.userObject = "Scan Failed"
                        treeModel.nodeChanged(rootNode)
                    }
                }
            }
        }

        private fun populateTree(json: String) {
            try {
                val gson = Gson()
                val listType = object : TypeToken<List<ScannedElement>>() {}.type
                val elements: List<ScannedElement> = gson.fromJson(json, listType)
                rootNode.userObject = "Found ${elements.size} Elements"
                val groups = elements.groupBy { it.tagName }
                for ((tag, list) in groups) {
                    val tagNode = DefaultMutableTreeNode(tag)
                    rootNode.add(tagNode)
                    for (el in list) tagNode.add(DefaultMutableTreeNode(el))
                }
                treeModel.reload()
                elementTree.expandRow(0)
            } catch (e: Exception) { codeArea.text = "Error: ${e.message}" }
        }

        private fun runPythonScanner(onSuccess: (String?) -> Unit) {
            Thread {
                try {
                    val jsFile = copyResourceToTemp("/scanner/dom_scanner.js", "scanner.js")
                    val pyFile = copyResourceToTemp("/python/runner.py", "runner.py")
                    if (jsFile == null || pyFile == null) return@Thread

                    val commandLine = GeneralCommandLine(
                        "python", pyFile.absolutePath, jsFile.absolutePath, urlField.text,
                        headlessCheckbox.isSelected.toString(), keepOpenCheckbox.isSelected.toString()
                    )
                    commandLine.charset = StandardCharsets.UTF_8
                    val process = commandLine.createProcess()
                    activePythonProcess = process
                    val reader = process.inputStream.bufferedReader(StandardCharsets.UTF_8)
                    val outputBuilder = StringBuilder()
                    var line: String?
                    var jsonFound = false
                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.append(line)
                        if (line!!.trim().endsWith("]")) { jsonFound = true; break }
                    }
                    onSuccess(if (jsonFound) outputBuilder.toString() else null)
                } catch (e: Exception) { e.printStackTrace(); onSuccess(null) }
            }.start()
        }

        private fun copyResourceToTemp(resourcePath: String, fileName: String): File? {
            val input = this::class.java.getResourceAsStream(resourcePath) ?: return null
            val tempFile = File.createTempFile("playwright_plugin_", "_$fileName")
            tempFile.deleteOnExit()
            tempFile.outputStream().use { input.copyTo(it) }
            return tempFile
        }
    }
}

// ==========================================
// WINDOW 2: LIBRARY (Default: Right)
// ==========================================
class PlaywrightLibraryFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = PlaywrightLibraryContent(project)
        val contentObj = ContentFactory.getInstance().createContent(content.panel, "", false)
        toolWindow.contentManager.addContent(contentObj)

        // Set narrow default width (150px = 50% of typical 300px)
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val component = toolWindow.component
            component.preferredSize = java.awt.Dimension(150, component.height)
            component.minimumSize = java.awt.Dimension(100, component.minimumSize.height)
            component.revalidate()
        }
    }

    private class PlaywrightLibraryContent(private val project: Project) {
        val panel = JPanel(BorderLayout())
        private val frameworkListModel = DefaultListModel<File>()
        private val frameworkList = JBList(frameworkListModel)
        private val selectFolderButton = JButton("Select Folder", AllIcons.Nodes.Folder)
        private val refreshFilesButton = JButton(AllIcons.Actions.Refresh)
        private val currentPathLabel = JLabel("No folder selected")
        private val prefFrameworkPath = "com.zaenrotech.playwright.frameworkPath"

        init {
            val toolbar = JToolBar()
            toolbar.isFloatable = false
            toolbar.add(selectFolderButton)
            toolbar.add(refreshFilesButton)
            panel.add(toolbar, BorderLayout.NORTH)
            panel.add(currentPathLabel, BorderLayout.SOUTH)

            frameworkList.cellRenderer = object : ColoredListCellRenderer<File>() {
                override fun customizeCellRenderer(list: JList<out File>, value: File?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    if (value == null) return
                    append(value.name)
                    icon = AllIcons.FileTypes.Text
                }
            }
            panel.add(JBScrollPane(frameworkList), BorderLayout.CENTER)

            selectFolderButton.addActionListener {
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                val virtualFile = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null).choose(project).firstOrNull()
                if (virtualFile != null) {
                    saveFrameworkPath(virtualFile.path)
                }
            }

            refreshFilesButton.addActionListener { loadFrameworkPath() }

            frameworkList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val file = frameworkList.selectedValue ?: return
                        val vFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return
                        FileEditorManager.getInstance(project).openFile(vFile, true)
                    }
                }
            })

            loadFrameworkPath()
        }

        private fun saveFrameworkPath(path: String) {
            PropertiesComponent.getInstance().setValue(prefFrameworkPath, path)
            loadFiles(File(path))
        }

        private fun loadFrameworkPath() {
            val path = PropertiesComponent.getInstance().getValue(prefFrameworkPath)
            if (path != null) loadFiles(File(path))
        }

        private fun loadFiles(folder: File) {
            currentPathLabel.text = "Path: ${folder.absolutePath}"
            frameworkListModel.clear()
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles { _, name -> name.endsWith(".py") }?.sortedBy { it.name }?.forEach { frameworkListModel.addElement(it) }
            } else {
                currentPathLabel.text = "Invalid path"
            }
        }
    }
}

// Renderer class
class PlaywrightTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: javax.swing.JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        val node = value as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject
        if (userObject is ScannedElement) {
            append(userObject.toString())

            // Add selector score badge
            val score = userObject.attributes?.get("selectorScore")?.toIntOrNull() ?: 0
            val scoreColor = when {
                score >= 8 -> java.awt.Color(0, 150, 0)
                score >= 5 -> java.awt.Color(200, 100, 0)
                else -> java.awt.Color(200, 0, 0)
            }
            append(" [$score]", com.intellij.ui.SimpleTextAttributes(com.intellij.ui.SimpleTextAttributes.STYLE_BOLD, scoreColor))

            icon = when (userObject.tagName.lowercase()) {
                "button" -> AllIcons.Actions.Execute
                "input", "textarea" -> AllIcons.Nodes.Variable
                "a" -> AllIcons.Ide.Link
                "img", "svg" -> AllIcons.FileTypes.Image
                "div", "span" -> AllIcons.Nodes.Tag
                else -> AllIcons.General.Web
            }
        } else if (userObject is String) {
            append(userObject)
            icon = AllIcons.Nodes.Folder
        }
    }
}