package com.example.myapp.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.os.Environment
import androidx.core.content.FileProvider

object FileUtils {
    // 내부 저장소에 URI로부터 파일 복사
    fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "routine_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)

            inputStream?.copyTo(outputStream)

            inputStream?.close()
            outputStream.close()

            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    // 카메라 촬영용 임시 파일 생성
    fun createImageFile(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "routine_${System.currentTimeMillis()}",
            ".jpg",
            storageDir
        )
    }

    // FileProvider를 통한 URI 반환
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
