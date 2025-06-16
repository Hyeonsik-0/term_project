package com.example.myapp.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.*
import androidx.room.Room
import com.example.myapp.Alarm.AlarmReceiver
import com.example.myapp.model.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import org.tensorflow.lite.Interpreter
import com.example.myapp.tokenizer.BertTokenizer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class RoutineViewModel(application: Application) : AndroidViewModel(application) {
    // 1) DB 인스턴스와 Repository 초기화
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "app-db"
    )
        .fallbackToDestructiveMigration(true)
        .build()

    private val routineDao = db.routineDao()
    private val recordDao  = db.routineRecordDao()
    private val repository = RoutineRepository(routineDao, recordDao)

    val routines: LiveData<List<RoutineEntity>> = repository.getAllRoutines()
    val todaysRecords: LiveData<List<RoutineRecordEntity>> = repository.getRecordsByDate(LocalDate.now())
    // 루틴을 추가하는 함수
    fun addRoutine(
        title: String,
        repeatOn: Set<Weekday>,
        startTime: LocalTime,
        isActive: Boolean,
        isShared: Boolean,
        sharedWith: List<String>
    ) {
        val routine = RoutineEntity(
            title = title,
            repeatOn = repeatOn,
            startTime = startTime,
            isActive = isActive,
            isShared = isShared,
            sharedWith = sharedWith
        )
        viewModelScope.launch {
            val newId = repository.insertRoutine(routine) // insert 후 새로운 ID를 받아옴
            if (isActive) {
                // 새로 생성된 ID로 알람을 설정
                scheduleAlarm(routine.copy(id = newId.toInt()))
            }
        }
    }
    fun updateRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.updateRoutine(routine)
            if (routine.isActive) {
                scheduleAlarm(routine)
            } else {
                cancelAlarm(routine)
            }
        }
    }
    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.deleteRoutine(routine)
            cancelAlarm(routine) // 알람도 함께 취소
        }
    }

    // 알람을 설정하는 함수
    fun scheduleAlarm(routine: RoutineEntity) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 정확한 알람을 설정할 수 있는 권한이 있는지 확인
            if (!alarmManager.canScheduleExactAlarms()) {
                return // 권한이 없으므로 알람 설정 중단
            }
        }
        // 기존에 설정된 알람이 있다면 취소
        cancelAlarm(routine)

        // 루틴이 비활성화 상태이거나 반복 요일이 없으면 알람을 설정하지 않음
        if (!routine.isActive || routine.repeatOn.isEmpty()) {
            return
        }

        // 다음 알람 시간을 계산
        val nextAlarmTime = getNextAlarmTime(routine.startTime, routine.repeatOn)

        // 알람을 위한 Intent 준비
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

        // 정확한 시간에 알람 설정 (Doze 모드에서도 동작)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAlarmTime.timeInMillis,
            pendingIntent
        )
    }

    // 다음 알람 시간을 계산하는 헬퍼 함수 (추가)
    private fun getNextAlarmTime(startTime: LocalTime, repeatOn: Set<Weekday>): Calendar {
        val now = Calendar.getInstance()

        // 오늘부터 7일간 확인
        for (i in 0..7) {
            val nextPossibleDay = Calendar.getInstance()
            nextPossibleDay.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = nextPossibleDay.get(Calendar.DAY_OF_WEEK)

            if (repeatOn.contains(intToWeekday(dayOfWeek))) {
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

        // 만약 7일 내에 유효한 다음 알람이 없다면, 가장 빠른 요일에 다음주로 설정 (이 경우는 거의 발생하지 않음)
        val fallbackDay = Calendar.getInstance()
        fallbackDay.add(Calendar.DAY_OF_YEAR, 7)
        return fallbackDay
    }

    // Calendar의 요일(Int)을 Weekday Enum으로 변환 (수정됨)
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


    // 알람을 취소하는 함수 (기존과 동일)
    private fun cancelAlarm(routine: RoutineEntity) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routine.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun getRoutineById(id: Int): LiveData<RoutineEntity?> {
        return repository.getRoutineById(id)
    }

    fun getRecordsForRoutine(routineId: Int): LiveData<List<RoutineRecordEntity>> {
        return repository.getRecordsForRoutine(routineId)
    }

    fun getRecordsByDate(date: LocalDate): LiveData<List<RoutineRecordEntity>> {
        return repository.getRecordsByDate(date)
    }

    // 루틴 기록을 추가하는 함수
    fun addRecord(routineId: Int, date: LocalDate, detail: String, photoUri: String? = null) {
        val sentiment = getSentiment(detail)
        val record = RoutineRecordEntity(
            routineId = routineId,
            date = date,
            detail = detail,
            photoUri = photoUri,
            sentiment = sentiment
        )
        viewModelScope.launch {
            repository.insertRecord(record)
        }
    }

    // 오늘 날짜의 루틴 ID와 일치하는 기록을 가져오는 함수
    suspend fun getTodayRecord( routineId: Int, today: LocalDate): RoutineRecordEntity? {
        return repository.getTodayRecord(routineId, today)
    }

    // 루틴 기록을 업데이트하는 함수
    fun updateRecord(record: RoutineRecordEntity) {
        val sentiment = getSentiment(record.detail ?: "")
        val updated = record.copy(sentiment = sentiment)
        viewModelScope.launch {
            repository.updateRecord(updated)
        }
    }

    fun getRecordsWithTitleForDate(date: LocalDate): LiveData<List<RoutineRecordWithTitle>> {
        val result = MutableLiveData<List<RoutineRecordWithTitle>>()

        val recordsLive = getRecordsByDate(date)
        val routinesLive = routines

        recordsLive.observeForever { records ->
            val routineList = routinesLive.value ?: return@observeForever

            val combined = records.mapNotNull {record ->
                val title = routineList.find { it.id == record.routineId }?.title
                title?.let {
                    RoutineRecordWithTitle(record, it)
                }
            }
            result.value = combined
        }

        return result
    }

    private fun getSentiment(inputText: String): Int {
        var interpreter = Interpreter(loadModelFile())
        var tokenizer = BertTokenizer(getApplication<Application>().applicationContext)
        val maxLen = 128

        val (inputIds, attnMask) = tokenizer.encode(inputText)
        val inputIdBuffer = ByteBuffer.allocateDirect(maxLen * 4).order(ByteOrder.nativeOrder())
        val attentionBuffer = ByteBuffer.allocateDirect(maxLen * 4).order(ByteOrder.nativeOrder())

        inputIds.forEach { inputIdBuffer.putInt(it) }
        attnMask.forEach { attentionBuffer.putInt(it) }
        inputIdBuffer.rewind()
        attentionBuffer.rewind()
        val tokenTypeBuffer = ByteBuffer.allocateDirect(128 * 4).order(ByteOrder.nativeOrder())
        repeat(128) { tokenTypeBuffer.putInt(0) } // KoElectra는 single sentence → 전부 0
        tokenTypeBuffer.rewind()

        val inputs = arrayOf(attentionBuffer, inputIdBuffer, tokenTypeBuffer)
        val output = Array(1) { FloatArray(2) }

        interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to output))

        val result = output[0]
        val probs = softmax(result)
        val diff = kotlin.math.abs(probs[1] - probs[0])
        val sentiment = when {
            diff <= 0.05 -> 0 // 중립
            probs[1] > probs[0] -> 1 // 긍정
            else -> -1 // 부정
        }

        return sentiment
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { Math.exp((it - max).toDouble()) }
        val sum = exps.sum()
        return exps.map { (it / sum).toFloat() }.toFloatArray()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetManager = getApplication<Application>().assets
        val fileDescriptor = assetManager.openFd("koelectra_fixed.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
