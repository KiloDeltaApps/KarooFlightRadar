package com.kilodeltaapps.karooflightradar.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kilodeltaapps.karooflightradar.data.AirfieldDisplayFormat
import com.kilodeltaapps.karooflightradar.data.AircraftDataType
import com.kilodeltaapps.karooflightradar.data.LabelDisplayMode
import com.kilodeltaapps.karooflightradar.data.LabelLineConfig
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import com.kilodeltaapps.karooflightradar.utils.UnitConversions
import kotlin.math.roundToInt

// Navigation States
enum class SettingsPage {
    MAIN_MENU,
    RADAR_DISPLAY,
    LABELS,
    AIRPORTS,
    FILTERS,
    DATA,
    UNITS,
    SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN_MENU) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when(currentPage) {
                        SettingsPage.MAIN_MENU -> "Settings"
                        SettingsPage.RADAR_DISPLAY -> "Radar Display"
                        SettingsPage.LABELS -> "Label Settings"
                        SettingsPage.AIRPORTS -> "Airports"
                        SettingsPage.FILTERS -> "Filters"
                        SettingsPage.DATA -> "Data Connection"
                        SettingsPage.UNITS -> "Units"
                        SettingsPage.SYSTEM -> "System"
                    })
                },
                navigationIcon = {
                    if (currentPage != SettingsPage.MAIN_MENU) {
                        IconButton(onClick = { currentPage = SettingsPage.MAIN_MENU }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Crossfade(targetState = currentPage, label = "SettingsNavTransition") { page ->
                // Scroll state for all sub-pages
                val pageScrollState = rememberScrollState()

                when (page) {
                    SettingsPage.MAIN_MENU -> MainMenu(
                        onNavigate = { currentPage = it }
                    )
                    SettingsPage.RADAR_DISPLAY -> RadarDisplaySettings(uiState, viewModel, pageScrollState)
                    SettingsPage.LABELS -> LabelSettingsScreen(uiState, viewModel, pageScrollState)
                    SettingsPage.AIRPORTS -> AirportSettingsScreen(uiState, viewModel, pageScrollState)
                    SettingsPage.FILTERS -> FilterSettingsScreen(uiState, viewModel, pageScrollState)
                    SettingsPage.DATA -> DataSettingsScreen(uiState, viewModel, pageScrollState)
                    SettingsPage.UNITS -> UnitsSettingsScreen(uiState, viewModel, pageScrollState)
                    SettingsPage.SYSTEM -> SystemSettingsScreen(uiState, viewModel, pageScrollState)
                }
            }
        }
    }
}

@Composable
private fun MainMenu(onNavigate: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MenuButton(
            title = "Radar Display",
            subtitle = "Range, Orientation, Trails",
            icon = Icons.Default.Radar,
            onClick = { onNavigate(SettingsPage.RADAR_DISPLAY) }
        )
        MenuButton(
            title = "Label Settings",
            subtitle = "Fields, Rolling/Static Modes",
            icon = Icons.Default.Label,
            onClick = { onNavigate(SettingsPage.LABELS) }
        )
        MenuButton(
            title = "Airports",
            subtitle = "Visibility, Formats, Preload",
            icon = Icons.Default.FlightTakeoff,
            onClick = { onNavigate(SettingsPage.AIRPORTS) }
        )
        MenuButton(
            title = "Filters",
            subtitle = "Altitude, Speed Restrictions",
            icon = Icons.Default.FilterAlt,
            onClick = { onNavigate(SettingsPage.FILTERS) }
        )
        MenuButton(
            title = "Data Connection",
            subtitle = "API Range, Polling Interval",
            icon = Icons.Default.DataObject,
            onClick = { onNavigate(SettingsPage.DATA) }
        )
        MenuButton(
            title = "Units",
            subtitle = "Distance, Altitude Units",
            icon = Icons.Default.Settings,
            onClick = { onNavigate(SettingsPage.UNITS) }
        )
        MenuButton(
            title = "System",
            subtitle = "Simulation, Notifications, Reset",
            icon = Icons.Default.AirplanemodeActive,
            onClick = { onNavigate(SettingsPage.SYSTEM) }
        )
    }
}

// --- NEW LABEL SETTINGS SCREEN ---

@Composable
private fun LabelSettingsScreen(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {

        SettingsCard(title = "Appearance") {
            CompactNumberInput(
                label = "Label Text Size (sp)",
                value = uiState.labelSize,
                onValueChange = viewModel::onLabelSizeChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- TOP LINE CONFIG ---
        SettingsCard(title = "Top Line Configuration") {
            LabelLineEditor(
                label = "Top Line Mode",
                currentConfig = uiState.labelConfig.topLine,
                onModeChange = viewModel::updateTopLineMode,
                onFieldsChange = viewModel::updateTopLineFields
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- BOTTOM LINE CONFIG ---
        SettingsCard(title = "Bottom Line Configuration") {
            LabelLineEditor(
                label = "Bottom Line Mode",
                currentConfig = uiState.labelConfig.bottomLine,
                onModeChange = viewModel::updateBottomLineMode,
                onFieldsChange = viewModel::updateBottomLineFields
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SMART ALTITUDE ---
        SettingsCard(title = "Smart Altitude Settings") {
            // Determine label based on Unit setting
            val altLabel = if (uiState.altitudeUnits == AppSettings.AltitudeUnits.METERS) "Meters" else "Feet"

            Text("Transition Altitude ($altLabel)", style = MaterialTheme.typography.bodyMedium)

            // Convert the stored meters to the display unit for the text field
            val displayValue = if (uiState.altitudeUnits == AppSettings.AltitudeUnits.METERS) {
                uiState.labelConfig.smartAltitudeTransitionMeters
            } else {
                UnitConversions.metersToFeet(uiState.labelConfig.smartAltitudeTransitionMeters.toDouble()).roundToInt()
            }

            // 'remember' with key ensures text updates if units change
            var tempText by remember(displayValue) { mutableStateOf(displayValue.toString()) }

            OutlinedTextField(
                value = tempText,
                onValueChange = {
                    tempText = it
                    it.toIntOrNull()?.let { valInt -> viewModel.updateSmartAltitudeTransition(valInt) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(altLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Below this altitude, raw $altLabel are shown. Above this, Flight Level (FL) is shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LabelLineEditor(
    label: String,
    currentConfig: LabelLineConfig,
    onModeChange: (LabelDisplayMode) -> Unit,
    onFieldsChange: (List<AircraftDataType>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column {
        // Mode Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode:", modifier = Modifier.weight(1f))
            Switch(
                checked = currentConfig.mode == LabelDisplayMode.ROLLING,
                onCheckedChange = { isRolling ->
                    onModeChange(if(isRolling) LabelDisplayMode.ROLLING else LabelDisplayMode.STATIC_COMBINED)
                }
            )
        }
        Text(
            text = currentConfig.mode.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        // Field Selector Button
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("Select Fields (${currentConfig.fields.size})")
        }

        // Selected Fields Preview
        if (currentConfig.fields.isNotEmpty()) {
            Text(
                text = currentConfig.fields.joinToString(", ") { it.displayName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = "No fields selected (Line Hidden)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (showDialog) {
        MultiSelectDialog(
            options = AircraftDataType.values().toList(),
            selectedOptions = currentConfig.fields,
            onDismiss = { showDialog = false },
            onConfirm = { selected ->
                onFieldsChange(selected)
                showDialog = false
            }
        )
    }
}

@Composable
fun MultiSelectDialog(
    options: List<AircraftDataType>,
    selectedOptions: List<AircraftDataType>,
    onDismiss: () -> Unit,
    onConfirm: (List<AircraftDataType>) -> Unit
) {
    // Maintain a visual list for reordering.
    val displayList = remember(selectedOptions) {
        mutableStateListOf<AircraftDataType>().apply {
            addAll(selectedOptions)
            addAll(options.filter { !selectedOptions.contains(it) })
        }
    }

    val tempSelected = remember(selectedOptions) {
        mutableStateListOf<AircraftDataType>().apply { addAll(selectedOptions) }
    }

    val saveAndDismiss = {
        val finalSelection = displayList.filter { tempSelected.contains(it) }
        onConfirm(finalSelection)
    }

    Dialog(onDismissRequest = { saveAndDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Customize Fields", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Drag items to reorder. Check to display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    ReorderableList(
                        items = displayList,
                        onMove = { from, to ->
                            if (from in displayList.indices && to in displayList.indices) {
                                displayList.add(to, displayList.removeAt(from))
                            }
                        }
                    ) { item ->
                        ReorderableTile(
                            label = item.displayName,
                            isChecked = tempSelected.contains(item),
                            onCheckedChange = { isChecked ->
                                if (isChecked) tempSelected.add(item) else tempSelected.remove(item)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { saveAndDismiss() }) { Text("Done") }
                }
            }
        }
    }
}

// --- REORDERABLE COMPONENTS ---

@Composable
fun <T> ReorderableList(
    items: List<T>,
    onMove: (Int, Int) -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
            val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")
            val translationY = if (isDragging) dragOffset else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        this.translationY = translationY
                        this.scaleX = scale
                        this.scaleY = scale
                        this.shadowElevation = elevation.toPx()
                    }
                    .onGloballyPositioned { coordinates ->
                        if (itemHeightPx == 0f) itemHeightPx = coordinates.size.height.toFloat()
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(item)
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val dist = dragOffset
                                        val threshold = itemHeightPx * 0.7f
                                        if (dist > threshold) {
                                            val nextIndex = index + 1
                                            if (nextIndex < items.size) {
                                                onMove(index, nextIndex)
                                                draggingIndex = nextIndex
                                                dragOffset -= itemHeightPx
                                            }
                                        } else if (dist < -threshold) {
                                            val prevIndex = index - 1
                                            if (prevIndex >= 0) {
                                                onMove(index, prevIndex)
                                                draggingIndex = prevIndex
                                                dragOffset += itemHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                                    onDragCancel = { draggingIndex = null; dragOffset = 0f }
                                )
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ReorderableTile(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = isChecked,
                    onClick = { onCheckedChange(!isChecked) }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// --- SUB SCREENS ---

@Composable
private fun RadarDisplaySettings(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "Radar View") {
            // Label updates dynamically based on Distance Unit
            val unitLabel = if (uiState.distanceUnits == AppSettings.DistanceUnits.METRIC) "km" else "nm"

            CompactNumberInput(
                label = "Radar Range ($unitLabel)",
                value = uiState.radarRange,
                onValueChange = viewModel::onRadarRangeChange
            )

            CompactSwitchRow(
                text = if (uiState.isNorthUp) "Map Orientation: North Up" else "Map Orientation: Track Up",
                checked = uiState.isNorthUp,
                onCheckedChange = viewModel::onNorthUpChange
            )

            CompactSwitchRow(
                text = "Show Ownship",
                checked = uiState.showOwnship,
                onCheckedChange = viewModel::onShowOwnshipChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsCard(title = "Trails") {
            CompactSwitchRow(
                text = "Show Aircraft Trails",
                checked = uiState.showAircraftTrails,
                onCheckedChange = viewModel::onShowAircraftTrailsChange
            )

            if (uiState.showAircraftTrails) {
                CompactNumberInput(
                    label = "Trail Length (sec)",
                    value = uiState.trailLength,
                    onValueChange = viewModel::onTrailLengthChange
                )
            }
        }
    }
}

@Composable
private fun AirportSettingsScreen(uiState: SettingsUiState, viewModel: SettingsViewModel, scrollState: androidx.compose.foundation.ScrollState) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "Airport Display") {
            CompactSwitchRow(
                text = "Show Airports",
                checked = uiState.showAirports,
                onCheckedChange = viewModel::onShowAirportsChange
            )

            // Keep as km unless specific conversion logic is added to repo/viewModel for preload
            CompactNumberInput(
                label = "Preload Radius (km)",
                value = uiState.airportPreloadRadius,
                onValueChange = viewModel::onAirportPreloadRadiusChange
            )

            Text("Label Format", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            CompactRadioGroup(
                options = AirfieldDisplayFormat.values().associate { it to it.name },
                selectedOption = uiState.airfieldDisplayFormat,
                onOptionSelected = viewModel::onAirfieldFormatChange
            )
        }
        LoadedAirportsCard(viewModel)
    }
}

@Composable
private fun FilterSettingsScreen(uiState: SettingsUiState, viewModel: SettingsViewModel, scrollState: androidx.compose.foundation.ScrollState) {
    // Dynamic Labels
    val altLabel = if (uiState.altitudeUnits == AppSettings.AltitudeUnits.METERS) "m" else "ft"
    val spdLabel = if (uiState.distanceUnits == AppSettings.DistanceUnits.METRIC) "km/h" else "kts"

    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "Altitude Filter") {
            CompactSwitchRow(text = "Enable Altitude Filter", checked = uiState.altitudeFilterEnabled, onCheckedChange = viewModel::onAltitudeFilterEnabledChange)
            if (uiState.altitudeFilterEnabled) {
                CompactNumberInput(label = "Min Altitude ($altLabel)", value = uiState.minAltitudeFilter, onValueChange = viewModel::onMinAltitudeFilterChange)
                CompactNumberInput(label = "Max Altitude ($altLabel)", value = uiState.maxAltitudeFilter, onValueChange = viewModel::onMaxAltitudeFilterChange)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(title = "Speed Filter") {
            CompactSwitchRow(text = "Enable Speed Filter", checked = uiState.speedFilterEnabled, onCheckedChange = viewModel::onSpeedFilterEnabledChange)
            if (uiState.speedFilterEnabled) {
                CompactNumberInput(label = "Min Speed ($spdLabel)", value = uiState.minSpeedFilter, onValueChange = viewModel::onMinSpeedFilterChange)
                CompactNumberInput(label = "Max Speed ($spdLabel)", value = uiState.maxSpeedFilter, onValueChange = viewModel::onMaxSpeedFilterChange)
            }
            CompactSwitchRow(text = "Filter Gliders Only (Debug)", checked = uiState.filterGlidersOnly, onCheckedChange = viewModel::onFilterGlidersOnlyChange)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(title = "Elevation Filter") {
            CompactSwitchRow(
                text = "Enable Elevation Filter",
                checked = uiState.elevationFilterEnabled,
                onCheckedChange = viewModel::onElevationFilterEnabledChange
            )
            if (uiState.elevationFilterEnabled) {
                CompactNumberInput(
                    label = "Min Angle Above Horizon (deg)",
                    value = uiState.minElevationFilter,
                    onValueChange = viewModel::onMinElevationFilterChange
                )
                Text(
                    "Hides aircraft low on the horizon (blocked by terrain). Typical values: 2-5°.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DataSettingsScreen(uiState: SettingsUiState, viewModel: SettingsViewModel, scrollState: androidx.compose.foundation.ScrollState) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "API Connection") {
            CompactNumberInput(label = "Request Range (nm)", value = uiState.apiSearchRange, onValueChange = viewModel::onApiSearchRangeChange)
            CompactNumberInput(label = "Polling Interval (sec)", value = uiState.apiPollingInterval, onValueChange = viewModel::onApiPollingIntervalChange)
            Text(
                "Request range determines the size of the box requested from the server. Polling interval sets how often data is refreshed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnitsSettingsScreen(uiState: SettingsUiState, viewModel: SettingsViewModel, scrollState: androidx.compose.foundation.ScrollState) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "Distance Units") {
            CompactRadioGroup(
                options = AppSettings.DistanceUnits.values().associate { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                selectedOption = uiState.distanceUnits,
                onOptionSelected = viewModel::onDistanceUnitsChange
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(title = "Altitude Units") {
            CompactRadioGroup(
                options = AppSettings.AltitudeUnits.values().associate { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                selectedOption = uiState.altitudeUnits,
                onOptionSelected = viewModel::onAltitudeUnitsChange
            )
        }
    }
}

@Composable
private fun SystemSettingsScreen(uiState: SettingsUiState, viewModel: SettingsViewModel, scrollState: androidx.compose.foundation.ScrollState) {
    Column(modifier = Modifier.verticalScroll(scrollState).padding(vertical = 8.dp)) {
        SettingsCard(title = "System Features") {
            CompactSwitchRow(text = "GPS Simulation Mode", checked = uiState.isGpsSimulationEnabled, onCheckedChange = viewModel::onGpsSimulationChange)
            CompactSwitchRow(text = "Background Notification", checked = uiState.showNotification, onCheckedChange = viewModel::onShowNotificationChange)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(title = "Danger Zone") {
            Button(
                onClick = viewModel::resetToDefaults,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset All Settings to Defaults", color = MaterialTheme.colorScheme.onError)
            }
            Text(
                "This will revert all settings, including units and filters, to their factory defaults. This action cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// --- REUSED COMPONENTS (Unchanged) ---

@Composable
fun MenuButton(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun LoadedAirportsCard(viewModel: SettingsViewModel) {
    val loadedAirports by viewModel.loadedAirports.collectAsState()
    val isLoading by viewModel.isLoadingAirports.collectAsState()

    SettingsCard(title = "Loaded Airports") {
        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (loadedAirports.isEmpty()) {
                Text("No airports loaded or check Preload Radius.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    loadedAirports.forEachIndexed { index, airport ->
                        AirportListItem(airport = airport, isLast = index == loadedAirports.size - 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.refreshLoadedAirports() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh Airport List")
            }
        }
    }
}

@Composable
fun AirportListItem(airport: AirportInfo, isLast: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(airport.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${airport.icao ?: ""} ${if (airport.icao != null && airport.iata != null) "•" else ""} ${airport.iata ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            Text("${String.format("%.1f", airport.distanceKm)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (!isLast) Divider(Modifier.padding(top = 8.dp))
    }
}

@Composable
fun CompactNumberInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            // Use Decimal keyboard to allow float values (e.g. "2.5" km)
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
fun CompactSwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
            selected = checked,
            onClick = { onCheckedChange(!checked) },
            role = Role.Switch
        ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun <T> CompactRadioGroup(options: Map<T, String>, selectedOption: T, onOptionSelected: (T) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        options.forEach { (option, label) ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).selectable(
                    selected = (option == selectedOption),
                    onClick = { onOptionSelected(option) },
                    role = Role.RadioButton
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = null
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}