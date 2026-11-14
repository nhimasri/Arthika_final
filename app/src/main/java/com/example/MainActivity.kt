package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.entity.TransactionEntity
import com.example.di.ArthikaViewModelFactory
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.presentation.MainViewModel
import com.example.presentation.budgets.BudgetsScreen
import com.example.presentation.components.AddEditTransactionSheet
import com.example.presentation.components.NotificationsBottomSheet
import com.example.presentation.components.ReviewDetectionBottomSheet
import com.example.presentation.components.SmsImportBottomSheet
import com.example.presentation.components.WhatIfSimulatorBottomSheet
import com.example.presentation.goals.GoalsScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.navigation.BudgetsRoute
import com.example.presentation.navigation.GoalsRoute
import com.example.presentation.navigation.HomeRoute
import com.example.presentation.navigation.ProfileRoute
import com.example.presentation.navigation.TransactionsRoute
import com.example.presentation.onboarding.OnboardingScreen
import com.example.presentation.profile.ProfileScreen
import com.example.presentation.transactions.TransactionsScreen
import com.example.ui.theme.ArthikaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ArthikaApplication
        val factory = ArthikaViewModelFactory(app.container)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.ensureMonthRolloverSynced()
            }

            ArthikaTheme(themePreference = uiState.preferences.themePreference) {
                val rootStage = when {
                    !uiState.isLoaded -> 0
                    !uiState.preferences.onboardingCompleted -> 1
                    else -> 2
                }
                AnimatedContent(
                    targetState = rootStage,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "root_stage_transition"
                ) { stage ->
                    when (stage) {
                        0 -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        1 -> {
                            OnboardingScreen(
                                onFinishOnboarding = { name, income, fixed, savings, smsEnabled, scanInbox ->
                                    viewModel.completeOnboarding(
                                        name = name,
                                        monthlyIncome = income,
                                        fixedExpenses = fixed,
                                        protectedSavings = savings,
                                        enableSms = smsEnabled,
                                        scanInboxIfPermitted = scanInbox
                                    )
                                }
                            )
                        }

                        else -> {
                            ArthikaMainShell(viewModel = viewModel, uiState = uiState)
                        }
                    }
                }
            }
        }
    }
}

private data class NavDestinationItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun ArthikaMainShell(
    viewModel: MainViewModel,
    uiState: com.example.presentation.ArthikaUiState
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var reviewingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddSheetWithType by remember { mutableStateOf<TransactionType?>(null) }
    var showSmsImportSheet by remember { mutableStateOf(false) }
    var showWhatIfSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(HomeRoute, "Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
            NavDestinationItem(TransactionsRoute, "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_transactions"),
            NavDestinationItem(BudgetsRoute, "Budgets", Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_budgets"),
            NavDestinationItem(GoalsRoute, "Goals", Icons.Rounded.TrackChanges, Icons.Outlined.TrackChanges, "nav_goals"),
            NavDestinationItem(ProfileRoute, "Profile", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // CRITICAL: Only NEEDS_REVIEW transactions open the SMS review interface.
    // Confirmed SMS and manual transactions both open the normal editing sheet.
    val onTransactionSelected: (TransactionEntity) -> Unit = { txn ->
        if (txn.status == TransactionStatus.NEEDS_REVIEW) {
            reviewingTransaction = txn
        } else {
            editingTransaction = txn
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        val showFab = currentDestination?.hasRoute(HomeRoute::class) == true ||
            currentDestination?.hasRoute(TransactionsRoute::class) == true

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = { showAddSheetWithType = TransactionType.DEBIT },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_add_transaction")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            letterSpacing = (-0.2).sp
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    enterTransition = {
                        fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(200))
                    },
                    exitTransition = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(200))
                    },
                    popExitTransition = { fadeOut(animationSpec = tween(150)) },
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            uiState = uiState,
                            onNavigateToTransactions = { navigateToTab(TransactionsRoute) },
                            onNavigateToBudgets = { navigateToTab(BudgetsRoute) },
                            onNavigateToGoals = { navigateToTab(GoalsRoute) },
                            onNavigateToProfile = { navigateToTab(ProfileRoute) },
                            onOpenAddTransaction = { type -> showAddSheetWithType = type },
                            onOpenWhatIf = { showWhatIfSheet = true },
                            onOpenSmsImport = { showSmsImportSheet = true },
                            onOpenNotifications = { showNotificationsSheet = true },
                            onTransactionClick = onTransactionSelected
                        )
                    }
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            uiState = uiState,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSelectFilterChip = viewModel::selectFilterChip,
                            onSelectCategoryFilter = viewModel::selectCategoryFilter,
                            onSelectPaymentMethodFilter = viewModel::selectPaymentMethodFilter,
                            onClearExtraFilters = viewModel::clearExtraFilters,
                            onOpenSmsImport = { showSmsImportSheet = true },
                            onScanInbox = viewModel::scanSmsInbox,
                            onTransactionClick = onTransactionSelected
                        )
                    }
                    composable<BudgetsRoute> {
                        BudgetsScreen(
                            uiState = uiState,
                            onUpdateFinancialProfile = viewModel::updateFinancialProfile,
                            onUpdateCategoryBudget = viewModel::updateCategoryBudget,
                            onCreateCategory = viewModel::createCategory,
                            onRenameCategory = viewModel::renameCategory,
                            onDeleteCategory = viewModel::deleteCategory,
                            onAddRecurringPayment = viewModel::addRecurringPayment,
                            onPayRecurringBill = viewModel::recordRecurringBillPayment,
                            onUnmarkRecurringBill = viewModel::unmarkRecurringBillPaid,
                            onDeleteRecurringPayment = viewModel::deleteRecurringPayment,
                            onOpenWhatIf = { showWhatIfSheet = true }
                        )
                    }
                    composable<GoalsRoute> {
                        GoalsScreen(
                            uiState = uiState,
                            onUpsertGoal = viewModel::upsertGoal,
                            onContributeGoal = viewModel::contributeToGoal,
                            onUpdateContribution = viewModel::updateGoalContribution,
                            onDeleteContribution = viewModel::deleteGoalContribution,
                            onDeleteGoal = viewModel::deleteGoal,
                            onCheckInChallenge = viewModel::checkInNoSpendChallenge,
                            onAddChallenge = viewModel::addNoSpendChallenge
                        )
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            uiState = uiState,
                            onUpdateUserName = viewModel::updateUserName,
                            onToggleSmsDetection = viewModel::setSmsDetectionEnabled,
                            onOpenSmsImport = { showSmsImportSheet = true },
                            onScanInbox = viewModel::scanSmsInbox,
                            onDeleteAllSmsData = viewModel::deleteAllSmsTransactions,
                            onToggleSpendingRule = viewModel::toggleSpendingRule,
                            onUpdateSinglePaymentLimit = viewModel::setSinglePaymentLimit,
                            onToggleDailyAlerts = viewModel::setDailyAlertsEnabled,
                            onThemeChange = viewModel::setThemePreference,
                            onClearAllData = viewModel::clearAllUserData
                        )
                    }
                }
            }
        }
    }

    reviewingTransaction?.let { txn ->
        ReviewDetectionBottomSheet(
            transaction = txn,
            categories = uiState.categories,
            onConfirm = { merchant, catId, markTransfer ->
                viewModel.confirmReviewedTransaction(txn, merchant, catId, markTransfer)
                reviewingTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(txn.id)
                reviewingTransaction = null
            },
            onDismiss = { reviewingTransaction = null }
        )
    }

    val sheetTxn = editingTransaction
    val addType = showAddSheetWithType
    if (sheetTxn != null || addType != null) {
        AddEditTransactionSheet(
            existingTransaction = sheetTxn,
            initialType = sheetTxn?.type ?: addType ?: TransactionType.DEBIT,
            categories = uiState.categories,
            summary = uiState.summary,
            singlePaymentLimit = uiState.preferences.singlePaymentLimit,
            onSave = { title, amount, type, catId, method, notes, isTransfer ->
                if (sheetTxn != null) {
                    viewModel.updateTransaction(
                        sheetTxn.copy(
                            title = title,
                            merchantName = title,
                            amount = amount,
                            type = type,
                            categoryId = catId,
                            paymentMethod = method,
                            notes = notes,
                            isTransfer = isTransfer
                        )
                    )
                } else {
                    viewModel.addManualTransaction(
                        title = title,
                        amount = amount,
                        type = type,
                        categoryId = catId,
                        timestamp = System.currentTimeMillis(),
                        paymentMethod = method,
                        notes = notes,
                        isTransfer = isTransfer
                    )
                }
                editingTransaction = null
                showAddSheetWithType = null
            },
            onDelete = sheetTxn?.let { existing ->
                {
                    viewModel.deleteTransaction(existing.id)
                    editingTransaction = null
                }
            },
            onDismiss = {
                editingTransaction = null
                showAddSheetWithType = null
            }
        )
    }

    if (showSmsImportSheet) {
        SmsImportBottomSheet(
            onImportSms = { sender, body -> viewModel.importSmsText(sender, body) },
            onScanInbox = { viewModel.scanSmsInbox() },
            isScanningInbox = uiState.isScanningInbox,
            onDismiss = { showSmsImportSheet = false }
        )
    }

    if (showWhatIfSheet) {
        WhatIfSimulatorBottomSheet(
            result = uiState.whatIfResult,
            onSimulate = { title, amount -> viewModel.simulateWhatIf(title, amount) },
            onDismiss = {
                showWhatIfSheet = false
                viewModel.clearWhatIf()
            }
        )
    }

    if (showNotificationsSheet) {
        NotificationsBottomSheet(
            notifications = uiState.notifications,
            onMarkAllRead = viewModel::markAllNotificationsRead,
            onDismiss = { showNotificationsSheet = false }
        )
    }
}
