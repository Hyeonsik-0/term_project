package com.example.myapp.Alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapp.R
import com.example.myapp.model.AppDatabase
import com.example.myapp.model.RoutineEntity
import com.example.myapp.model.Weekday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.LocalTime
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getIntExtra("ROUTINE_ID", 0)
        if (routineId == 0) return

        CoroutineScope(Dispatchers.IO).launch {
            // AppDatabase.getInstance(context)를 사용하여 DB 인스턴스를 가져옵니다.
            val db = AppDatabase.getInstance(context)
            val today = java.time.LocalDate.now()

            // 1. 오늘 날짜의 루틴 기록이 있는지 확인
            val recordCount = db.routineRecordDao().getRecordCountForToday(routineId, today)

            // 2. 기록이 없을 때만 (0개일 때만) 알림을 표시
            if (recordCount == 0) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val routineTitle = intent.getStringExtra("ROUTINE_TITLE") ?: "루틴 시간입니다!"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        "routine_alarm_channel",
                        "루틴 알림",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "설정한 루틴의 시작을 알립니다."
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val builder = NotificationCompat.Builder(context, "routine_alarm_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(routineTitle)
                    .setContentText("오늘의 루틴을 실천할 시간이에요! 💪")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                notificationManager.notify(routineId, builder.build())

                // 3. 공유 대상에게도 알람 보내기
                val routine = db.routineDao().getNoLiveRoutineById(routineId)
                if (routine?.isShared == true && routine.sharedWith.isNotEmpty()) {
                    val prefs = context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                    val fromUserId = prefs.getString("userId", "") ?: ""
                    sendRoutinePerformedNotification(
                        fromUserId,
                        routine.title,
                        routine.sharedWith
                    )
                }
            }

            // 4. 다음 알람을 다시 설정합니다.
            rescheduleNextAlarm(context, routineId)
        }
    }

    private fun rescheduleNextAlarm(context: Context, routineId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            // suspend 함수인 getRoutineByIdSuspend를 호출합니다.
            val routine = db.routineDao().getRoutineByIdSuspend(routineId)

            if (routine != null && routine.isActive) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nextAlarmTime = getNextAlarmTime(routine.startTime, routine.repeatOn)

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("ROUTINE_ID", routine.id)
                    putExtra("ROUTINE_TITLE", routine.title)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    routine.id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextAlarmTime.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    // 다음 알람 시간을 계산하는 헬퍼 함수
    private fun getNextAlarmTime(startTime: LocalTime, repeatOn: Set<Weekday>): Calendar {
        val now = Calendar.getInstance()

        // 오늘부터 7일간 확인
        for (i in 0..7) {
            val nextPossibleDay = Calendar.getInstance()
            nextPossibleDay.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = nextPossibleDay.get(Calendar.DAY_OF_WEEK)

            if (repeatOn.any { it.name == intToWeekday(dayOfWeek).name }) {
                val nextAlarmTime = Calendar.getInstance().apply {
                    time = nextPossibleDay.time // 기준 날짜를 i일 후로 설정
                    set(Calendar.HOUR_OF_DAY, startTime.hour)
                    set(Calendar.MINUTE, startTime.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // 계산된 알람 시간이 현재 시간보다 미래인 경우에만 유효
                if (nextAlarmTime.after(now)) {
                    return nextAlarmTime
                }
            }
        }

        // 만약 7일 내에 유효한 다음 알람이 없다면, 가장 빠른 요일에 다음주로 설정
        val fallbackDay = Calendar.getInstance()
        fallbackDay.add(Calendar.DAY_OF_YEAR, 7)
        return fallbackDay
    }

    // Calendar의 요일(Int)을 Weekday Enum으로 변환
    private fun intToWeekday(dayOfWeek: Int): Weekday {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> Weekday.SUNDAY
            Calendar.MONDAY -> Weekday.MONDAY
            Calendar.TUESDAY -> Weekday.TUESDAY
            Calendar.WEDNESDAY -> Weekday.WEDNESDAY
            Calendar.THURSDAY -> Weekday.THURSDAY
            Calendar.FRIDAY -> Weekday.FRIDAY
            Calendar.SATURDAY -> Weekday.SATURDAY
            else -> throw IllegalArgumentException("Invalid day of week")
        }
    }

    private fun sendRoutinePerformedNotification(
        fromUserId: String,
        routineName: String,
        sharedWith: List<String>
    ) {
        val client = OkHttpClient()

        for (toUserId in sharedWith) {
            val json = JSONObject().apply {
                put("fromUser", fromUserId)
                put("toUser", toUserId)
                put("routineName", routineName)
                put("isPerformed", "false")
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://routine-server-uqzh.onrender.com/notify")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("FCM", "전송 실패: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d("FCM", "전송 성공: ${response.code}")
                }
            })
        }
    }
}