package com.minfinrobot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minfinrobot.ui.MainViewModel
import com.minfinrobot.ui.logs.LogsScreen
import com.minfinrobot.ui.main.MainScreen
import com.minfinrobot.ui.settings.SettingsScreen
import com.minfinrobot.ui.test.TestScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MainNavigation() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val robotState by viewModel.robotState.collectAsState()
    val mergedState = state.copy(robotState = robotState)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Главная", "Настройки", "Тест", "Логи")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Минфин-робот") })
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> MainScreen(viewModel, mergedState)
                1 -> SettingsScreen(viewModel, mergedState)
                2 -> TestScreen()
                3 -> LogsScreen(viewModel)
            }
        }
    }
}
