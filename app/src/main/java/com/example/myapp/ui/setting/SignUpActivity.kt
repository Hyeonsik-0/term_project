package com.example.myapp.ui.setting

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myapp.R
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod

class SignUpActivity : AppCompatActivity() {

    private lateinit var idInput: EditText
    private lateinit var nicknameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirmInput: EditText
    private lateinit var signUpButton: Button
    private lateinit var showPasswordCheckBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        idInput = findViewById(R.id.signUpIdInput)
        nicknameInput = findViewById(R.id.signUpNicknameInput)
        passwordInput = findViewById(R.id.signUpPasswordInput)
        passwordConfirmInput = findViewById(R.id.signUpPasswordConfirmInput)
        signUpButton = findViewById(R.id.signUpButton)
        showPasswordCheckBox = findViewById(R.id.signUpShowPasswordCheckBox)

        showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val method = if (isChecked) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            passwordInput.transformationMethod = method
            passwordConfirmInput.transformationMethod = method
            passwordInput.setSelection(passwordInput.text.length)
            passwordConfirmInput.setSelection(passwordConfirmInput.text.length)
        }

        signUpButton.setOnClickListener {
            val userId = idInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirm = passwordConfirmInput.text.toString().trim()
            val nickname = nicknameInput.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty() || confirm.isEmpty() || nickname.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버에 평문 비밀번호 전달
            val json = JSONObject().apply {
                put("userId", userId)
                put("password", password)
                put("nickname", nickname)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://routine-server-uqzh.onrender.com/register-user")
                .post(body)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@SignUpActivity, "회원가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        when {
                            response.isSuccessful -> {
                                Toast.makeText(this@SignUpActivity, "회원가입 성공", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                                intent.putExtra("userId", userId)  // 가입한 ID 전달
                                startActivity(intent)
                            }
                            response.code == 409 -> Toast.makeText(this@SignUpActivity, "이미 존재하는 ID입니다", Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(this@SignUpActivity, "서버 오류", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }
}
