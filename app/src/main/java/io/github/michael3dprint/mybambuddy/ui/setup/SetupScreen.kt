package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** First-run screen where the server address and API key are entered and tested. Connects the view model to [SetupContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel, modifier: Modifier = Modifier, onSetupComplete: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SetupContent(
        uiState = uiState,
        onServerUrlChanged = viewModel::onServerUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onToggleApiKeyVisibility = viewModel::toggleApiKeyVisibility,
        onTestConnection = viewModel::testConnection,
        onSaveSettings = {
            viewModel.saveSettings()
            onSetupComplete()
        },
        onNavigateBack = onSetupComplete,
        modifier = modifier,
    )
}

/** Draws the Setup screen from its state; every change is a callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupContent(
    uiState: SetupUiState,
    onServerUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onTestConnection: () -> Unit,
    onSaveSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Heading(
                        // Not "PrintDeck Setup": this configures the server connection for the whole app, not the
                        // print-deck section. "PrintDeck" is a section name and must not stand in as the app title.
                        text = "Server setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.alerts_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = Dimens.Space.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
        ) {
            // Tailscale Hint Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.setup_info),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(Dimens.Space.m))
                    Text(
                        text = stringResource(R.string.setup_make_sure_tailscale_is_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Server URL Input
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = onServerUrlChanged,
                label = { Text(stringResource(R.string.settings_server_url)) },
                supportingText = uiState.urlWarning?.let { warning -> { Text(warning, color = MaterialTheme.colorScheme.error) } },
                placeholder = { Text(stringResource(R.string.setup_e_g_http_100_64)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = stringResource(R.string.settings_server_url),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // API Key Input
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text(stringResource(R.string.settings_api_key_optional)) },
                placeholder = { Text(stringResource(R.string.settings_leave_empty_if_auth_is)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = stringResource(R.string.settings_api_key),
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onToggleApiKeyVisibility) {
                        Icon(
                            imageVector = if (uiState.isApiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (uiState.isApiKeyVisible) "Hide API Key" else "Show API Key",
                        )
                    }
                },
                visualTransformation = if (uiState.isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    },
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Test Connection & Save Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m),
            ) {
                FilledTonalButton(
                    onClick = {
                        keyboardController?.hide()
                        onTestConnection()
                    },
                    enabled = !uiState.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.s))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = stringResource(R.string.settings_test_connection),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.s))
                    }
                    Text(stringResource(R.string.setup_test))
                }

                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSaveSettings()
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.settings_save_settings),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Dimens.Space.s))
                    Text(stringResource(R.string.setup_save))
                }
            }

            // Test Result Banner
            AnimatedVisibility(visible = uiState.testResultMessage != null || uiState.testErrorState != null) {
                if (uiState.testResultSuccess == true && uiState.testResultMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Space.l),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.settings_success),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(Dimens.Space.m))
                            Text(
                                text = uiState.testResultMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else if (uiState.testErrorState != null || uiState.testResultMessage != null) {
                    val errTitle = uiState.testErrorState?.title ?: "Connection Failed"
                    val errMsg = uiState.testErrorState?.message ?: uiState.testResultMessage ?: "Failed to connect to server."
                    val primaryLabel = uiState.testErrorState?.primaryActionLabel ?: "Retry"

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Space.l),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = stringResource(R.string.history_error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(Dimens.Space.m))
                                Heading(
                                    text = errTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(Dimens.Space.m))
                            Button(
                                onClick = onTestConnection,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(text = primaryLabel)
                            }
                            io.github.michael3dprint.mybambuddy.ui.help.HelpButton(
                                uiState.testErrorState?.kind?.let {
                                    io.github.michael3dprint.mybambuddy.domain.help.ErrorHelp.topicFor(it)
                                },
                            )
                        }
                    }
                }
            }

            // Save Confirmation Banner
            AnimatedVisibility(visible = uiState.isSaved && uiState.saveMessage != null) {
                uiState.saveMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Space.l),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.settings_saved),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(Dimens.Space.m))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Space.xl))
        }
    }
}
