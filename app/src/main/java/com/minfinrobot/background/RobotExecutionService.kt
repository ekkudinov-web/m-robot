package com.minfinrobot.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.minfinrobot.MinfinRobotApp
import com.minfinrobot.data.log.LogStore
import com.minfinrobot.domain.model.InstrumentRef
import com.minfinrobot.domain.model.RobotConfig
import com.minfinrobot.domain.model.RobotRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground-service для долгоиграющего мониторинга.
 */
class RobotExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Робот запущен"))

        if (job?.isActive == true) {
            LogStore.warn("Сервис уже запущен — игнорируем повторный startService")
            return START_NOT_STICKY
        }

        val config = State.pendingConfig
        if (config == null) {
            LogStore.error("Нет конфигурации для запуска")
            stopSelf()
            return START_NOT_STICKY
        }

        job = scope.launch {
            try {
                val app = applicationContext as MinfinRobotApp
                State._currentState.value = RobotRunState.IDLE
                val finalState = app.startRobotUseCase.invoke(
                    config = config,
                    instruments = State.cachedInstruments,
                    onState = { state ->
                        State._currentState.value = state
                        updateNotification(formatState(state))
                    }
                )
                State._currentState.value = finalState
                LogStore.info("Робот завершил работу: $finalState")
            } catch (e: Exception) {
                LogStore.error("Фатальная ошибка робота: ${e.message}")
                State._currentState.value = RobotRunState.ERROR
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Минфин-робот", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Фоновый сервис мониторинга" }
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Минфин-робот")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun formatState(s: RobotRunState): String = when (s) {
        RobotRunState.IDLE -> "Подготовка"
        RobotRunState.WAITING_FOR_WINDOW -> "Ждём начала окна мониторинга"
        RobotRunState.POLLING_LISTING -> "Поллинг сайта Минфина"
        RobotRunState.PUBLICATION_FOUND -> "Публикация найдена, обрабатываем"
        RobotRunState.PLACING_ORDERS -> "Отправляем ордера"
        RobotRunState.DONE_SUCCESS -> "Готово"
        RobotRunState.DONE_PAUSE -> "Готово: Минфин объявил паузу"
        RobotRunState.DONE_WINDOW_EXPIRED -> "Окно закрыто, публикация не появилась"
        RobotRunState.ERROR -> "Завершено с ошибкой"
    }

    object State {
        @Volatile var pendingConfig: RobotConfig? = null
        @Volatile var cachedInstruments: List<InstrumentRef> = emptyList()

        internal val _currentState = MutableStateFlow(RobotRunState.IDLE)
        val currentState: StateFlow<RobotRunState> = _currentState.asStateFlow()
    }

    companion object {
        private const val CHANNEL_ID = "minfin_robot_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, config: RobotConfig, instruments: List<InstrumentRef>) {
            State.pendingConfig = config
            State.cachedInstruments = instruments
            val intent = Intent(context, RobotExecutionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RobotExecutionService::class.java)
            context.stopService(intent)
        }
    }
}
