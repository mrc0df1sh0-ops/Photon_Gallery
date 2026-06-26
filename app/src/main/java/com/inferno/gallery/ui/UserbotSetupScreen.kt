@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.inferno.gallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.network.UserbotProvider
import kotlinx.coroutines.launch

// ── Telegram Brand Colors ──
private val TelegramBlue = Color(0xFF0088CC)
private val TelegramBlueDark = Color(0xFF006699)
private val TelegramBlueLight = Color(0xFF29B6F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserbotSetupScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userbotProvider = remember { UserbotProvider.getInstance(context) }
    val authState by userbotProvider.authState.collectAsState()
    val userName by userbotProvider.userName.collectAsState()
    val settings = remember { SettingsRepository.getInstance(context) }

    // Initialize TDLib client
    LaunchedEffect(Unit) {
        userbotProvider.initialize()
    }

    // Compute current step index for progress rail
    val currentStep = when (authState) {
        is UserbotProvider.AuthState.Idle, is UserbotProvider.AuthState.LoggingOut -> -1
        is UserbotProvider.AuthState.WaitPhone -> 0
        is UserbotProvider.AuthState.WaitCode -> 1
        is UserbotProvider.AuthState.WaitPassword -> 2
        is UserbotProvider.AuthState.Ready -> 3
        is UserbotProvider.AuthState.Error -> -2
        is UserbotProvider.AuthState.LoggedOut -> -3
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Header with Telegram branding ──
            if (currentStep >= 0) {
                TelegramHeader()
                Spacer(Modifier.height(8.dp))
            }

            // ── Main Content ──
            if (currentStep >= 0 && currentStep < 3) {
                // Horizontal step indicator
                HorizontalStepIndicator(
                    currentStep = currentStep,
                    totalSteps = 3,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Step content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 3 } + fadeOut())
                    },
                    label = "stepContent",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) { step ->
                    when (step) {
                        0 -> PhoneInputStep(
                            onSubmit = { phone -> userbotProvider.sendPhoneNumber(phone) }
                        )
                        1 -> CodeInputStep(
                            onSubmit = { code -> userbotProvider.sendCode(code) }
                        )
                        2 -> PasswordInputStep(
                            onSubmit = { password -> userbotProvider.sendPassword(password) }
                        )
                    }
                }
            } else {
                // Non-step states (Idle, Ready, Error, LoggingOut)
                AnimatedContent(
                    targetState = authState,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.95f))
                            .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessHigh)))
                    },
                    label = "nonStepContent",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    when (state) {
                        is UserbotProvider.AuthState.Idle, is UserbotProvider.AuthState.LoggingOut -> {
                            InitializingState(
                                message = if (state is UserbotProvider.AuthState.LoggingOut) "Logging out…" else "Connecting to Telegram…"
                            )
                        }
                        is UserbotProvider.AuthState.Ready -> {
                            ConnectedState(
                                userName = userName,
                                userbotProvider = userbotProvider,
                                settings = settings,
                                onLogout = { userbotProvider.logout() },
                                onDone = onBackClick
                            )
                        }
                        is UserbotProvider.AuthState.Error -> {
                            ErrorState(
                                message = state.message,
                                onRetry = { scope.launch { userbotProvider.initialize() } }
                            )
                        }
                        is UserbotProvider.AuthState.LoggedOut -> {
                            LoggedOutState(onDone = onBackClick)
                        }
                        else -> {} // Handled by step content above
                    }
                }
            }
        }
    }
}

// ── Telegram Header ──

@Composable
private fun TelegramHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Telegram logo badge
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(TelegramBlue, TelegramBlueLight)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Telegram",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Telegram Login",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        )
        Text(
            "Connect your account for cloud backup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Horizontal Step Indicator ──

@Composable
private fun HorizontalStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val stepLabels = listOf("Phone", "Code", "Password")
    val stepIcons = listOf(Icons.Rounded.Phone, Icons.Rounded.Sms, Icons.Rounded.Lock)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val isCompleted = i < currentStep

            val dotScale by animateFloatAsState(
                targetValue = if (isActive) 1.1f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotScale$i"
            )

            // Step circle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isActive -> TelegramBlue
                        isCompleted -> TelegramBlue.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .scale(dotScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCompleted) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    stepLabels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isActive -> TelegramBlue
                        isCompleted -> TelegramBlue.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Connector line
            if (i < totalSteps - 1) {
                val lineColor by animateColorAsState(
                    targetValue = if (i < currentStep) TelegramBlue.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "lineColor$i"
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp) // offset for label below dots
                        .width(48.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(lineColor)
                )
            }
        }
    }
}

// ── Initializing ──

@Composable
private fun InitializingState(message: String = "Connecting to Telegram…") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated Telegram logo
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                TelegramBlue.copy(alpha = pulseAlpha * 0.3f),
                                TelegramBlueLight.copy(alpha = pulseAlpha * 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = TelegramBlue.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            androidx.compose.material3.ContainedLoadingIndicator(
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Step Card Wrapper ──

@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Step info
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TelegramBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = TelegramBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                content()
            }
        }
    }
}

// ── Phone Input ──

@Composable
private fun PhoneInputStep(onSubmit: (String) -> Unit) {
    var phone by remember { mutableStateOf("+") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    StepCard(
        icon = Icons.Rounded.Phone,
        title = "Enter your phone number",
        subtitle = "We'll send a login code to your Telegram app"
    ) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") },
            placeholder = { Text("+91 98765 43210") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (phone.length > 3) onSubmit(phone.replace(" ", "")) }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelegramBlue,
                cursorColor = TelegramBlue,
                focusedLabelColor = TelegramBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(phone.replace(" ", "")) },
            enabled = phone.length > 3,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Send Code", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        // Info note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TelegramBlue.copy(alpha = 0.06f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = TelegramBlue,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Include country code (e.g. +91). The code will appear in your Telegram app, not via SMS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Code Input ──

@Composable
private fun CodeInputStep(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    StepCard(
        icon = Icons.Rounded.Sms,
        title = "Enter the verification code",
        subtitle = "Check your Telegram app for the login code"
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            label = { Text("Verification code") },
            placeholder = { Text("12345") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.length >= 5) onSubmit(code) }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelegramBlue,
                cursorColor = TelegramBlue,
                focusedLabelColor = TelegramBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(code) },
            enabled = code.length >= 5,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Verify", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Password Input (2FA) ──

@Composable
private fun PasswordInputStep(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    StepCard(
        icon = Icons.Rounded.Lock,
        title = "Two-Step Verification",
        subtitle = "Enter your cloud password to continue"
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (password.isNotBlank()) onSubmit(password) }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelegramBlue,
                cursorColor = TelegramBlue,
                focusedLabelColor = TelegramBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(password) },
            enabled = password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Submit", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "This is your cloud password, not the verification code. If you forgot it, you can reset it from the official Telegram app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Connected State ──

@Composable
private fun ConnectedState(
    userName: String,
    userbotProvider: UserbotProvider,
    settings: SettingsRepository,
    onLogout: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<UserbotProvider.UserbotChat>>(emptyList()) }
    var isLoadingChats by remember { mutableStateOf(true) }
    var selectedChatId by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        userbotProvider.getPrivateChannels { chats ->
            channels = chats
            isLoadingChats = false
        }
    }

    // Success animation
    val checkScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Telegram-style success: Blue circle with user initial
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(checkScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(TelegramBlueLight, TelegramBlue)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val initial = userName.firstOrNull()?.uppercase() ?: "✓"
            Text(
                initial,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Connected!",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        )
        Text(
            userName,
            style = MaterialTheme.typography.bodyMedium,
            color = TelegramBlue
        )

        Spacer(Modifier.height(24.dp))

        // Channel picker
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Backup destination",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "Choose where to store your photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                if (isLoadingChats) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.ContainedLoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else if (channels.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "No channels found. Create a private Telegram channel first, then come back.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        items(channels) { chat ->
                            val isSelected = selectedChatId == chat.id
                            Surface(
                                onClick = { selectedChatId = chat.id },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected)
                                    TelegramBlue.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isSelected) Modifier.border(
                                            1.5.dp,
                                            TelegramBlue.copy(alpha = 0.5f),
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Channel avatar
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (chat.type == "saved") TelegramBlue.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (chat.type == "saved") Icons.Rounded.Bookmark
                                            else Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = if (isSelected) TelegramBlue
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            chat.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isSelected) TelegramBlue
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            if (chat.type == "saved") "Saved Messages"
                                            else "Private channel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = TelegramBlue,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Button(
            onClick = {
                scope.launch {
                    settings.updateTelegramBackupMode("userbot")
                    settings.updateTelegramUserbotChatId(selectedChatId)
                    userbotProvider.setTargetChatId(selectedChatId)
                    onDone()
                }
            },
            enabled = selectedChatId != 0L,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enable Cloud Backup", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Disconnect Account",
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ── Error State ──

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Connection Failed",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelegramBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Logged Out State ──

@Composable
private fun LoggedOutState(onDone: () -> Unit) {
    // Auto-navigate back after a brief delay
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onDone()
    }

    val checkScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "loggedOutScale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(checkScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Disconnected",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your Telegram account has been unlinked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onDone) {
                Text("Go Back", color = TelegramBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
