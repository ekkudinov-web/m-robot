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
import com.minfinrobot.domain.model.CbrConfig
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
 * Foreground-service для мониторинга. Поддерживает два типа роботов:
 *   - MINFIN (StartRobotUseCase)
 *   - CBR    (StartCbrRobotUseCase)
 *
 * Защита "один робот за раз": если сервис активен (job?.isActive == true),
 * повторный start() с любым типом отказывает с уведомлением в лог.
 */
class RobotExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    enum class Mode { MINFIN, CBR }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = State.pendingMode ?: run {
            LogStore.error("Нет конфигурации для запуска")
            stopSelf()
            return START_NOT_STICKY
        }
        val title = when (mode) {
            Mode.MINFIN -> "Минфин-робот"
            Mode.CBR -> "ЦБ-робот"
        }
        startForeground(NOTIFICATION_ID, buildNotification(title, "Запущен"))

        if (job?.isActive == true) {
            LogStore.warn("Сервис уже запущен — игнорируем повторный startService")
            return START_NOT_STICKY
        }

        State.currentMode = mode

        job = scope.launch {
            try {
                val app = applicationContext as MinfinRobotApp
                State._currentState.value = RobotRunState.IDLE
                val finalState = when (mode) {
                    Mode.MINFIN -> {
                        val cfg = State.pendingMinfinConfig ?: error("Нет MinfinConfig")
                        app.startRobotUseCase.invoke(
                            config = cfg,
                            instruments = State.cachedInstruments,
                            onState = { state ->
                                State._currentState.value = state
                                updateNotification(title, formatState(state))
                            }
                        )
                    }
                    Mode.CBR -> {
                        val cfg = State.pendingCbrConfig ?: error("Нет CbrConfig")
                        app.startCbrRobotUseCase.invoke(
                            config = cfg,
                            instruments = State.cachedInstruments,
                            onState = { state ->
                                State._currentState.value = state
                                updateNotification(title, formatState(state))
                            }
                        )
                    }
                }
                State._currentState.value = finalState
                LogStore.info("Робот завершил работу: $finalState")
            } catch (e: Exception) {
                LogStore.error("Фатальная ошибка робота: ${e.message}")
                State._currentState.value = RobotRunState.ERROR
            } finally {
                State.currentMode = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        State.currentMode = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Робот-мониторинг", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Фоновый сервис мониторинга" }
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, status: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(title, status))
    }

    private fun formatState(s: RobotRunState): String = when (s) {
        RobotRunState.IDLE -> "Подготовка"
        RobotRunState.WAITING_FOR_WINDOW -> "Ждём начала окна мониторинга"
        RobotRunState.POLLING_LISTING -> "Поллинг источника"
        RobotRunState.PUBLICATION_FOUND -> "Публикация найдена, обрабатываем"
        RobotRunState.PLACING_ORDERS -> "Отправляем ордера"
        RobotRunState.DONE_SUCCESS -> "Готово"
        RobotRunState.DONE_PAUSE -> "Готово: Минфин объявил паузу"
        RobotRunState.DONE_WINDOW_EXPIRED -> "Окно закрыто, публикация не появилась"
        RobotRunState.ERROR -> "Завершено с ошибкой"
    }

    object State {
        @Volatile var pendingMode: Mode? = null
        @Volatile var pendingMinfinConfig: RobotConfig? = null
        @Volatile var pendingCbrConfig: CbrConfig? = null
        @Volatile var cachedInstruments: List<InstrumentRef> = emptyList()

        @Volatile var currentMode: Mode? = null
            internal set

        internal val _currentState = MutableStateFlow(RobotRunState.IDLE)
        val currentState: StateFlow<RobotRunState> = _currentState.asStateFlow()

        fun isBusy(): Boolean = currentMode != null
    }

    companion object {
        private const val CHANNEL_ID = "minfin_robot_channel"
        private const val NOTIFICATION_ID = 1001

        fun startMinfin(context: Context, config: RobotConfig, instruments: List<InstrumentRef>): Boolean {
            if (State.isBusy()) {
                LogStore.warn("Нельзя запустить Минфин-робот: уже запущен ${State.currentMode}")
                return false
            }
            State.pendingMode = Mode.MINFIN
            State.pendingMinfinConfig = config
            State.cachedInstruments = instruments
            val intent = Intent(context, RobotExecutionService::class.java)
            context.startForegroundService(intent)
            return true
        }

        fun startCbr(context: Context, config: CbrConfig, instruments: List<InstrumentRef>): Boolean {
            if (State.isBusy()) {
                LogStore.warn("Нельзя запустить ЦБ-робот: уже запущен ${State.currentMode}")
                return false
            }
            State.pendingMode = Mode.CBR
            State.pendingCbrConfig = config
            State.cachedInstruments = instruments
            val intent = Intent(context, RobotExecutionService::class.java)
            context.startForegroundService(intent)
            return true
        }

        fun stop(context: Context) {
            val intent = Intent(context, RobotExecutionService::class.java)
            context.stopService(intent)
        }
    }
}
