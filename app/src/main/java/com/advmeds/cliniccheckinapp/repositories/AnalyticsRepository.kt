package com.advmeds.cliniccheckinapp.repositories

import android.util.Log
import com.advmeds.cliniccheckinapp.BuildConfig
import com.advmeds.cliniccheckinapp.models.events.EventRepository
import com.advmeds.cliniccheckinapp.models.events.entities.EventData
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "check---"

interface AnalyticsRepository {
    suspend fun sendEvent(
        eventName: String,
        params: MutableMap<String, Any>? = null,
        sessionNumber: Long? = null,
        destination: DestinationType = DestinationType.LOCAL
    )

    companion object {
        const val SOURCE_ACTION = "source_value"
        const val SOURCE_SCREEN = "SourceScreen"
        const val SESSION_NUMBER = "SessionNumber"
        const val DEVICE_ID = "deviceId"
        const val USER_ID = "userId"
        const val DOCTOR_ID = "userId"
        const val APP_VERSION_NAME = "app_version_name"
        const val APP_VERSION_CODE = "app_version_code"
        const val TIME = "app_version_code"
    }

    enum class DestinationType {
        LOCAL,
        SERVER,
        LOCAL_TO_SERVER
    }
}

class AnalyticsRepositoryImpl private constructor(
    private val eventRepository: EventRepository,
    private val sharedPreferencesRepo: SharedPreferencesRepo
) : AnalyticsRepository {

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsRepositoryImpl? = null

        fun getInstance(
            eventRepository: EventRepository,
            sharedPreferencesRepo: SharedPreferencesRepo
        ): AnalyticsRepository {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = AnalyticsRepositoryImpl(eventRepository, sharedPreferencesRepo)
                }
                return INSTANCE!!
            }
        }
    }

    override suspend fun sendEvent(
        eventName: String,
        params: MutableMap<String, Any>?,
        sessionNumber: Long?,
        destination: AnalyticsRepository.DestinationType
    ) {
        setGlobalProperties(params)
        when (destination) {
            AnalyticsRepository.DestinationType.LOCAL -> {
                logEventToLocal(eventName, params, sessionNumber)
            }
            AnalyticsRepository.DestinationType.LOCAL_TO_SERVER -> {
                logEventFromLocalToServer()
            }
            AnalyticsRepository.DestinationType.SERVER -> TODO()
        }
    }

    private suspend fun logEventToLocal(
        eventName: String,
        params: MutableMap<String, Any>?,
        sessionNumber: Long?
    ) {
        params?.let {

            val sessionId = eventRepository.getOrCreateNewSession(
                sessionNumber = sessionNumber ?: sharedPreferencesRepo.sessionNumber.toLong(),
                deviceId = sharedPreferencesRepo.deviceId
            )

            val eventData = EventData(eventName = eventName, params = params, sessionId = sessionId)
            try {
                eventRepository.saveEventInDataBase(eventData)
            } catch (e: java.lang.Exception) {
                Log.d(TAG, "logEventToLocal: $e")
            }
        }
    }

    private suspend fun logEventFromLocalToServer() {
        try {
            val eventDataList = eventRepository.getAllEventFromDatabase()

            eventDataList.forEach { eventDataDb ->
                val param = eventRepository.getParamById(eventDataDb.id)
                eventDataDb.params.putAll(param)
                Log.d(TAG, "$param")
            }

            Log.d(TAG, "\n")
        } catch (e: Exception) {
            Log.d(TAG, "logEventToLocal: $e")
        }
    }

    private fun setGlobalProperties(params: MutableMap<String, Any>?) {
        params?.let {
            params[AnalyticsRepository.APP_VERSION_NAME] =
                BuildConfig.VERSION_NAME
            params[AnalyticsRepository.APP_VERSION_CODE] =
                BuildConfig.VERSION_CODE
            params[AnalyticsRepository.TIME] =
                getCurrentDateTime()
        }
    }

    private fun getCurrentDateTime(): String {
        val currentDateAndTime = Date()

        val pattern = "yyyy-MM-dd'T'HH:mm:ss"
        val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())

        return simpleDateFormat.format(currentDateAndTime)
    }
}