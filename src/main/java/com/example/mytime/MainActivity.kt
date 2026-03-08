package com.example.mytime

import com.google.accompanist.systemuicontroller.rememberSystemUiController
import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mytime.ui.FlipClockWithBackground
import com.example.mytime.ui.theme.MytimeTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Modifier
import android.media.MediaPlayer


class MainActivity : ComponentActivity() {
    private val batteryLevelState = mutableStateOf("--")
    private val locationState = mutableStateOf("Loading...")
    private val dateState = mutableStateOf("--")
    private val hourState = mutableStateOf("00")
    private val minuteState = mutableStateOf("00")
    private val secondState = mutableStateOf("00")
    private val amPmState = mutableStateOf("AM")
    private val dayOfWeekState = mutableStateOf("--")
    private var mediaPlayer: MediaPlayer? = null



    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val batteryPct = level * 100 / scale.toFloat()
                batteryLevelState.value = "${batteryPct.toInt()}%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化定位
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocationPermission()

        // 防止屏幕自动熄屏
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        // 初始化当前时间
        val now = Calendar.getInstance()
        updateCurrentTime(now)

        setContent {
            MytimeTheme {
                val systemUiController = rememberSystemUiController()
                SideEffect {
                    systemUiController.setSystemBarsColor(
                        color = Color(0xFF1A1A1A),
                        darkIcons = false
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                ) {
                    FlipClockWithBackground(
                        batteryLevel = batteryLevelState.value,
                        location = locationState.value,
                        date = dateState.value,
                        dayOfWeek = dayOfWeekState.value,
                        hour = hourState.value,
                        minute = minuteState.value,
                        second = secondState.value,
                        amPm = amPmState.value,
                        backgroundRes = getBackgroundForTime(),
                        onPlayAudio = { playAudio() }

                    )
                }
            }
            // 定时更新时间
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) {
                    while (true) {
                        delay(1000)
                        val nowLoop = Calendar.getInstance()
                        updateCurrentTime(nowLoop)
                    }
                }
            }
        }
    }


    fun playAudio() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.audio_file)
        }
        mediaPlayer?.start()
    }
    // 获取当前时间对应的背景图
    private fun getBackgroundForTime(): Int {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            currentHour in 6..11 -> R.drawable.morning_background
            currentHour in 12..17 -> R.drawable.afternoon_background
            else -> R.drawable.jiguang
        }
    }


    private fun updateCurrentTime(now: Calendar) {
        val h = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = now.get(Calendar.MINUTE).toString().padStart(2, '0')
        val s = now.get(Calendar.SECOND).toString().padStart(2, '0')

        hourState.value = h
        minuteState.value = m
        secondState.value = s
        amPmState.value = if (h.toInt() < 12) "AM" else "PM"

        // 更新日期部分
        dateState.value = dateFormat.format(now.time)
        // 更新星期部分
        val dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) ?: "Unknown"
        dayOfWeekState.value = dayOfWeek

    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        } else {
            getLocationWithLocationManager()
        }
    }


    @SuppressLint("MissingPermission")
    private fun getLocationWithLocationManager() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGPSEnabled || isNetworkEnabled) {
            val provider = if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            val location = locationManager.getLastKnownLocation(provider)

            if (location != null) {
                // 获取经纬度
                val lat = location.latitude
                val lng = location.longitude
                // 使用 Geocoder 解析地址
                val geocoder = Geocoder(this, Locale.ENGLISH)
                try {
                    val addresses: List<Address> = geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val locality = address.locality ?: ""
                        val country = address.countryName ?: ""
                        locationState.value = "$locality $country"
                    } else {
                        locationState.value = "Unknown Location"
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    locationState.value = "Unable to retrieve location"
                }
            } else {
                locationState.value = "Location not available"
            }
        } else {
            locationState.value = "Location services disabled"
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocationWithLocationManager()
        } else {
            locationState.value = "Permission denied"
        }
    }


    override fun onResume() {
        super.onResume()
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, ifilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }
}

