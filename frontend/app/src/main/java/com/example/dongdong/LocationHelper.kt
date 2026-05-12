package com.example.dongdong

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * 위치 정보를 가져오고 주소로 변환하는 헬퍼 클래스
 * 요구사항: 
 * 1. ACCESS_FINE_LOCATION 권한 필요
 * 2. play-services-location 및 kotlinx-coroutines-play-services 라이브러리 필요
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentAddress(): String? {
        return try {
            // 현재 위치 가져오기 (고정밀도 요청)
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            location?.let {
                getAddressFromCoords(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getAddressFromCoords(lat: Double, lng: Double): String? {
        val geocoder = Geocoder(context, Locale.KOREAN)
        return try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                
                // 요구사항: 시(adminArea)와 구/동(locality/subLocality) 단위 정제
                val city = address.adminArea ?: "" // 예: 인천광역시
                val district = address.locality ?: address.subLocality ?: "" // 예: 미추홀구
                
                if (city.isNotEmpty() && district.isNotEmpty()) {
                    "$city $district"
                } else {
                    // 정보가 부족할 경우 전체 주소에서 대한민국 제외 후 반환
                    address.getAddressLine(0).replace("대한민국 ", "").trim()
                }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
