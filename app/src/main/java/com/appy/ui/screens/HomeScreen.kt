package com.appy.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

/**
 * Status bar style options for generated APK
 */
enum class StatusBarStyle(val displayName: String) {
    LIGHT("Light (dark icons)"),
    DARK("Dark (light icons)")
}

/**
 * Data class for APK configuration
 */
data class ApkConfig(
    val url: String,
    val appName: String,
    val packageId: String,
    val iconUri: Uri?,
    val statusBarStyle: StatusBarStyle = StatusBarStyle.LIGHT,
    val enableOfflineCache: Boolean = false
)

/**
 * Build state for tracking APK generation progress
 */
sealed class BuildState {
    data object Idle : BuildState()
    data class Building(val progress: Float, val message: String) : BuildState()
    data class ReadyToSave(val tempFilePath: String, val suggestedFileName: String) : BuildState()
    data object Success : BuildState()
    data class Error(val message: String) : BuildState()
}

/**
 * Maximum package ID length - must be <= template package ID length
 * Template uses "com.appy.generated.webapp.placeholder.app" (44 chars)
 */
private const val MAX_PACKAGE_ID_LENGTH = 44

/**
 * Maximum app name length - must be <= template app name length
 * Template uses "AppyGeneratedWebApplicationPlaceholderNameHere" (46 chars)
 */
private const val MAX_APP_NAME_LENGTH = 46

/**
 * Validates if a package ID follows Android naming conventions
 */
private fun isValidPackageId(packageId: String): Boolean {
    if (packageId.isBlank()) return false
    // Package ID must not exceed maximum length
    if (packageId.length > MAX_PACKAGE_ID_LENGTH) return false
    // Package ID must have at least two parts separated by dots
    val parts = packageId.split(".")
    if (parts.size < 2) return false
    // Each part must start with a letter and contain only letters, digits, and underscores
    val partPattern = Regex("^[a-z][a-z0-9_]*$")
    return parts.all { it.isNotEmpty() && partPattern.matches(it) }
}

/**
 * Validates if an app name is valid
 */
private fun isValidAppName(appName: String): Boolean {
    if (appName.isBlank()) return false
    if (appName.length > MAX_APP_NAME_LENGTH) return false
    return true
}

/**
 * Home screen implementing Material 3 Expressive design principles
 * Features:
 * - Glass-effect TopAppBar with blur
 * - Extra-large corner radii (32dp) for main input container
 * - Emphasized easing for transitions
 * - LinearProgressIndicator that morphs to success checkmark
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    buildState: BuildState = BuildState.Idle,
    onBuildClick: (ApkConfig) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var url by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var packageId by remember { mutableStateOf("com.appy.app") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var statusBarStyle by remember { mutableStateOf(StatusBarStyle.LIGHT) }
    var statusBarDropdownExpanded by remember { mutableStateOf(false) }
    var enableOfflineCache by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    
    // Validation states
    val isPackageIdValid = isValidPackageId(packageId)
    val isAppNameValid = isValidAppName(appName)

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        iconUri = uri
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // "Appy" branding with Display typography
                    Text(
                        text = "Appy",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Headline text
            Text(
                text = "Turn any website into an APK",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Main URL input container with extra-large corner radius (32dp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge, // 32dp corners
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // URL Input
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Website URL") },
                        placeholder = { Text("https://example.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "URL"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.large
                    )

                    // App Name Input
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("App Name") },
                        placeholder = { Text("My App") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Label,
                                contentDescription = "App Name"
                            )
                        },
                        singleLine = true,
                        isError = appName.isNotBlank() && !isAppNameValid,
                        supportingText = if (appName.isNotBlank() && !isAppNameValid) {
                            { 
                                Text("App name too long (max $MAX_APP_NAME_LENGTH chars)") 
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.large
                    )

                    // Package ID Input
                    OutlinedTextField(
                        value = packageId,
                        onValueChange = { packageId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package ID") },
                        placeholder = { Text("com.example.app") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Package ID"
                            )
                        },
                        singleLine = true,
                        isError = packageId.isNotBlank() && !isPackageIdValid,
                        supportingText = if (packageId.isNotBlank() && !isPackageIdValid) {
                            { 
                                Text(
                                    if (packageId.length > MAX_PACKAGE_ID_LENGTH) 
                                        "Package ID too long (max $MAX_PACKAGE_ID_LENGTH chars)"
                                    else 
                                        "Use lowercase letters, numbers, underscores (e.g., com.example.app)"
                                ) 
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.large
                    )

                    // Icon Picker
                    Text(
                        text = "App Icon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(iconUri),
                                contentDescription = "Selected icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add icon",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Status Bar Style Dropdown
                    Text(
                        text = "Status Bar Style",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = statusBarDropdownExpanded,
                        onExpandedChange = { statusBarDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = statusBarStyle.displayName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusBarDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.large
                        )
                        ExposedDropdownMenu(
                            expanded = statusBarDropdownExpanded,
                            onDismissRequest = { statusBarDropdownExpanded = false }
                        ) {
                            StatusBarStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.displayName) },
                                    onClick = {
                                        statusBarStyle = style
                                        statusBarDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Offline Cache Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enableOfflineCache = !enableOfflineCache }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Offline Cache",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Experimental",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Cache pages for offline use. May not work on all websites.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = enableOfflineCache,
                            onCheckedChange = { enableOfflineCache = it }
                        )
                    }

                    // Info note about customization
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Your custom app name, package ID, and icon will be applied to the generated APK. Each unique package ID allows a separate app installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Primary Compound Action Button for Build
                    PrimaryCompoundActionButton(
                        text = "Build APK",
                        enabled = url.isNotBlank() && isAppNameValid && isPackageIdValid && 
                                  (buildState is BuildState.Idle || buildState is BuildState.Success || buildState is BuildState.Error),
                        onClick = {
                            onBuildClick(
                                ApkConfig(
                                    url = url,
                                    appName = appName,
                                    packageId = packageId,
                                    iconUri = iconUri,
                                    statusBarStyle = statusBarStyle,
                                    enableOfflineCache = enableOfflineCache
                                )
                            )
                        }
                    )
                }
            }

            // Progress/Status section with morphing indicator
            BuildStatusSection(buildState = buildState)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Primary Compound Action Button following M3 Expressive guidelines
 * Combines icon and text in a prominent action button
 */
@Composable
fun PrimaryCompoundActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

/**
 * Build status section that shows progress or success state
 * Features morphing LinearProgressIndicator to success checkmark
 */
@Composable
fun BuildStatusSection(buildState: BuildState) {
    // Emphasized easing for M3 Expressive transitions
    val emphasizedEasing = EaseInOutCubic

    AnimatedContent(
        targetState = buildState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(250, easing = emphasizedEasing)) +
                scaleIn(animationSpec = tween(250, easing = emphasizedEasing), initialScale = 0.95f))
                .togetherWith(
                    fadeOut(animationSpec = tween(250, easing = emphasizedEasing)) +
                        scaleOut(animationSpec = tween(250, easing = emphasizedEasing), targetScale = 0.95f)
                )
        },
        label = "buildStateTransition"
    ) { state ->
        when (state) {
            is BuildState.Idle -> {
                // Empty state
                Spacer(modifier = Modifier.height(48.dp))
            }
            is BuildState.Building -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        animationSpec = tween(250, easing = emphasizedEasing),
                        label = "progressAnimation"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is BuildState.ReadyToSave -> {
                // Waiting for user to select save location
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        text = "Choose where to save your APK",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            is BuildState.Success -> {
                // Success checkmark (morphed from progress indicator)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "APK Generated Successfully!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is BuildState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
