package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.VolatileMemoryResetScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ObdViewModel
import com.example.viewmodel.SequenceViewModel

enum class AppDestination {
    MAIN_MENU,
    MANUAL_TPS,
    VOLATILE_RESET
}

class MainActivity : ComponentActivity() {

    private val sequenceViewModel: SequenceViewModel by viewModels()
    private val obdViewModel: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppDestination.MAIN_MENU) }

                when (currentScreen) {
                    AppDestination.MAIN_MENU -> {
                        MainMenuScreen(
                            onNavigateToManualTps = {
                                currentScreen = AppDestination.MANUAL_TPS
                            },
                            onNavigateToVolatileReset = {
                                currentScreen = AppDestination.VOLATILE_RESET
                            }
                        )
                    }

                    AppDestination.MANUAL_TPS -> {
                        HomeScreen(
                            viewModel = sequenceViewModel,
                            onBack = {
                                currentScreen = AppDestination.MAIN_MENU
                            }
                        )
                    }

                    AppDestination.VOLATILE_RESET -> {
                        VolatileMemoryResetScreen(
                            viewModel = obdViewModel,
                            onBack = {
                                currentScreen = AppDestination.MAIN_MENU
                            }
                        )
                    }
                }
            }
        }
    }
}
