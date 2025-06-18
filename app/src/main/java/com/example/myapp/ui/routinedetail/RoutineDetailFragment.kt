package com.example.myapp.ui.routinedetail

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.myapp.ui.RoutineViewModel
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapp.R
import com.example.myapp.databinding.FragmentRoutineDetailBinding
import com.example.myapp.model.RoutineRecordEntity
import com.example.myapp.util.FileUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RoutineDetailFragment : Fragment() {
    private var _binding: FragmentRoutineDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RoutineViewModel by activityViewModels()
    private lateinit var recordAdapter: RoutineRecordAdapter
    private var selectedPhotoUri: Uri? = null
    private var imagePreview: ImageView? = null
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

    companion object {
        private const val ARG_ROUTINE_ID = "routine_id"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineDetailBinding.inflate(inflater, container, false)

        // 어댑터 생성
        recordAdapter = RoutineRecordAdapter(
            emptyList(),
            onItemClick = { record ->
                showRoutineRecordDialog(record)
            }
        )
        binding.recyclerView.adapter  = recordAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val routineId = arguments?.getInt(ARG_ROUTINE_ID) ?: -1

        // 루틴 제목 설정
        viewModel.getRoutineById(routineId).observe(viewLifecycleOwner) { loadedRoutine ->
            loadedRoutine?.let {
                (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = ""
                binding.routineTitleTextView.text = it.title
            }
        }

        // 기록 목록 관찰 및 어댑터에 전달
        viewModel.getRecordsForRoutine(routineId).observe(viewLifecycleOwner) { records ->
            recordAdapter.submitList(records)

        }
    }
    // 루틴 기록 다이얼로그 표시
    private fun showRoutineRecordDialog(record: RoutineRecordEntity) {
        viewModel.getRoutineById(record.routineId).observe(viewLifecycleOwner) { routine ->
            val dialogView = layoutInflater.inflate(
                R.layout.dialog_record_routine,
                requireActivity().window.decorView as ViewGroup,
                false
            )
            val titleText = dialogView.findViewById<TextView>(R.id.tvRoutineTitle)
            val editDetail = dialogView.findViewById<EditText>(R.id.editDetail)
            val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

            titleText.text = routine?.title ?: "기록"
            editDetail.setText(record.detail ?: "")

            imagePreview = dialogView.findViewById(R.id.imagePreview)
            val btnSelectPhoto = dialogView.findViewById<Button>(R.id.btnSelectPhoto)
            if (!record.photoUri.isNullOrEmpty()) {
                selectedPhotoUri = record.photoUri.toUri()
                imagePreview?.apply {
                    visibility = View.VISIBLE
                    setImageURI(selectedPhotoUri)
                }
            }

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

            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(dialogView)

            btnSave.setOnClickListener {
                val detail = editDetail.text.toString()
                if (detail.isEmpty()) {
                    editDetail.error = "상세 내용을 입력하세요"
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val localUri = withContext(Dispatchers.IO) {
                        selectedPhotoUri?.let {
                            FileUtils.copyUriToInternalStorage(requireContext(), it)
                        }
                    }
                    val updated = record.copy(
                        detail = detail,
                        photoUri = localUri?.toString() ?: record.photoUri
                    )
                    viewModel.updateRecord(updated)
                    dialog.dismiss()
                }
            }
            dialog.show()
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

}