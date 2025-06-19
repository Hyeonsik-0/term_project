package com.example.myapp.ui.setting

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myapp.MainActivity
import com.example.myapp.R
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var idInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var showPasswordCheckBox: CheckBox
    private lateinit var signUpButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 자동 로그인 체크
        val prefs = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val fromChangePassword = intent.getBooleanExtra("fromChangePassword", false)
        val savedUserId = prefs.getString("userId", "")
        if (!fromChangePassword && prefs.getBoolean("isLoggedIn", false)) {
            Toast.makeText(this, "${savedUserId}님 자동 로그인", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        idInput = findViewById(R.id.loginIdInput)
        passwordInput = findViewById(R.id.loginPasswordInput)
        loginButton = findViewById(R.id.loginButton)
        showPasswordCheckBox = findViewById(R.id.loginShowPasswordCheckBox)
        signUpButton = findViewById(R.id.signupButton)

        // 인텐트로 전달된 userId가 있다면 자동 입력
        val passedUserId = intent.getStringExtra("userId")
        if (!passedUserId.isNullOrEmpty()) {
            idInput.setText(passedUserId)
        }

        // 비밀번호 표시 토글
        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            passwordInput.transformationMethod = if (isChecked)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            passwordInput.setSelection(passwordInput.text.length)
        }

        // 회원가입 화면으로 이동
        signUpButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // 로그인 요청
        loginButton.setOnClickListener {
            val userId = idInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("userId", userId)
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://routine-server-uqzh.onrender.com/login-user")
                .post(body)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            prefs.edit()
                                .putBoolean("isLoggedIn", true)
                                .putString("userId", userId)
                                .apply()

                            Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "로그인 실패: 아이디 또는 비밀번호 오류", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }
}
