package uz.bekobod.faolmfy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.bekobod.faolmfy.ui.screens.ActivationScreen
import uz.bekobod.faolmfy.ui.screens.HomeScreen
import uz.bekobod.faolmfy.ui.screens.PendingApprovalScreen
import uz.bekobod.faolmfy.ui.screens.SetupWizardScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val snackbarHost = remember { SnackbarHostState() }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHost) },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    val state by vm.state.collectAsState()
                    val regions by vm.regions.collectAsState()
                    val districts by vm.districts.collectAsState()
                    val mfys by vm.mfys.collectAsState()
                    val jobs by vm.jobs.collectAsState()

                    Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (state.screen) {
                        Screen.LOADING -> Box(
                            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        Screen.ACTIVATION -> ActivationScreen(
                            regions = regions,
                            districts = districts,
                            mfys = mfys,
                            jobs = jobs,
                            loading = state.loading,
                            error = state.error,
                            onRegionSelected = { vm.loadDistricts(it) },
                            onDistrictSelected = { vm.loadMfys(it) },
                            onSubmit = { code, fio, phone, org, job ->
                                vm.activate(code, fio, phone, org, job)
                            },
                        )

                        Screen.PENDING -> PendingApprovalScreen()

                        Screen.WIZARD -> SetupWizardScreen(
                            onReportPerms = { f, b, n, a, bat, auto ->
                                vm.reportPerms(f, b, n, a, bat, auto)
                            },
                            onFinish = { vm.finishWizard() },
                        )

                        Screen.HOME -> {
                            // Rasm/izoh saqlanganda qisqa xabar ko'rsatamiz
                            state.toast?.let { msg ->
                                LaunchedEffect(msg) {
                                    snackbarHost.showSnackbar(msg)
                                    vm.clearToast()
                                }
                            }
                            HomeScreen(
                                profile = state.profile,
                                day = state.today,
                                pendingPoints = state.pendingPoints,
                                pendingAttachments = state.pendingAttachments,
                                photosByStop = state.photosByStop,
                                notesByAnchor = state.notesByAnchor,
                                trackingActive = state.trackingActive,
                                isWorkday = state.isWorkday,
                                loading = state.loading,
                                onRefresh = { vm.refreshToday() },
                                onSync = { vm.syncNow() },
                                onPhotoTaken = { file, anchor -> vm.onPhotoTaken(file, anchor) },
                                onNoteSaved = { anchor, note -> vm.onNoteSaved(anchor, note) },
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshConfig()
    }
}
