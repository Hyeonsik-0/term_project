package com.example.myapp.ui.home

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapp.R
import com.example.myapp.databinding.DialogEditRoutineBinding
import com.example.myapp.databinding.FragmentHomeBinding
import com.example.myapp.model.Weekday
import com.example.myapp.model.RoutineEntity
import com.example.myapp.ui.RoutineViewModel
import androidx.navigation.fragment.findNavController
import com.example.myapp.util.FileUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import androidx.core.net.toUri
import com.example.myapp.weather.LocationProvider
import com.example.myapp.weather.WeatherRepository
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.bumptech.glide.Glide
import com.example.myapp.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RoutineViewModel by activityViewModels()
    private val weatherViewModel: WeatherViewModel by activityViewModels()
    private lateinit var adapter: RoutineAdapter

    private var selectedPhotoUri: Uri? = null
    private var imagePreview: ImageView? = null
    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            selectedPhotoUri = cameraImageUri
            imagePreview?.apply {
                visibility = View.VISIBLE
                setImageURI(cameraImageUri)
            }
            savePhotoToGallery(cameraImageFile)
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 사진 선택 후 처리 로직
            val uri = result.data?.data
            selectedPhotoUri = uri
            imagePreview?.apply {
                visibility = View.VISIBLE
                setImageURI(uri)
            }
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(requireContext(), "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "알림을 받으려면 권한을 허용해야 합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    private lateinit var locationProvider: LocationProvider
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 이상에서만
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                weatherViewModel.loadWeather(locationProvider, weatherRepository)
            } else {
                Toast.makeText(requireContext(), "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        askNotificationPermission() // 권한 요청 함수 호출
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        binding.textToday.text = today

        adapter = RoutineAdapter(
            emptyList(),
            onItemClick = { routine -> showRoutineDetails(routine) }, // 기존: showRoutineDetails -> 변경: showEditRoutineDialog
            onAddClick = { showAddRoutineDialog() },
            onStartClick = { routine -> showRoutineRecordDialog(routine) },
            onEditClick = { routine -> showEditRoutineDialog(routine) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewModel.routines.observe(viewLifecycleOwner) { routines ->
            adapter.submitList(routines)
        }
        viewModel.todaysRecords.observe(viewLifecycleOwner) { records ->
            adapter.updateCompletedRoutines(records)
        }
        locationProvider = LocationProvider(requireActivity())
        weatherRepository = WeatherRepository(requireContext())

        // 날씨 LiveData observe
        weatherViewModel.weatherInfo.observe(viewLifecycleOwner) { info ->
            binding.weatherTextView.text = String.format(Locale.getDefault(), "%.1f°C \n %s", info.main.temp, info.name)
            Glide.with(this)
                .load(info.weather[0].icon)
                .into(binding.weatherIconImageView)
        }
        weatherViewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        // 최초 1회만 loadWeather 호출
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            weatherViewModel.loadWeather(locationProvider, weatherRepository)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 루틴 수정
    private fun showEditRoutineDialog(routine: RoutineEntity) {
        val dialogBinding = DialogEditRoutineBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("루틴 수정")
            .setView(dialogBinding.root)
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
            .setNeutralButton("삭제") { _, _ ->
                // 삭제 버튼 클릭 시 루틴과 알람 삭제
                viewModel.deleteRoutine(routine)
                Toast.makeText(requireContext(), "'${routine.title}' 루틴이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .show()

        // 기존 루틴 데이터로 다이얼로그 내용을 채웁니다.
        dialogBinding.editTitle.setText(routine.title)
        dialogBinding.timeButton.text = routine.startTime.toString()
        dialogBinding.switchActive.isChecked = routine.isActive // '알림 활성화' 상태 불러오기
        dialogBinding.checkBoxShare.isChecked = routine.isShared // '공유 여부' 상태 불러오기
        // 요일 체크박스 상태 설정
        Weekday.entries.forEach { day ->
            dialogBinding.root.findViewWithTag<CheckBox>("cb_$day")?.isChecked = routine.repeatOn.contains(day)
        }

        // 시간 버튼 설정은 showAddRoutineDialog와 동일하게 구현
        dialogBinding.timeButton.setOnClickListener {
            val currentTime = routine.startTime // 기존 루틴의 시간을 기본값으로 설정
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedTime = LocalTime.of(hourOfDay, minute)
                    dialogBinding.timeButton.text = selectedTime.toString()
                },
                currentTime.hour,
                currentTime.minute,
                true // 24시간 형식으로 표시
            ).show()
        }

        var friendList: MutableList<Friend>? = null
        lateinit var friendAdapter: FriendSelectAdapter

        val prefs = requireContext().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        val currentUid = prefs.getString("userId", null)

        if (currentUid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .collection("friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    friendList = snapshot.documents.mapNotNull {
                        it.toObject(Friend::class.java)
                    }.toMutableList()

                    // 🔽 루틴에 저장된 uid들과 비교해 체크 상태 초기화
                    routine.sharedWith?.let { sharedUids ->
                        friendList!!.forEach { friend ->
                            friend.isChecked = sharedUids.contains(friend.uid)
                        }
                    }

                    friendAdapter = FriendSelectAdapter(friendList!!)
                    dialogBinding.recyclerViewFriendSelect.apply {
                        layoutManager = LinearLayoutManager(requireContext())
                        adapter = friendAdapter
                        visibility = if (routine.isShared) View.VISIBLE else View.GONE
                    }

                    dialogBinding.checkBoxShare.setOnCheckedChangeListener { _, isChecked ->
                        dialogBinding.recyclerViewFriendSelect.visibility =
                            if (isChecked) View.VISIBLE else View.GONE
                    }
                }
        }

        // 저장 버튼 클릭 시 'updateRoutine'을 호출합니다.
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val title = dialogBinding.editTitle.text.toString().trim()
                if (title.isEmpty()) {
                    dialogBinding.editTitle.error = "제목을 입력하세요"
                    return@setOnClickListener
                }

                val updatedRepeatOn = Weekday.entries.filter { day ->
                    dialogBinding.root.findViewWithTag<CheckBox>("cb_$day")?.isChecked == true
                }.toSet()

                val timeParts = dialogBinding.timeButton.text.split(":")
                val updatedStartTime = LocalTime.of(timeParts[0].toInt(), timeParts[1].toInt())
                val updatedIsActive = dialogBinding.switchActive.isChecked // 수정된 '알림 활성화' 상태
                val updatedIsShared = dialogBinding.checkBoxShare.isChecked // 수정된 '공유 여부' 상태

                val updatedSharedWith = if (updatedIsShared && friendList != null) {
                    friendList!!.filter { it.isChecked }.map { it.uid }
                } else emptyList()

                // 기존 routine 객체에 변경된 값들을 복사하여 새로운 객체 생성
                val updatedRoutine = routine.copy(
                    title = title,
                    repeatOn = updatedRepeatOn,
                    startTime = updatedStartTime,
                    isActive = updatedIsActive,
                    sharedWith = updatedSharedWith,
                    isShared = updatedIsShared
                )

                viewModel.updateRoutine(updatedRoutine)
                dialog.dismiss()
            }
    }

    // 루틴 추가
    private fun showAddRoutineDialog() {
        val dialogBinding = DialogEditRoutineBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("루틴 추가")
            .setView(dialogBinding.root)
            .setPositiveButton("추가", null)
            .setNegativeButton("취소", null)
            .show()

        // 친구 목록 관련 변수
        var friendList: MutableList<Friend>? = null
        lateinit var friendAdapter: FriendSelectAdapter

        // 친구 목록 불러오기
        val prefs = requireContext().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
        val currentUid = prefs.getString("userId", null)

        if (currentUid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .collection("friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    friendList = snapshot.documents.mapNotNull {
                        it.toObject(Friend::class.java)
                    }.toMutableList()

                    friendAdapter = FriendSelectAdapter(friendList!!)
                    dialogBinding.recyclerViewFriendSelect.apply {
                        layoutManager = LinearLayoutManager(requireContext())
                        adapter = friendAdapter
                        visibility = View.GONE
                    }

                    dialogBinding.checkBoxShare.setOnCheckedChangeListener { _, isChecked ->
                        dialogBinding.recyclerViewFriendSelect.visibility =
                            if (isChecked) View.VISIBLE else View.GONE
                    }
                }
        }

        // 기본 값 설정
        dialogBinding.editTitle.setText("")
        dialogBinding.switchActive.isChecked = true
        Weekday.entries.forEach { day ->
            dialogBinding.root.findViewWithTag<CheckBox>("cb_$day")?.isChecked = true
        }

        dialogBinding.timeButton.setOnClickListener {
            val currentTime = LocalTime.now()
            TimePickerDialog(
                requireContext(),
                { _, h, m ->
                    val selectedTime = LocalTime.of(h, m)
                    dialogBinding.timeButton.text = selectedTime.toString()
                },
                currentTime.hour,
                currentTime.minute,
                true
            ).show()
        }

        // 루틴 추가 버튼 클릭 시
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val title = dialogBinding.editTitle.text.toString().trim()
                val isActive = dialogBinding.switchActive.isChecked

                if (title.isEmpty()) {
                    dialogBinding.editTitle.error = "제목을 입력하세요"
                    return@setOnClickListener
                }

                val timeButtonText = dialogBinding.timeButton.text.toString()
                if (isActive && timeButtonText == "시간 설정") {
                    Toast.makeText(requireContext(), "알림을 받으려면 시간을 설정해야 합니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val repeatOn = Weekday.entries.filter { day ->
                    dialogBinding.root.findViewWithTag<CheckBox>("cb_$day")?.isChecked == true
                }.toSet().takeIf { it.isNotEmpty() } ?: setOf(LocalDate.now().dayOfWeek.let {
                    Weekday.valueOf(it.name)
                })

                val startTime = if (isActive) {
                    val timeParts = timeButtonText.split(":")
                    LocalTime.of(timeParts[0].toInt(), timeParts[1].toInt())
                } else {
                    LocalTime.MIDNIGHT
                }

                // 공유 여부 & 선택된 UID 추출
                val isShared = dialogBinding.checkBoxShare.isChecked
                val sharedWith = if (isShared && friendList != null) {
                    friendList!!.filter { it.isChecked }.map { it.uid }
                } else {
                    emptyList()
                }

                // ViewModel에 공유 정보까지 전달
                viewModel.addRoutine(title, repeatOn, startTime, isActive, isShared, sharedWith)
                dialog.dismiss()
            }
    }

    // 루틴 상세 정보 화면으로 이동
    private fun showRoutineDetails(routine: RoutineEntity) {
        val bundle = Bundle().apply {
            putInt("routine_id", routine.id)
        }
        findNavController().navigate(R.id.action_homeFragment_to_routineDetailFragment, bundle)
    }

    // 루틴 수행
    private fun showRoutineRecordDialog(routine: RoutineEntity) {
        val today = LocalDate.now()

        lifecycleScope.launch {
            // 오늘 날짜의 기록이 있는지 확인
            val existingRecord = viewModel.getTodayRecord(routine.id, today)

            // 다이얼로그 UI 구성
            val dialogView = layoutInflater.inflate(R.layout.dialog_record_routine, requireActivity().window.decorView as ViewGroup, false)
            val titleText = dialogView.findViewById<TextView>(R.id.tvRoutineTitle)
            val editDetail = dialogView.findViewById<EditText>(R.id.editDetail)
            val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

            titleText.text = routine.title

            // 이미지 미리보기 설정
            imagePreview = dialogView.findViewById(R.id.imagePreview)
            val btnSelectPhoto = dialogView.findViewById<Button>(R.id.btnSelectPhoto)

            btnSelectPhoto.setOnClickListener {
                val options = arrayOf("갤러리에서 선택", "카메라로 촬영")
                AlertDialog.Builder(requireContext())
                    .setTitle("사진 선택")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> { // 갤러리
                                val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                                photoPickerLauncher.launch(intent)
                            }
                            1 -> { // 카메라
                                cameraImageFile = FileUtils.createImageFile(requireContext())
                                cameraImageUri = FileUtils.getUriForFile(requireContext(), cameraImageFile!!)
                                cameraLauncher.launch(cameraImageUri)
                            }
                        }
                    }
                    .show()
            }

            // 기존 기록이 있다면 상세 내용을 미리 채워넣기
            if (existingRecord != null) {
                editDetail.setText(existingRecord.detail ?: "")

                // 기존 이미지가 있다면 미리보기 설정
                if (existingRecord.photoUri != null) {
                    selectedPhotoUri = existingRecord.photoUri.toUri()
                    imagePreview?.apply {
                        visibility = View.VISIBLE
                        setImageURI(selectedPhotoUri)
                    }
                }
            }
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(dialogView)

            btnSave.setOnClickListener {
                val detail = editDetail.text.toString()
                if (detail.isEmpty()) {
                    editDetail.error = "상세 내용을 입력하세요"
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val localUri = withContext(Dispatchers.IO) { // coroutine로 IO 작업 수행
                        selectedPhotoUri?.let {
                            FileUtils.copyUriToInternalStorage(requireContext(), it)
                        }
                    }
                    if (existingRecord != null) {
                        // 기존 기록이 있다면 업데이트
                        val updated = existingRecord.copy(
                            detail = detail,
                            photoUri = localUri?.toString() ?: existingRecord.photoUri
                        )
                        viewModel.updateRecord(updated)
                    } else {
                        // 새로운 기록 추가
                        viewModel.addRecord(
                            routineId = routine.id,
                            date = today,
                            detail = detail,
                            photoUri = localUri?.toString()
                        )

                        if (routine.isShared && routine.sharedWith.isNotEmpty()) {
                            val prefs = requireContext().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                            val currentUserId = prefs.getString("userId", null) ?: return@launch

                            sendRoutinePerformedNotification(
                                fromUserId = currentUserId,
                                routineName = routine.title,
                                sharedWith = routine.sharedWith
                            )
                        }
                    }
                    dialog.dismiss()
                }
            }
            dialog.show()
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
                put("isPerformed", "true")
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://routine-server-uqzh.onrender.com/notify") // 에뮬레이터 기준
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

    // 갤러리에 찍은 사진을 저장하는 함수
    private fun savePhotoToGallery(photoFile: File?) {
        if (photoFile == null) return
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, photoFile.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { outputUri ->
            resolver.openOutputStream(outputUri)?.use { outputStream ->
                java.io.FileInputStream(photoFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
