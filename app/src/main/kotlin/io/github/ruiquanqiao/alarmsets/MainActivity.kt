package io.github.ruiquanqiao.alarmsets

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ruiquanqiao.alarmsets.core.designsystem.AlarmSetsTheme
import io.github.ruiquanqiao.alarmsets.core.model.AlarmSet
import io.github.ruiquanqiao.alarmsets.ui.editor.AlarmEditorSheet
import io.github.ruiquanqiao.alarmsets.ui.editor.EditorEvent
import io.github.ruiquanqiao.alarmsets.ui.editor.SetEditorScreen
import io.github.ruiquanqiao.alarmsets.ui.editor.SetEditorViewModel
import io.github.ruiquanqiao.alarmsets.ui.ringtones.RingtonePickerScreen
import io.github.ruiquanqiao.alarmsets.ui.ringtones.RingtonePickerViewModel
import io.github.ruiquanqiao.alarmsets.ui.settings.SettingsScreen
import io.github.ruiquanqiao.alarmsets.ui.settings.SettingsViewModel
import io.github.ruiquanqiao.alarmsets.ui.settings.exactAlarmSettingsIntent
import io.github.ruiquanqiao.alarmsets.ui.sets.SetsEvent
import io.github.ruiquanqiao.alarmsets.ui.sets.SetsScreen
import io.github.ruiquanqiao.alarmsets.ui.sets.SetsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AlarmSetsApplication).container

        setContent {
            AlarmSetsTheme {
                RequestNotificationPermissionOnce()
                AlarmSetsNavHost(container)
            }
        }
    }
}

/**
 * From Android 13 a denied notification permission means the ringing
 * notification cannot be posted, which in turn blocks the full-screen alarm UI.
 * Asked once at startup rather than buried in settings.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private object Routes {
    const val SETS = "sets"
    const val SETTINGS = "settings"
    const val SET_EDITOR = "set/{setId}"
    const val RINGTONES = "ringtones/{alarmId}"

    fun setEditor(setId: Long) = "set/$setId"
    fun ringtones(alarmId: Long) = "ringtones/$alarmId"
}

/** Builds a ViewModel that needs constructor arguments, without a DI framework. */
private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

@Composable
private fun AlarmSetsNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Routes.SETS) {

        composable(Routes.SETS) {
            val vm: SetsViewModel = viewModel(factory = factory { SetsViewModel(container) })
            val state by vm.uiState.collectAsStateWithLifecycle()
            val expanded by vm.expandedSetIds.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) { vm.refreshWarnings() }

            // Some file managers hand a .json file over as text/plain or with
            // no type at all, so the filter stays wide and the parser decides.
            val templatePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text != null) {
                        vm.importTemplate(text)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Could not read that file") }
                    }
                }
            }

            LaunchedEffect(Unit) {
                vm.eventFlow.collect { event ->
                    when (event) {
                        is SetsEvent.Imported -> {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.import_done, event.name, event.alarmCount,
                                ),
                            )
                        }

                        is SetsEvent.ImportFailed ->
                            snackbarHostState.showSnackbar(event.reason)
                    }
                }
            }

            SetsScreen(
                state = state,
                expandedSetIds = expanded,
                onToggleExpanded = vm::toggleExpanded,
                onSetEnabled = vm::setEnabled,
                onAlarmEnabled = vm::alarmEnabled,
                onOpenSet = { navController.navigate(Routes.setEditor(it)) },
                onCreateSet = {
                    scope.launch {
                        val id = container.saveAlarmSet(AlarmSet(name = "New set"))
                        navController.navigate(Routes.setEditor(id))
                    }
                },
                onImportSet = {
                    templatePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(
            Routes.SET_EDITOR,
            arguments = listOf(navArgument("setId") { type = NavType.LongType }),
        ) { entry ->
            val setId = entry.arguments?.getLong("setId") ?: return@composable
            val vm: SetEditorViewModel =
                viewModel(factory = factory { SetEditorViewModel(container, setId) })
            val set by vm.set.collectAsStateWithLifecycle()
            val editing by vm.editingAlarm.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                vm.eventFlow.collect { event ->
                    when (event) {
                        is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text)
                        EditorEvent.Closed -> navController.popBackStack()
                        is EditorEvent.Exported -> {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "${event.setName}.alarmset.json")
                                putExtra(Intent.EXTRA_TEXT, event.json)
                            }
                            context.startActivity(Intent.createChooser(share, null))
                        }
                    }
                }
            }

            SetEditorScreen(
                set = set,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onRename = vm::rename,
                onAccent = vm::setAccent,
                onShift = vm::shift,
                onAddAlarm = vm::startNewAlarm,
                onEditAlarm = vm::startEditing,
                onAlarmEnabled = vm::setAlarmEnabled,
                onDuplicate = vm::duplicate,
                onExport = vm::export,
                onDelete = vm::delete,
            )

            editing?.let { alarm ->
                AlarmEditorSheet(
                    alarm = alarm,
                    ringtoneTitle = alarm.ringtone.displayTitle(),
                    onDismiss = vm::cancelEditing,
                    onSave = vm::saveEditing,
                    onDelete = vm::deleteAlarm,
                    onPickRingtone = { draft ->
                        // The picker writes straight to a stored alarm, so an
                        // unsaved draft is persisted first to give it an id.
                        scope.launch {
                            val id = container.saveAlarm(draft)
                            vm.cancelEditing()
                            navController.navigate(Routes.ringtones(id))
                        }
                    },
                )
            }
        }

        composable(
            Routes.RINGTONES,
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { entry ->
            val alarmId = entry.arguments?.getLong("alarmId") ?: return@composable
            val vm: RingtonePickerViewModel =
                viewModel(factory = factory { RingtonePickerViewModel(container, alarmId) })
            val state by vm.state.collectAsStateWithLifecycle()

            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    vm.import(uri.toString(), uri.lastPathSegment.orEmpty())
                }
            }

            RingtonePickerScreen(
                state = state,
                onSelect = vm::select,
                onImport = { picker.launch(arrayOf("audio/*")) },
                onBack = {
                    vm.stopPreview()
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container) })
            val state by vm.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { vm.refresh() }
            LaunchedEffect(state.installIntent) {
                state.installIntent?.let {
                    context.startActivity(it)
                    vm.consumeInstallIntent()
                }
            }

            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onCheckUpdates = vm::checkForUpdates,
                onDownload = vm::download,
                onFixExactAlarms = {
                    context.startActivity(exactAlarmSettingsIntent(context.packageName))
                },
                onAllowInstalls = { context.startActivity(vm.unknownSourcesIntent()) },
            )
        }
    }
}

private fun io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef.displayTitle(): String =
    when (this) {
        is io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef.Bundled ->
            io.github.ruiquanqiao.alarmsets.core.audio.BundledRingtones.find(key)?.title ?: key

        is io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef.Imported -> displayName
        io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef.SystemDefault -> "System default"
        io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef.Silent -> "Silent"
    }
