package io.github.aomizuki0307.petmed.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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

    val start = if (state.household == null) Routes.ONBOARDING else Routes.TODAY

    NavHost(navController = navController, startDestination = start) {
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
                        // 初回登録フロー: ペット→薬登録へ
                        navController.navigate(Routes.medEdit(savedPetId)) { popUpTo(Routes.TODAY) }
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
