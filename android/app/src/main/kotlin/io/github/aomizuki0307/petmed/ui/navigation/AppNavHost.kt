package io.github.aomizuki0307.petmed.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.aomizuki0307.petmed.ui.AppViewModel
import io.github.aomizuki0307.petmed.ui.history.HistoryScreen
import io.github.aomizuki0307.petmed.ui.invite.InviteScreen
import io.github.aomizuki0307.petmed.ui.invite.JoinHouseholdScreen
import io.github.aomizuki0307.petmed.ui.meds.MedicationEditScreen
import io.github.aomizuki0307.petmed.ui.onboarding.OnboardingScreen
import io.github.aomizuki0307.petmed.ui.paywall.PaywallScreen
import io.github.aomizuki0307.petmed.ui.pets.PetEditScreen
import io.github.aomizuki0307.petmed.ui.settings.SettingsScreen
import io.github.aomizuki0307.petmed.ui.today.TodayScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val JOIN = "join"
    const val PET_EDIT = "pet_edit?petId={petId}"
    const val MED_EDIT = "med_edit/{petId}?medId={medId}"
    const val TODAY = "today"
    const val HISTORY = "history"
    const val INVITE = "invite"
    const val PAYWALL = "paywall?trigger={trigger}"
    const val SETTINGS = "settings"

    fun petEdit(petId: String? = null) = "pet_edit?petId=${petId ?: ""}"
    fun medEdit(petId: String, medId: String? = null) = "med_edit/$petId?medId=${medId ?: ""}"
    fun paywall(trigger: String) = "paywall?trigger=$trigger"
}

@Composable
fun AppNavHost(viewModel: AppViewModel) {
    val navController: NavHostController = rememberNavController()
    val state by viewModel.uiState.collectAsState()

    // 参加済み世帯の復元待ちの間はオンボーディングを出さない（P1-4:
    // 復元前に「新しく始める」を押すと保存中の世帯IDを新規IDで上書きしてしまう）。
    // 復元が10秒以内に終わらない場合のみオンボーディングへフォールバックする
    var restoreTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(10_000)
        restoreTimedOut = true
    }
    if (viewModel.awaitingRestore && state.household == null && !restoreTimedOut) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // startDestinationは固定（状態で切り替えるとグラフ再生成がnavigateと競合する）。
    // 世帯が読み込まれたらオンボーディングから自動遷移する（prodの再起動復元にも対応）。
    LaunchedEffect(state.household != null) {
        val onOnboarding = navController.currentDestination?.route == Routes.ONBOARDING
        if (state.household != null && onOnboarding) {
            navController.navigate(Routes.TODAY) { popUpTo(0) }
        }
    }

    NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                viewModel = viewModel,
                onCreated = {
                    navController.navigate(Routes.petEdit()) { popUpTo(0) }
                },
                onJoin = { navController.navigate(Routes.JOIN) },
            )
        }
        composable(Routes.JOIN) {
            JoinHouseholdScreen(
                viewModel = viewModel,
                onJoined = { navController.navigate(Routes.TODAY) { popUpTo(0) } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PET_EDIT) { entry ->
            val petId = entry.arguments?.getString("petId")?.ifBlank { null }
            PetEditScreen(
                viewModel = viewModel,
                petId = petId,
                onSaved = { savedPetId ->
                    if (petId == null && viewModel.uiState.value.household?.medications?.none { it.petId == savedPetId } != false) {
                        // 初回登録フロー: ペット→薬登録へ（PET_EDITは戻れないように消す）
                        navController.navigate(Routes.medEdit(savedPetId)) {
                            popUpTo(Routes.PET_EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MED_EDIT) { entry ->
            val petId = entry.arguments?.getString("petId") ?: return@composable
            val medId = entry.arguments?.getString("medId")?.ifBlank { null }
            MedicationEditScreen(
                viewModel = viewModel,
                petId = petId,
                medId = medId,
                onSaved = { navController.navigate(Routes.TODAY) { popUpTo(0) } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TODAY) {
            TodayScreen(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenInvite = { navController.navigate(Routes.INVITE) },
                onAddPet = { navController.navigate(Routes.petEdit()) },
                onAddMed = { petId -> navController.navigate(Routes.medEdit(petId)) },
                onEditMed = { petId, medId -> navController.navigate(Routes.medEdit(petId, medId)) },
                onOpenPaywall = { trigger -> navController.navigate(Routes.paywall(trigger)) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.paywall("history_lock")) },
            )
        }
        composable(Routes.INVITE) {
            InviteScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.paywall("invite")) },
            )
        }
        composable(Routes.PAYWALL) { entry ->
            PaywallScreen(
                viewModel = viewModel,
                trigger = entry.arguments?.getString("trigger") ?: "menu",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenInvite = { navController.navigate(Routes.INVITE) },
                onOpenPaywall = { navController.navigate(Routes.paywall("menu")) },
                onDeleted = { navController.navigate(Routes.ONBOARDING) { popUpTo(0) } },
            )
        }
    }
}
