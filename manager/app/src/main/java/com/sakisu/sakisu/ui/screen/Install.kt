package com.sakisu.sakisu.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sakisu.sakisu.R
import com.sakisu.sakisu.getKernelVersion
import com.sakisu.sakisu.ui.component.DialogHandle
import com.sakisu.sakisu.ui.component.rememberConfirmDialog
import com.sakisu.sakisu.ui.component.rememberCustomDialog
import com.sakisu.sakisu.ui.component.settings.AppBackButton
import com.sakisu.sakisu.ui.component.settings.SettingsBaseWidget
import com.sakisu.sakisu.ui.component.settings.SettingsChooseDialog
import com.sakisu.sakisu.ui.component.settings.SettingsChooseWidget
import com.sakisu.sakisu.ui.navigation.LocalNavigator
import com.sakisu.sakisu.ui.navigation.Route
import com.sakisu.sakisu.ui.screen.kernelFlash.component.SlotSelectionDialog
import com.sakisu.sakisu.ui.theme.CardConfig
import com.sakisu.sakisu.ui.theme.CardConfig.cardAlpha
import com.sakisu.sakisu.ui.theme.ThemeConfig
import com.sakisu.sakisu.ui.theme.blurEffect
import com.sakisu.sakisu.ui.theme.blurSource
import com.sakisu.sakisu.ui.theme.getCardColors
import com.sakisu.sakisu.ui.theme.getCardElevation
import com.sakisu.sakisu.ui.theme.renderBackgroundBlur
import com.sakisu.sakisu.ui.util.LkmSelection
import com.sakisu.sakisu.ui.util.classifyBootImage
import com.sakisu.sakisu.ui.util.getAvailablePartitions
import com.sakisu.sakisu.ui.util.getCurrentKmi
import com.sakisu.sakisu.ui.util.getDefaultPartition
import com.sakisu.sakisu.ui.util.getSlotSuffix
import com.sakisu.sakisu.ui.util.getSupportedKmis
import com.sakisu.sakisu.ui.util.isAbDevice
import com.sakisu.sakisu.ui.util.probeRootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */

private data class InstallDeviceProbe(
    val rootAvailable: Boolean = false,
    val isAbDevice: Boolean = false,
    val defaultPartition: String = "boot",
    val currentKmi: String = "",
)

private const val INSTALL_PROBE_TIMEOUT_MS = 8_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(
    preselectedKernelUri: String? = null
) {
    val context = LocalContext.current
    var installMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBootImageKind by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var enableVivoPatch by remember {
        mutableStateOf(
            Build.MANUFACTURER.orEmpty().contains("vivo", ignoreCase = true) ||
                Build.MANUFACTURER.orEmpty().contains("iqoo", ignoreCase = true) ||
                Build.BRAND.orEmpty().contains("vivo", ignoreCase = true) ||
                Build.BRAND.orEmpty().contains("iqoo", ignoreCase = true)
        )
    }

    val kernelVersion = getKernelVersion()
    val isGKI = kernelVersion.isGKI()
    // Single serialized probe: rapid Home↔Install navigation used to cancel overlapping
    // produceState shell jobs on the shared libsu Shell and freeze the manager.
    var deviceProbe by remember { mutableStateOf(InstallDeviceProbe()) }
    LaunchedEffect(Unit) {
        deviceProbe = withContext(Dispatchers.IO) {
            withTimeoutOrNull(INSTALL_PROBE_TIMEOUT_MS) {
                val root = runCatching { probeRootAvailable() }.getOrDefault(false)
                val ab = runCatching { isAbDevice() }.getOrDefault(false)
                val partition = runCatching { getDefaultPartition() }.getOrDefault("boot")
                val kmi = if (root) {
                    runCatching { getCurrentKmi() }.getOrDefault("")
                } else {
                    ""
                }
                InstallDeviceProbe(
                    rootAvailable = root,
                    isAbDevice = ab,
                    defaultPartition = partition.ifBlank { "boot" },
                    currentKmi = kmi,
                )
            } ?: InstallDeviceProbe()
        }
    }
    val abDevice = deviceProbe.isAbDevice
    val summary = stringResource(R.string.horizon_kernel_summary)

    // 处理预选的内核文件
    LaunchedEffect(preselectedKernelUri) {
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = preselectedUri,
                    summary = summary
                )
                installMethod = horizonMethod
                tempKernelUri = preselectedUri

                if (abDevice) {
                    showSlotSelectionDialog = true
                }
            } catch (e: Exception) {

            }
        }
    }

    if (showRebootDialog) {
        RebootDialog(
            show = true,
            onDismiss = { showRebootDialog = false },
            onConfirm = {
                showRebootDialog = false
                try {
                    val process = Runtime.getRuntime().exec("su")
                    process.outputStream.bufferedWriter().use { writer ->
                        writer.write("svc power reboot\n")
                        writer.write("exit\n")
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.failed_reboot, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    var partitionSelectionIndex by remember { mutableIntStateOf(0) }
    var hasCustomSelected by remember { mutableStateOf(false) }
    var partitionsState by remember { mutableStateOf<List<String>>(emptyList()) }
    var defaultPartitionState by remember { mutableStateOf("") }
    val navigator = LocalNavigator.current

    LaunchedEffect(installMethod) {
        val method = installMethod
        if (method is InstallMethod.DirectInstall || method is InstallMethod.DirectInstallToInactiveSlot) {
            partitionsState = withContext(Dispatchers.IO) { getAvailablePartitions() }
            defaultPartitionState = withContext(Dispatchers.IO) { getDefaultPartition() }
        }
    }

    val onInstall = {
        installMethod?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri,
                                selectedSlot = method.slot
                            )
                        )
                    }
                }
                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    val partitionSelection = partitionsState.getOrNull(partitionSelectionIndex)
                    val flashIt = FlashIt.FlashBoot(
                        boot = if (method is InstallMethod.SelectFile) method.uri else null,
                        lkm = lkmSelection,
                        ota = isOta,
                        partition = partitionSelection,
                        vivoPatch = enableVivoPatch,
                    )
                    navigator.push(Route.Flash(flashIt))
                }
            }
        }
        Unit
    }

    // 槽位选择
    SlotSelectionDialog(
        show = showSlotSelectionDialog && abDevice,
        onDismiss = { showSlotSelectionDialog = false },
        onSlotSelected = { slot ->
            showSlotSelectionDialog = false
            val horizonMethod = InstallMethod.HorizonKernel(
                uri = tempKernelUri,
                slot = slot,
                summary = summary
            )
            installMethod = horizonMethod
        }
    )

    val currentKmi = deviceProbe.currentKmi

    val preferredKmi = if (enableVivoPatch) {
        currentKmi.takeIf { it.isNotBlank() }?.let { base ->
            if (base.endsWith("_vivo")) base else "${base}_vivo"
        }
    } else {
        currentKmi.takeIf { it.isNotBlank() }
    }

    val selectKmiDialog = rememberSelectKmiDialog(preferredKmi = preferredKmi) { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            onInstall()
        }
    }

    val continueInstall: () -> Unit = {
        val selectedPartition = partitionsState.getOrNull(partitionSelectionIndex)
        val isVivoVendorBootRmvr = enableVivoPatch &&
            (selectedBootImageKind == "vendor_boot" || selectedPartition == "vendor_boot")
        val needsKmi = isGKI &&
            lkmSelection == LkmSelection.KmiNone &&
            installMethod !is InstallMethod.HorizonKernel &&
            (if (enableVivoPatch) !isVivoVendorBootRmvr else currentKmi.isBlank())

        if (needsKmi) {
            selectKmiDialog.show()
        } else {
            onInstall()
        }
    }

    val onClickNext: () -> Unit = {
        val method = installMethod
        if (enableVivoPatch &&
            method is InstallMethod.SelectFile &&
            selectedBootImageKind == null
        ) {
            coroutineScope.launch {
                selectedBootImageKind = method.uri?.let { classifyBootImage(it) } ?: "unknown"
                continueInstall()
            }
        } else {
            continueInstall()
        }
    }

    val installOnlySupportKoFile = stringResource(R.string.install_only_support_ko_file)
    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val isKo = isKoFile(context, uri)
                if (isKo) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    Toast.makeText(
                        context,
                        installOnlySupportKoFile,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val onLkmUpload = {
        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/octet-stream"
        })
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .blurSource()
                .padding(top = 12.dp)
        ) {
            SelectInstallMethod(
                isGKI = isGKI,
                rootAvailable = deviceProbe.rootAvailable,
                isAbDevice = deviceProbe.isAbDevice,
                defaultPartitionName = deviceProbe.defaultPartition,
                onSelected = { method ->
                    selectedBootImageKind = null
                    if (method is InstallMethod.HorizonKernel && method.uri != null) {
                        if (abDevice) {
                            tempKernelUri = method.uri
                            showSlotSelectionDialog = true
                        } else {
                            installMethod = method
                        }
                    } else {
                        installMethod = method
                    }
                },
                selectedMethod = installMethod
            )

            // 选择LKM直接安装分区
            AnimatedVisibility(
                visible = installMethod is InstallMethod.DirectInstall || installMethod is InstallMethod.DirectInstallToInactiveSlot,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    ElevatedCard(
                        colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                        elevation = getCardElevation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
                        val suffix = produceState(initialValue = "", isOta) {
                            value = getSlotSuffix(isOta)
                        }.value

                        val displayPartitions = partitionsState.map { name ->
                            if (defaultPartitionState == name) "$name (default)" else name
                        }

                        val defaultIndex = partitionsState.indexOf(defaultPartitionState).takeIf { it >= 0 } ?: 0
                        if (!hasCustomSelected) partitionSelectionIndex = defaultIndex

                        if (displayPartitions.isNotEmpty()) {
                            SettingsChooseWidget(
                                icon = Icons.Default.Edit,
                                items = displayPartitions,
                                selectedIndex = partitionSelectionIndex,
                                title = "${stringResource(R.string.install_select_partition)} (${suffix})",
                                onSelectedIndexChange = { index ->
                                    hasCustomSelected = true
                                    partitionSelectionIndex = index
                                },
                            )
                        }
                    }
                }
            }

            if (isGKI) {
                ElevatedCard(
                    colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                    elevation = getCardElevation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    SettingsBaseWidget(
                        title = "vivo修补",
                        description = "去除vr或适配vivo特性",
                        icon = Icons.Default.Security,
                        onClick = {
                            enableVivoPatch = !enableVivoPatch
                            selectedBootImageKind = null
                        },
                    ) {
                        Text(
                            text = if (enableVivoPatch) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enableVivoPatch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isGKI) {
                    // 使用本地的LKM文件
                    ElevatedCard(
                        colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                        elevation = getCardElevation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        SettingsBaseWidget(
                            title = stringResource(id = R.string.install_upload_lkm_file),
                            onClick = {
                                onLkmUpload()
                            },
                            description = (lkmSelection as? LkmSelection.LkmUri)?.let {
                                stringResource(
                                    id = R.string.selected_lkm,
                                    it.uri.lastPathSegment ?: "(file)"
                                )
                            },
                            icon = Icons.AutoMirrored.Filled.Input,
                        ) { }
                    }
                }

                (installMethod as? InstallMethod.HorizonKernel)?.let { method ->
                    if (method.slot != null) {
                        ElevatedCard(
                            colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                            elevation = getCardElevation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.selected_slot,
                                    if (method.slot == "a") stringResource(id = R.string.slot_a)
                                    else stringResource(id = R.string.slot_b)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = installMethod != null,
                    onClick = onClickNext,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        stringResource(id = R.string.install_next),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RebootDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(id = R.string.reboot_complete_title)) },
            text = { Text(stringResource(id = R.string.reboot_complete_msg)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.no))
                }
            }
        )
    }
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @param:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}

@Composable
private fun SelectInstallMethod(
    isGKI: Boolean = false,
    rootAvailable: Boolean = false,
    isAbDevice: Boolean = false,
    defaultPartitionName: String = "boot",
    onSelected: (InstallMethod) -> Unit = {},
    selectedMethod: InstallMethod? = null
) {
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val selectFileTip = stringResource(
        id = R.string.select_file_tip, defaultPartitionName
    )

    val radioOptions = mutableListOf<InstallMethod>(
        InstallMethod.SelectFile(summary = selectFileTip)
    )

    if (rootAvailable) {
        radioOptions.add(InstallMethod.DirectInstall)
        if (isAbDevice) {
            radioOptions.add(InstallMethod.DirectInstallToInactiveSlot)
        }
        radioOptions.add(InstallMethod.HorizonKernel(summary = horizonKernelSummary))
    }

    var selectedOption by remember { mutableStateOf<InstallMethod?>(null) }
    var currentSelectingMethod by remember { mutableStateOf<InstallMethod?>(null) }

    LaunchedEffect(selectedMethod) {
        selectedOption = selectedMethod
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option = when (currentSelectingMethod) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(
                        uri,
                        summary = selectFileTip
                    )

                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(
                        uri,
                        summary = horizonKernelSummary
                    )

                    else -> null
                }
                option?.let { opt ->
                    selectedOption = opt
                    onSelected(opt)
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            selectedOption = InstallMethod.DirectInstallToInactiveSlot
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        },
        onDismiss = null
    )

    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        currentSelectingMethod = option
        when (option) {
            is InstallMethod.SelectFile, is InstallMethod.HorizonKernel -> {
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/zip")
                    )
                })
            }

            is InstallMethod.DirectInstall -> {
                selectedOption = option
                onSelected(option)
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    var lkmExpanded by remember { mutableStateOf(false) }
    var gkiExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // LKM 安装/修补
        if (isGKI) {
            ElevatedCard(
                colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                elevation = getCardElevation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                stringResource(R.string.Lkm_install_methods),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        modifier = Modifier.clickable {
                            lkmExpanded = !lkmExpanded
                        }
                    )
                }

                AnimatedVisibility(
                    visible = lkmExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                    ) {
                        radioOptions.filter { it !is InstallMethod.HorizonKernel }.forEach { option ->
                            val interactionSource = remember { MutableInteractionSource() }
                            Surface(
                                color = if (option.javaClass == selectedOption?.javaClass)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = cardAlpha)
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardAlpha),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = option.javaClass == selectedOption?.javaClass,
                                            onClick = { onClick(option) },
                                            role = Role.RadioButton,
                                            indication = LocalIndication.current,
                                            interactionSource = interactionSource
                                        )
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                ) {
                                    RadioButton(
                                        selected = option.javaClass == selectedOption?.javaClass,
                                        onClick = null,
                                        interactionSource = interactionSource,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 10.dp)
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(id = option.label),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        option.summary?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // anykernel3 刷写
        if (rootAvailable) {
            ElevatedCard(
                colors = getCardColors(MaterialTheme.colorScheme.surfaceVariant),
                elevation = getCardElevation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Filled.FileUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                stringResource(R.string.GKI_install_methods),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        modifier = Modifier.clickable {
                            gkiExpanded = !gkiExpanded
                        }
                    )
                }

                AnimatedVisibility(
                    visible = gkiExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                    ) {
                        radioOptions.filterIsInstance<InstallMethod.HorizonKernel>().forEach { option ->
                            val interactionSource = remember { MutableInteractionSource() }
                            Surface(
                                color = if (option.javaClass == selectedOption?.javaClass)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = cardAlpha)
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardAlpha),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = option.javaClass == selectedOption?.javaClass,
                                            onClick = { onClick(option) },
                                            role = Role.RadioButton,
                                            indication = LocalIndication.current,
                                            interactionSource = interactionSource
                                        )
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                ) {
                                    RadioButton(
                                        selected = option.javaClass == selectedOption?.javaClass,
                                        onClick = null,
                                        interactionSource = interactionSource,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 10.dp)
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(id = option.label),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        option.summary?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectKmiDialog(
    preferredKmi: String? = null,
    onSelected: (String?) -> Unit
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val supportedKmi by produceState(initialValue = emptyList()) {
            value = getSupportedKmis()
        }
        val orderedKmis = remember(supportedKmi, preferredKmi) {
            if (preferredKmi.isNullOrBlank()) {
                supportedKmi
            } else {
                val preferred = supportedKmi.firstOrNull { it == preferredKmi }
                val fallback = if (preferredKmi.endsWith("_vivo")) {
                    preferredKmi.removeSuffix("_vivo")
                } else {
                    preferredKmi
                }
                val secondary = supportedKmi.firstOrNull { it == fallback }
                buildList {
                    preferred?.let { add(it) }
                    if (secondary != null && secondary != preferred) add(secondary)
                    addAll(supportedKmi.filter { it != preferred && it != secondary })
                }
            }
        }
        val selectedIndex = preferredKmi
            ?.let { orderedKmis.indexOf(it).takeIf { index -> index >= 0 } }
            ?: if (orderedKmis.isNotEmpty()) 0 else -1

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            SettingsChooseDialog(
                show = true,
                title = stringResource(R.string.select_kmi),
                items = orderedKmis,
                selectedIndex = selectedIndex,
                onDismiss = dismiss,
                onSelectedIndexChange = { index ->
                    onSelected(orderedKmis.getOrNull(index))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(
                stringResource(R.string.install)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardAlpha),
            scrolledContainerColor =
                if (ThemeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardAlpha),
        ),
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}

@Preview
@Composable
fun SelectInstallPreview() {
    InstallScreen()
}
