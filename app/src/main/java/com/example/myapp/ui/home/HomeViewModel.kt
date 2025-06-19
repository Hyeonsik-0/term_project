package com.example.myapp.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.example.myapp.weather.LocationProvider
import com.example.myapp.weather.WeatherRepository
import com.example.myapp.weather.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.compareTo

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _weatherInfo = MutableLiveData<WeatherResponse>()
    val weatherInfo: LiveData<WeatherResponse> = _weatherInfo

    private val _weatherError = MutableLiveData<String?>()
    val weatherError: LiveData<String?> = _weatherError

    private val _quote = MutableLiveData<String?>()
    val quote: LiveData<String?> = _quote

    private var isWeatherLoaded = false
    private var isQuoteLoaded = false

    fun loadWeather(locationProvider: LocationProvider, weatherRepository: WeatherRepository) {
        if (isWeatherLoaded) return
        isWeatherLoaded = true

        locationProvider.getLocation { lat, lon ->
            weatherRepository.getWeather(
                lat, lon,
                onSuccess = { temp, iconUrl, cityName ->
                    val response = WeatherResponse(
                        main = com.example.myapp.weather.Main(temp, 0),
                        weather = listOf(com.example.myapp.weather.Weather("", iconUrl)),
                        name = cityName
                    )
                    _weatherInfo.postValue(response)
                },
                onFailure = {
                    _weatherError.postValue("날씨 정보를 불러오지 못했습니다")
                }
            )
        }
    }

    fun loadQuote(
        maxLength: Int = 25,
        maxRetry: Int = 3
    ) {
        if (isQuoteLoaded) return
        isQuoteLoaded = true

        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://korean-advice-open-api.vercel.app/api/advice")
                .build()

            repeat(maxRetry) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@repeat
                        val message = JSONObject(body).getString("message")
                        if (message.length <= maxLength) {
                            _quote.postValue(message)
                            return@launch
                        }
                    }
                } catch (_: Exception) { }
            }
            // 실패 시 기본 문구
            _quote.postValue("오늘은 내가 할 수 있는 최선의 날이다.")
        }
    }
}