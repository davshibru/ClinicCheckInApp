package com.advmeds.cliniccheckinapp.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.advmeds.cliniccheckinapp.R
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.ApiService
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.request.CreateAppointmentRequest
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.response.CreateAppointmentResponse
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.response.GetClinicGuardianResponse
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.response.GetPatientsResponse
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.response.GetScheduleResponse
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.sharedPreferences.AutomaticAppointmentSettingModel
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.sharedPreferences.QueueingMachineSettingModel
import com.advmeds.cliniccheckinapp.repositories.ServerRepository
import com.advmeds.cliniccheckinapp.repositories.SharedPreferencesRepo
import com.advmeds.cliniccheckinapp.utils.NativeText
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okio.Buffer
import retrofit2.Retrofit
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferencesRepo = SharedPreferencesRepo.getInstance(getApplication())

    /** @see SharedPreferencesRepo.checkInSerialNo */
    var checkInSerialNo: Int
        get() = sharedPreferencesRepo.checkInSerialNo
        set(value) {
            sharedPreferencesRepo.checkInSerialNo = value
        }

    /** @see SharedPreferencesRepo.queueingMachineSetting */
    var queueingMachineSettings: QueueingMachineSettingModel
        get() = sharedPreferencesRepo.queueingMachineSetting
        set(value) {
            sharedPreferencesRepo.queueingMachineSetting = value
        }

    /** @see SharedPreferencesRepo.queueingMachineSettingIsEnable */
    val queueingMachineSettingIsEnable: Boolean
        get() = sharedPreferencesRepo.queueingMachineSettingIsEnable


    /** @see SharedPreferencesRepo.queueingBoardSettingIsEnable */
    val queueingBoardSettingIsEnable: Boolean
        get() = sharedPreferencesRepo.queueingBoardSettingIsEnable

    val automaticAppointmentSetting: AutomaticAppointmentSettingModel
        get() = sharedPreferencesRepo.automaticAppointmentSetting

    private val format = Json {
        isLenient = true
        coerceInputValues = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor {
            val request = it.request()

            val buffer = Buffer()
            request.body()?.writeTo(buffer)

            Timber.d("URL: ${request.url()}")
            Timber.d("Method: ${request.method()}")
            Timber.d("Request: ${buffer.readUtf8()}")

            return@addInterceptor it.proceed(request)
        }
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    private val serverRepo: ServerRepository
        get() {
            val retrofit = Retrofit.Builder()
                .client(client)
                .baseUrl(sharedPreferencesRepo.mSchedulerServerDomain.first)
                .addConverterFactory(format.asConverterFactory(MediaType.get("application/json")))
                .build()

            return ServerRepository(
                retrofit.create(ApiService::class.java)
            )
        }

    val getGuardianStatus = MutableLiveData<GetGuardianStatus>()
    val checkInStatus = MutableLiveData<CheckInStatus>()
    val getSchedulesStatus = MutableLiveData<GetSchedulesStatus>()
    val createAppointmentStatus = MutableLiveData<CreateAppointmentStatus>()

    private var getGuardianJob: Job? = null
    private var checkJob: Job? = null
    private var getSchedulesJob: Job? = null
    private var createAppointmentJob: Job? = null

    val clinicGuardian = MutableLiveData<GetClinicGuardianResponse?>()
    private var patient: CreateAppointmentRequest.Patient? = null

    fun getClinicGuardian(completion: ((GetClinicGuardianResponse) -> Unit)? = null) {
        createAppointmentJob?.cancel()
        getSchedulesJob?.cancel()
        checkJob?.cancel()
        getGuardianJob?.cancel()

        getGuardianJob = viewModelScope.launch {
            getGuardianStatus.value = GetGuardianStatus.Checking

            val response = try {
                val connectivityManager =
                    getApplication<MainApplication>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                while (connectivityManager.activeNetworkInfo?.isConnected != true) {
                    withContext(Dispatchers.IO) { delay(1000) }
                }

                val result = serverRepo.getClinicGuardian(sharedPreferencesRepo.orgId)

                Timber.d("Status code: ${result.code()}")
                Timber.d("Response: ${format.encodeToString(result.body())}")

                if (result.isSuccessful) {
                    result.body()!!.also {
                        sharedPreferencesRepo.logoUrl = it.logo
                    }
                } else {
                    GetClinicGuardianResponse(
                        success = false,
                        code = result.code(),
                        message = result.message()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)

                GetClinicGuardianResponse(
                    success = false,
                    code = 0,
                    message = when (e) {
                        is UnknownHostException -> {
                            getApplication<MainApplication>().getString(
                                R.string.no_internet
                            )
                        }
                        is SocketTimeoutException -> {
                            getApplication<MainApplication>().getString(
                                R.string.service_timeout
                            )
                        }
                        else -> {
                            e.localizedMessage
                        }
                    }
                )
            }

            clinicGuardian.value = response.takeIf { it.success }

            completion?.let { it(response) }

            getGuardianStatus.value = GetGuardianStatus.NotChecking(response)
        }

        getGuardianJob?.invokeOnCompletion {
            it ?: return@invokeOnCompletion

            Timber.e(it)

            clinicGuardian.value = null

            getGuardianStatus.value = if (it !is CancellationException) {
                GetGuardianStatus.Fail(it)
            } else {
                GetGuardianStatus.NotChecking(null)
            }
        }
    }

    fun getPatients(
        patient: CreateAppointmentRequest.Patient,
        completion: ((GetPatientsResponse) -> Unit)? = null
    ) {
        if (getGuardianJob?.isActive == true) return
        if (isCheckInEventProcessing()) return

        createAppointmentJob?.cancel()
        getSchedulesJob?.cancel()
        checkJob?.cancel()

        this.patient = patient

        checkJob = viewModelScope.launch {
            val application = getApplication<MainApplication>()

            if (clinicGuardian.value == null) {
                checkInStatus.value = CheckInStatus.NotChecking(
                    response = GetPatientsResponse(
                        success = false,
                        code = 0,
                        message = NativeText.Resource(R.string.clinic_data_not_found)
                    )
                )
                return@launch
            }

            val formatCheckedList = sharedPreferencesRepo.formatCheckedList

            if (patient.nationalId.isBlank()) {
                checkInStatus.value = CheckInStatus.NotChecking(
                    response = GetPatientsResponse(
                        success = false,
                        code = 0,
                        message = NativeText.Arguments(
                            R.string.national_id_input_hint,
                            listOf(formatCheckedList.joinToString("、") {
                                application.getString(
                                    it.description
                                )
                            })
                        )
                    )
                )
                return@launch
            }

            if (formatCheckedList.isNotEmpty() &&
                formatCheckedList.none {
                    it.inputFormatAvailable(patient.nationalId)
                }
            ) {
                checkInStatus.value = CheckInStatus.NotChecking(
                    response = GetPatientsResponse(
                        success = false,
                        code = 0,
                        message = NativeText.ArgumentsMulti(
                            R.string.national_id_format_invalid,
                            formatCheckedList.map { NativeText.Resource(it.description) }
                        )
                    )
                )

                return@launch
            }

            checkInStatus.value = CheckInStatus.Checking

            val response = try {
                val result = serverRepo.getPatients(
                    clinicId = sharedPreferencesRepo.orgId,
                    nationalId = patient.nationalId,
                    doctors = sharedPreferencesRepo.doctors.toTypedArray(),
                    rooms = sharedPreferencesRepo.rooms.mapNotNull { it.toIntOrNull() }
                        .toTypedArray()
                )

                Timber.d("Status code: ${result.code()}")
                Timber.d("Response: ${format.encodeToString(result.body())}")

                if (result.isSuccessful) {
                    result.body()!!
                } else {
                    GetPatientsResponse(
                        success = false,
                        code = result.code(),
                        message = NativeText.Simple(result.message())
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)

                GetPatientsResponse(
                    success = false,
                    code = 0,
                    message = when (e) {
                        is UnknownHostException -> {
                            NativeText.Resource(
                                R.string.no_internet
                            )
                        }
                        is SocketTimeoutException -> {
                            NativeText.Resource(
                                R.string.service_timeout
                            )
                        }
                        else -> {
                            e.localizedMessage?.let { NativeText.Simple(it) } ?: NativeText.Simple("")
                        }
                    }
                )
            }

            completion?.let { it(response) }

            checkInStatus.value = CheckInStatus.NotChecking(response = response, patient = patient)
        }

        checkJob?.invokeOnCompletion {
            it ?: return@invokeOnCompletion

            Timber.e(it)

            checkInStatus.value = if (it !is CancellationException) {
                CheckInStatus.Fail(it)
            } else {
                CheckInStatus.NotChecking(null)
            }
        }
    }

    fun getSchedule(completion: ((GetScheduleResponse) -> Unit)? = null) {
        if (checkJob?.isActive == true) return

        createAppointmentJob?.cancel()
        getSchedulesJob?.cancel()

        getSchedulesJob = viewModelScope.launch {
            getSchedulesStatus.value = GetSchedulesStatus.Checking

            val response = try {
                val result = serverRepo.getSchedules(sharedPreferencesRepo.orgId)

                Timber.d("Status code: ${result.code()}")
                Timber.d("Response: ${format.encodeToString(result.body())}")

                if (result.isSuccessful) {
                    result.body()!!
                } else {
                    GetScheduleResponse(
                        success = false,
                        code = result.code(),
                        message = result.message()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)

                GetScheduleResponse(
                    success = false,
                    code = 0,
                    message = when (e) {
                        is UnknownHostException -> {
                            getApplication<MainApplication>().getString(
                                R.string.no_internet
                            )
                        }
                        is SocketTimeoutException -> {
                            getApplication<MainApplication>().getString(
                                R.string.service_timeout
                            )
                        }
                        else -> {
                            e.localizedMessage
                        }
                    }
                )
            }

            completion?.let { it(response) }

            getSchedulesStatus.value = GetSchedulesStatus.NotChecking(response)
        }

        getSchedulesJob?.invokeOnCompletion {

            it ?: return@invokeOnCompletion

            Timber.e(it)

            getSchedulesStatus.value = if (it !is CancellationException) {
                GetSchedulesStatus.Fail(it)
            } else {
                GetSchedulesStatus.NotChecking(null)
            }
        }
    }

    fun createAppointment(
        isCheckIn: Boolean,
        schedule: GetScheduleResponse.ScheduleBean,
        patient: CreateAppointmentRequest.Patient? = null,
        isAutomaticAppointment: Boolean = false,
        completion: ((CreateAppointmentResponse) -> Unit)? = null
    ) {
        if (getSchedulesJob?.isActive == true) return

        if (!isAutomaticAppointment) {
            if (isCheckInEventProcessing())
                return
        }

        createAppointmentJob?.cancel()

        createAppointmentJob = viewModelScope.launch {
            createAppointmentStatus.value = CreateAppointmentStatus.Checking

            val response = try {
                val request = CreateAppointmentRequest(
                    checkIn = true,
                    clinicId = sharedPreferencesRepo.orgId,
                    doctor = schedule.doctor,
                    division = schedule.division,
                    patient = requireNotNull(patient ?: this@MainViewModel.patient) {
                        getApplication<MainApplication>().getString(R.string.mScheduler_api_error_10008)
                    }
                )

                val result = serverRepo.createsAppointment(request)

                Timber.d("Status code: ${result.code()}")
                Timber.d("Response: ${format.encodeToString(result.body())}")

                if (result.isSuccessful) {
                    val response = result.body()!!

                    if (!response.success &&
                        response.code == 10014 &&
                        schedule == GetScheduleResponse.ScheduleBean.PTCH_BABY
                    ) {
                        response.copy(
                            message = getApplication<MainApplication>().getString(R.string.mScheduler_api_error_10014_ptch_ca)
                        )
                    } else {
                        response
                    }
                } else {
                    CreateAppointmentResponse(
                        success = false,
                        code = result.code(),
                        message = result.message()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)

                CreateAppointmentResponse(
                    success = false,
                    code = 0,
                    message = when (e) {
                        is UnknownHostException -> {
                            getApplication<MainApplication>().getString(
                                R.string.no_internet
                            )
                        }
                        is SocketTimeoutException -> {
                            getApplication<MainApplication>().getString(
                                R.string.service_timeout
                            )
                        }
                        else -> {
                            e.localizedMessage
                        }
                    }
                )
            }

            completion?.let { it(response) }

            createAppointmentStatus.value = CreateAppointmentStatus.NotChecking(response)
        }

        createAppointmentJob?.invokeOnCompletion {

            it ?: return@invokeOnCompletion

            Timber.e(it)

            createAppointmentStatus.value = if (it !is CancellationException) {
                CreateAppointmentStatus.Fail(it)
            } else {
                CreateAppointmentStatus.NotChecking(null)
            }
        }
    }

    fun cancelJobOnCardAbsent() {
        createAppointmentJob?.cancel()
        getSchedulesJob?.cancel()
        checkJob?.cancel()
    }

    fun completeAllJobOnCardAbsentAfterAllProcessIsOver(completion: () -> Unit) {
        if (createAppointmentJob?.isActive == true) {
            createAppointmentJob?.invokeOnCompletion {
                completion()
            }
        }
        if (getSchedulesJob?.isActive == true) {
            getSchedulesJob?.invokeOnCompletion {
                completion()
            }
        }
        if (checkJob?.isActive == true) {
            checkJob?.invokeOnCompletion {
                completion()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }


    private fun isCheckInEventProcessing() =
        checkJob?.isActive == true || createAppointmentJob?.isActive == true

    fun getLanguage(): String {
        return sharedPreferencesRepo.language
    }


    sealed class GetGuardianStatus {
        object Checking : GetGuardianStatus()
        data class NotChecking(val response: GetClinicGuardianResponse?) : GetGuardianStatus()
        data class Fail(val throwable: Throwable) : GetGuardianStatus()
    }

    sealed class CheckInStatus {
        object Checking : CheckInStatus()
        data class NotChecking(
            val response: GetPatientsResponse?,
            val patient: CreateAppointmentRequest.Patient? = null
        ) : CheckInStatus()

        data class Fail(val throwable: Throwable) : CheckInStatus()
    }

    sealed class GetSchedulesStatus {
        object Checking : GetSchedulesStatus()
        data class NotChecking(val response: GetScheduleResponse?) : GetSchedulesStatus()
        data class Fail(val throwable: Throwable) : GetSchedulesStatus()
    }

    sealed class CreateAppointmentStatus {
        object Checking : CreateAppointmentStatus()
        data class NotChecking(val response: CreateAppointmentResponse?) : CreateAppointmentStatus()
        data class Fail(val throwable: Throwable) : CreateAppointmentStatus()
    }
}