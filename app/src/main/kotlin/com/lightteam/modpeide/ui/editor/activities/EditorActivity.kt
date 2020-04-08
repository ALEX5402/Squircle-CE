/*
 * Licensed to the Light Team Software (Light Team) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The Light Team licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightteam.modpeide.ui.editor.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.ColorPalette
import com.afollestad.materialdialogs.color.colorChooser
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.play.core.install.model.ActivityResult
import com.jakewharton.rxbinding3.widget.afterTextChangeEvents
import com.lightteam.modpeide.R
import com.lightteam.modpeide.databinding.ActivityEditorBinding
import com.lightteam.modpeide.domain.model.editor.DocumentModel
import com.lightteam.modpeide.ui.base.activities.BaseActivity
import com.lightteam.modpeide.ui.base.dialogs.DialogStore
import com.lightteam.modpeide.ui.base.utils.OnBackPressedHandler
import com.lightteam.modpeide.ui.editor.utils.ToolbarManager
import com.lightteam.modpeide.ui.editor.viewmodel.EditorViewModel
import com.lightteam.modpeide.ui.explorer.fragments.ExplorerFragment
import com.lightteam.modpeide.ui.settings.activities.SettingsActivity
import com.lightteam.modpeide.utils.commons.TypefaceFactory
import com.lightteam.modpeide.utils.event.PreferenceEvent
import com.lightteam.modpeide.utils.extensions.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import java.io.File
import javax.inject.Inject

class EditorActivity : BaseActivity(), DrawerLayout.DrawerListener,
    ToolbarManager.OnPanelClickListener, TabLayout.OnTabSelectedListener {

    companion object {
        private const val REQUEST_CODE_UPDATE = 10
    }

    @Inject
    lateinit var viewModel: EditorViewModel
    @Inject
    lateinit var toolbarManager: ToolbarManager

    private lateinit var binding: ActivityEditorBinding
    private lateinit var backPressedHandler: OnBackPressedHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_editor)
        binding.viewModel = viewModel
        observeViewModel()

        toolbarManager.bind(binding)
        onConfigurationChanged(resources.configuration)

        binding.drawerLayout.addDrawerListener(this)
        binding.tabDocumentLayout.addOnTabSelectedListener(this)
        binding.extendedKeyboard.setKeyListener(binding.editor)

        binding.extendedKeyboard.setHasFixedSize(true)
        binding.scroller.link(binding.editor)

        binding.editor
            .afterTextChangeEvents()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy {
                viewModel.canUndo.set(binding.editor.canUndo())
                viewModel.canRedo.set(binding.editor.canRedo())
            }
            .disposeOnActivityDestroy()

        binding.fragmentExplorer.post {
            backPressedHandler = binding.fragmentExplorer.fragment<ExplorerFragment>()
        }

        viewModel.checkUpdate()

        /* FIXME this feature just doesn't work, idk how to fix it
        // Check if user opened a file from external file explorer
        if (intent.action == Intent.ACTION_VIEW) {
            // path must be started with /storage/emulated/0/...
            val file = File(intent.data?.path)
            val documentModel = DocumentConverter.toModel(file)
            viewModel.openFile(documentModel)
        }*/
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_UPDATE) {
            when (resultCode) {
                Activity.RESULT_OK -> { /* approved */ }
                Activity.RESULT_CANCELED -> { /* rejected */ }
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    viewModel.toastEvent.value = R.string.message_in_app_update_failed
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveDocument(binding.tabDocumentLayout.selectedTabPosition)
    }

    override fun onResume() {
        super.onResume()
        loadDocument(binding.tabDocumentLayout.selectedTabPosition)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        toolbarManager.orientation = newConfig.orientation
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (!backPressedHandler.handleOnBackPressed()) {
                closeDrawers()
            }
        } else {
            if (viewModel.backEvent.value!!) {
                MaterialDialog(this).show {
                    title(R.string.dialog_title_exit)
                    message(R.string.dialog_message_exit)
                    negativeButton(R.string.action_no)
                    positiveButton(R.string.action_yes) {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    // region DRAWER

    override fun onDrawerStateChanged(newState: Int) {}
    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
    override fun onDrawerClosed(drawerView: View) {}
    override fun onDrawerOpened(drawerView: View) {
        closeKeyboard()
    }

    private fun closeDrawers() {
        binding.drawerLayout.closeDrawers()
    }

    // endregion DRAWER

    // region TABS

    override fun onTabReselected(tab: TabLayout.Tab?) {}
    override fun onTabUnselected(tab: TabLayout.Tab) {
        saveDocument(tab.position)
        closeKeyboard() // Обход бага, когда после переключения вкладок редактирование не работало
        // (позиция курсора не менялась с предыдущей вкладки)
    }
    override fun onTabSelected(tab: TabLayout.Tab) {
        loadDocument(tab.position)
    }

    private fun addTab(documentModel: DocumentModel, selection: Boolean) {
        binding.tabDocumentLayout.newTab(documentModel.name, R.layout.item_tab_document) { tab ->
            val closeIcon = tab.customView?.findViewById<View>(R.id.item_icon)
            closeIcon?.setOnClickListener {
                removeTab(tab.position)
            }
            tab.view.setOnLongClickListener {
                val wrapper = ContextThemeWrapper(it.context, R.style.Widget_Darcula_PopupMenu)
                val popupMenu = PopupMenu(wrapper, it)
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_close -> removeTab(tab.position)
                        R.id.action_close_others -> removeOtherTabs(tab.position)
                        R.id.action_close_all -> removeAllTabs()
                    }
                    return@setOnMenuItemClickListener true
                }
                popupMenu.inflate(R.menu.menu_document)
                popupMenu.makeRightPaddingRecursively()
                popupMenu.show()
                return@setOnLongClickListener true
            }
            if (selection) {
                tab.select()
            }
        }
    }

    private fun removeAllTabs() {
        for (index in binding.tabDocumentLayout.tabCount - 1 downTo 0) {
            removeTab(index)
        }
    }

    private fun removeOtherTabs(position: Int) {
        for (index in binding.tabDocumentLayout.tabCount - 1 downTo 0) {
            if (index != position) {
                removeTab(index)
            }
        }
    }

    private fun removeTab(index: Int) {
        val selectedIndex = binding.tabDocumentLayout.selectedTabPosition
        if (index == selectedIndex) {
            binding.editor.clearText() //TTL Exception bypass
            closeKeyboard() // Обход бага, когда после удаления вкладки можно было редактировать в ней текст
        }
        // Обход бага, когда после удаления вкладки индикатор не обновляет свою позицию
        if (index < selectedIndex) {
            binding.tabDocumentLayout.setScrollPosition(selectedIndex - 1, 0f, false)
        }
        removeDocument(index)
        binding.tabDocumentLayout.removeTabAt(index)
    }

    // endregion TABS

    private fun observeViewModel() {
        viewModel.toastEvent.observe(this, Observer {
            showToast(it)
        })
        viewModel.updateEvent.observe(this, Observer {
            val appUpdateManager = it.first
            val appUpdateInfo = it.second
            val appUpdateType = it.third
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                appUpdateType,
                this,
                REQUEST_CODE_UPDATE
            )
        })
        viewModel.installEvent.observe(this, Observer {
            Snackbar.make(binding.editor, R.string.message_in_app_update_ready, Snackbar.LENGTH_INDEFINITE)
                .setActionTextColor(getColour(R.color.colorPrimary))
                .setAction(R.string.action_restart) { viewModel.completeUpdate() }
                .show()
        })
        viewModel.documentsEvent.observe(this, Observer { docs ->
            for (document in docs) {
                addTab(document, false)
            }
        })
        viewModel.documentEvent.observe(this, Observer {
            addTab(it, true)
            closeDrawers()
        })
        viewModel.selectionEvent.observe(this, Observer {
            binding.tabDocumentLayout.getTabAt(it)?.select()
            closeDrawers()
        })
        viewModel.unopenableEvent.observe(this, Observer {
            openFile(it)
        })
        viewModel.analysisEvent.observe(this, Observer { model ->
            MaterialDialog(this).show {
                title(R.string.dialog_title_result)
                if (model.exception == null) {
                    message(R.string.message_no_errors_detected)
                } else {
                    message(text = model.exception!!.message)
                }
                positiveButton(R.string.action_ok)
            }
        })
        viewModel.contentEvent.observe(this, Observer { content ->
            binding.editor.setFacadeText(content.text)
            binding.editor.undoStack = content.undoStack
            binding.editor.redoStack = content.redoStack
            binding.editor.scrollX = content.documentModel.scrollX
            binding.editor.scrollY = content.documentModel.scrollY
            binding.editor.setSelection(
                content.documentModel.selectionStart,
                content.documentModel.selectionEnd
            )
        })

        // region PREFERENCES

        viewModel.preferenceEvent.observe(this, Observer { queue ->
            while (queue != null && queue.isNotEmpty()) {
                when (val event = queue.poll()) {
                    is PreferenceEvent.Theme -> binding.editor.theme = event.value
                    is PreferenceEvent.Fullscreen -> {
                        if (event.value) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        }
                    }
                    is PreferenceEvent.FontSize -> {
                        val newConfiguration = binding.editor.configuration.copy(fontSize = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.FontType -> {
                        val newConfiguration = binding.editor.configuration.copy(
                            fontType = TypefaceFactory.create(this, event.value)
                        )
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.WordWrap -> {
                        val newConfiguration = binding.editor.configuration.copy(wordWrap = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.CodeCompletion -> {
                        val newConfiguration = binding.editor.configuration.copy(codeCompletion = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.PinchZoom -> {
                        val newConfiguration = binding.editor.configuration.copy(pinchZoom = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.CurrentLine -> {
                        val newConfiguration = binding.editor.configuration.copy(highlightCurrentLine = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.Delimiters -> {
                        val newConfiguration = binding.editor.configuration.copy(highlightDelimiters = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.ExtendedKeys -> {
                        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
                            if (event.value) {
                                binding.extendedKeyboard.visibility = if (isOpen) View.VISIBLE else View.GONE
                            } else {
                                binding.extendedKeyboard.visibility = View.GONE
                            }
                        }
                    }
                    is PreferenceEvent.SoftKeys -> {
                        val newConfiguration = binding.editor.configuration.copy(softKeyboard = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.AutoIndent -> {
                        val newConfiguration = binding.editor.configuration.copy(autoIndentation = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.AutoBrackets -> {
                        val newConfiguration = binding.editor.configuration.copy(autoCloseBrackets = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                    is PreferenceEvent.AutoQuotes -> {
                        val newConfiguration = binding.editor.configuration.copy(autoCloseQuotes = event.value)
                        binding.editor.configuration = newConfiguration
                    }
                }
            }
        })

        // endregion PREFERENCES

        viewModel.observePreferences() // and loadFiles()
    }

    private fun openFile(documentModel: DocumentModel) {
        try { //Открытие файла через подходящую программу
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                File(documentModel.path)
            )
            val mime = contentResolver.getType(uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.toastEvent.value = R.string.message_cannot_be_opened
        }
    }

    private fun loadDocument(position: Int) {
        if (position > -1) { // if there's at least 1 tab
            val document = viewModel.tabsList[position]
            viewModel.loadFile(document)
        }
    }

    private fun saveDocument(position: Int) {
        if (position > -1) { // if there's at least 1 tab
            val document = viewModel.tabsList[position].copy(
                scrollX = binding.editor.scrollX,
                scrollY = binding.editor.scrollY,
                selectionStart = binding.editor.selectionStart,
                selectionEnd = binding.editor.selectionEnd
            )
            viewModel.tabsList[position] = document
            viewModel.saveToCache(document, binding.editor.getFacadeText())
            viewModel.saveUndoStack(document, binding.editor.undoStack)
            viewModel.saveRedoStack(document, binding.editor.redoStack)
            viewModel.stateLoadingDocuments.set(true) // show loading indicator
            binding.editor.clearText() //TTL Exception bypass
        }
    }

    private fun removeDocument(position: Int) {
        val document = viewModel.tabsList[position]
        viewModel.tabsList.removeAt(position)
        viewModel.stateNothingFound.set(viewModel.tabsList.isEmpty())
        viewModel.deleteCache(document)
    }

    // region TOOLBAR

    override fun onDrawerButton() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onNewButton() {
        onDrawerButton()
    }

    override fun onOpenButton() {
        onDrawerButton()
        viewModel.toastEvent.value = R.string.message_select_file
    }

    override fun onSaveButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (position > -1) {
            val document = viewModel.tabsList[position]
            viewModel.saveFile(document, binding.editor.getFacadeText())
            viewModel.saveToCache(document, binding.editor.getFacadeText())
            viewModel.saveUndoStack(document, binding.editor.undoStack)
            viewModel.saveRedoStack(document, binding.editor.redoStack)
        } else {
            viewModel.toastEvent.value = R.string.message_no_open_files
        }
    }

    override fun onCloseButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (position > -1) {
            removeTab(position)
        } else {
            viewModel.toastEvent.value = R.string.message_no_open_files
        }
    }

    override fun onCutButton() {
        if (binding.editor.hasSelection()) {
            binding.editor.cut()
        } else {
            viewModel.toastEvent.value = R.string.message_nothing_to_cut
        }
    }

    override fun onCopyButton() {
        if (binding.editor.hasSelection()) {
            binding.editor.copy()
        } else {
            viewModel.toastEvent.value = R.string.message_nothing_to_copy
        }
    }

    override fun onPasteButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (binding.editor.hasPrimaryClip() && position > -1) {
            binding.editor.paste()
        } else {
            viewModel.toastEvent.value = R.string.message_nothing_to_paste
        }
    }

    override fun onSelectAllButton() {
        binding.editor.selectAll()
    }

    override fun onSelectLineButton() {
        binding.editor.selectLine()
    }

    override fun onDeleteLineButton() {
        binding.editor.deleteLine()
    }

    override fun onDuplicateLineButton() {
        binding.editor.duplicateLine()
    }

    override fun onFindButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (position > -1) {
            MaterialDialog(this).show {
                title(R.string.dialog_title_find)
                customView(R.layout.dialog_find)
                negativeButton(R.string.action_cancel)
                positiveButton(R.string.action_find) {
                    val textToFind = it.getCustomView()
                        .findViewById<TextInputEditText>(R.id.input).text.toString()
                    val isMatchCaseChecked = it.getCustomView()
                        .findViewById<CheckBox>(R.id.box_matchCase).isChecked
                    val isRegExpChecked = it.getCustomView()
                        .findViewById<CheckBox>(R.id.box_regExp).isChecked
                    val isWordsOnlyChecked = it.getCustomView()
                        .findViewById<CheckBox>(R.id.box_wordOnly).isChecked
                    if (textToFind.isNotEmpty() && textToFind.isNotBlank()) {
                        binding.editor.find(textToFind, isMatchCaseChecked, isRegExpChecked, isWordsOnlyChecked)
                    } else {
                        viewModel.toastEvent.value = R.string.message_enter_the_text
                    }
                }
            }
        } else {
            viewModel.toastEvent.value = R.string.message_no_open_files
        }
    }

    override fun onReplaceAllButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (position > -1) {
            MaterialDialog(this).show {
                title(R.string.dialog_title_replace_all)
                customView(R.layout.dialog_replace_all)
                negativeButton(R.string.action_cancel)
                positiveButton(R.string.action_replace_all) {
                    val textReplaceWhat = it.getCustomView()
                        .findViewById<TextInputEditText>(R.id.input).text.toString()
                    val textReplaceWith = it.getCustomView()
                        .findViewById<TextInputEditText>(R.id.input2).text.toString()
                    if (textReplaceWhat.isNotEmpty()) {
                        binding.editor.replaceAll(textReplaceWhat, textReplaceWith)
                    } else {
                        viewModel.toastEvent.value = R.string.message_enter_the_text
                    }
                }
            }
        } else {
            viewModel.toastEvent.value = R.string.message_no_open_files
        }
    }

    override fun onGoToLineButton() {
        val position = binding.tabDocumentLayout.selectedTabPosition
        if (position > -1) {
            MaterialDialog(this).show {
                title(R.string.dialog_title_goto_line)
                customView(R.layout.dialog_goto_line)
                negativeButton(R.string.action_cancel)
                positiveButton(R.string.action_go_to) {
                    val input = getCustomView().findViewById<TextInputEditText>(R.id.input)
                    val inputResult = input.text.toString()
                    if (inputResult.isNotEmpty()) {
                        val toLine = inputResult.toInt() - 1 // т.к первая линия 0
                        when {
                            toLine <= 0 -> viewModel.toastEvent.value = R.string.message_line_above_than_0
                            toLine < binding.editor.arrayLineCount -> binding.editor.gotoLine(toLine)
                            else -> viewModel.toastEvent.value = R.string.message_line_not_exists
                        }
                    } else {
                        viewModel.toastEvent.value = R.string.message_line_not_exists
                    }
                }
            }
        } else {
            viewModel.toastEvent.value = R.string.message_no_open_files
        }
    }

    override fun onCodeAnalysisButton() {
        if (isUltimate()) {
            val position = binding.tabDocumentLayout.selectedTabPosition
            if (position > -1) {
                viewModel.analyze(position, binding.editor.getFacadeText())
            } else {
                viewModel.toastEvent.value = R.string.message_no_open_files
            }
        } else {
            DialogStore.Builder(this).show()
        }
    }

    override fun onInsertColorButton() {
        if (isUltimate()) {
            val position = binding.tabDocumentLayout.selectedTabPosition
            if (position > -1) {
                MaterialDialog(this).show {
                    title(R.string.dialog_title_color_picker)
                    colorChooser(
                        colors = ColorPalette.Primary,
                        subColors = ColorPalette.PrimarySub,
                        allowCustomArgb = true,
                        showAlphaSelector = true
                    ) { _, color ->
                        binding.editor.insert(color.toHexString())
                    }
                    positiveButton(R.string.action_insert)
                    negativeButton(R.string.action_cancel)
                }
            } else {
                viewModel.toastEvent.value = R.string.message_no_open_files
            }
        } else {
            DialogStore.Builder(this).show()
        }
    }

    override fun onUndoButton() {
        binding.editor.undo()
    }

    override fun onRedoButton() {
        binding.editor.redo()
    }

    override fun onSettingsButton() {
        launchActivity<SettingsActivity>()
    }

    // endregion TOOLBAR
}