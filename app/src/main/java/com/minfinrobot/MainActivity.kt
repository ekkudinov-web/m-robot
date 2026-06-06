package com.minfinrobot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minfinrobot.ui.MainViewModel
import com.minfinrobot.ui.cbr.CbrScreen
import com.minfinrobot.ui.logs.LogsScreen
import com.minfinrobot.ui.main.MainScreen
import com.minfinrobot.ui.settings.SettingsScreen
import com.minfinrobot.ui.test.TestScreen
import com.minfinrobot.ui.theme.AppBackground
import com.minfinrobot.ui.theme.AppColors
import com.minfinrobot.ui.theme.MinfinRobotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MinfinRobotTheme { MainNavigation() } }
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
    val tabs = listOf("Минфин", "Робот ЦБ", "Настройки", "Тест", "Логи")

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                "Минфин-робот",
                                color = AppColors.Gold,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = AppColors.Gold
                        )
                    )
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp,
                        containerColor = Color.Transparent,
                        contentColor = AppColors.Gold,
                        indicator = { positions ->
                            Box(
                                Modifier
                                    .tabIndicatorOffset(positions[selectedTab])
                                    .height(3.dp)
                                    .background(AppColors.Gold)
                            )
                        }
                    ) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                selectedContentColor = AppColors.Gold,
                                unselectedContentColor = AppColors.TextSecondary,
                                text = {
                                    Text(
                                        title,
                                        fontWeight = if (selectedTab == i)
                                            FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
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
                    1 -> CbrScreen(viewModel, mergedState)
                    2 -> SettingsScreen(viewModel, mergedState)
                    3 -> TestScreen()
                    4 -> LogsScreen(viewModel)
                }
            }
        }
    }
}
