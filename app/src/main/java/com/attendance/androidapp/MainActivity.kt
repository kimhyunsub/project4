package com.attendance.androidapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.attendance.androidapp.data.AttendanceApi
import com.attendance.androidapp.data.SessionStore
import com.attendance.androidapp.data.asApiMessage
import com.attendance.androidapp.location.LocationTracker
import com.attendance.androidapp.model.AppUiState
import com.attendance.androidapp.model.AttendanceActionRequestBody
import com.attendance.androidapp.model.AuthSession
import com.attendance.androidapp.model.CompanySetting
import com.attendance.androidapp.model.LoginRequestBody
import com.attendance.androidapp.model.TodayAttendanceStatus
import com.attendance.androidapp.model.UiLocation
import com.attendance.androidapp.model.UserInfo
import com.attendance.androidapp.ui.AttendanceMapView
import com.attendance.androidapp.util.DistanceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionStore = SessionStore(applicationContext)
        val api = AttendanceApi.create()
        val viewModelFactory = AttendanceViewModelFactory(
            api = api,
            sessionStore = sessionStore,
            deviceName = buildDeviceName()
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFEEF3FB)
                ) {
                    val viewModel: AttendanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = viewModelFactory)
                    AttendanceApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun buildDeviceName(): String {
        val manufacturer = (Build.MANUFACTURER ?: "").trim()
        val model = (Build.MODEL ?: "").trim()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }
    }
}

class AttendanceViewModelFactory(
    private val api: AttendanceApi,
    private val sessionStore: SessionStore,
    private val deviceName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AttendanceViewModel(api, sessionStore, deviceName) as T
    }
}

class AttendanceViewModel(
    private val api: AttendanceApi,
    private val sessionStore: SessionStore,
    private val deviceName: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(
            authSession = sessionStore.loadSession()
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        loadPublicCompanySetting()
        if (_uiState.value.authSession != null) {
            refreshAuthenticatedData()
        }
    }

    fun updateEmployeeCode(value: String) {
        _uiState.update { it.copy(employeeCode = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun updateLocationPermission(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
    }

    fun updateCurrentLocation(location: Location) {
        _uiState.update {
            it.copy(
                loadingLocation = false,
                currentLocation = UiLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.toDouble(),
                    capturedAt = Instant.ofEpochMilli(location.time.takeIf { it > 0 } ?: System.currentTimeMillis()).toString()
                )
            )
        }
    }

    fun onLocationLoading(started: Boolean) {
        _uiState.update { it.copy(loadingLocation = started) }
    }

    fun onLocationError(message: String) {
        _uiState.update { it.copy(loadingLocation = false, errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun logout() {
        sessionStore.clearSession()
        _uiState.update {
            it.copy(
                authSession = null,
                attendanceStatus = TodayAttendanceStatus(),
                errorMessage = null
            )
        }
    }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(loadingLogin = true, errorMessage = null) }
            try {
                val response = api.login(
                    LoginRequestBody(
                        employeeCode = state.employeeCode.trim(),
                        password = state.password,
                        deviceId = sessionStore.getOrCreateDeviceId(),
                        deviceName = deviceName
                    )
                )

                val session = AuthSession(
                    token = response.accessToken.orEmpty(),
                    tokenType = response.tokenType ?: "Bearer",
                    expiresAt = response.accessTokenExpiresAt ?: Instant.now().plusSeconds(31536000).toString(),
                    user = UserInfo(
                        id = response.employeeId,
                        name = response.employeeName ?: "사용자",
                        employeeCode = response.employeeCode ?: state.employeeCode.trim(),
                        companyName = response.companyName,
                        role = response.role
                    )
                )
                sessionStore.saveSession(session)

                _uiState.update {
                    it.copy(
                        authSession = session,
                        loadingLogin = false,
                        attendanceStatus = TodayAttendanceStatus(companyName = session.user.companyName),
                        companySetting = it.companySetting.copy(companyName = session.user.companyName ?: it.companySetting.companyName)
                    )
                }
                refreshAuthenticatedData()
            } catch (exception: HttpException) {
                _uiState.update {
                    it.copy(
                        loadingLogin = false,
                        errorMessage = normalizeLoginError(exception)
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        loadingLogin = false,
                        errorMessage = "로그인에 실패했습니다. 네트워크 상태를 확인해 주세요."
                    )
                }
            }
        }
    }

    fun checkIn() {
        submitAttendanceAction(isCheckIn = true)
    }

    fun checkOut() {
        submitAttendanceAction(isCheckIn = false)
    }

    private fun submitAttendanceAction(isCheckIn: Boolean) {
        val state = _uiState.value
        val session = state.authSession ?: return
        val currentLocation = state.currentLocation ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(submittingAttendance = true, errorMessage = null) }
            try {
                val authorization = "Bearer ${session.token}"
                val request = AttendanceActionRequestBody(
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    accuracyMeters = currentLocation.accuracyMeters,
                    capturedAt = currentLocation.capturedAt
                )

                if (isCheckIn) {
                    val response = api.checkIn(authorization, request)
                    _uiState.update {
                        it.copy(
                            submittingAttendance = false,
                            attendanceStatus = it.attendanceStatus.copy(
                                checkedInAt = response.checkInTime ?: Instant.now().toString()
                            ),
                            errorMessage = response.message
                        )
                    }
                } else {
                    val response = api.checkOut(authorization, request)
                    _uiState.update {
                        it.copy(
                            submittingAttendance = false,
                            attendanceStatus = it.attendanceStatus.copy(
                                checkedOutAt = response.checkOutTime ?: response.checkedOutAt ?: Instant.now().toString(),
                                status = response.status ?: "CHECKED_OUT"
                            ),
                            errorMessage = response.message
                        )
                    }
                }
                refreshAuthenticatedData()
            } catch (exception: HttpException) {
                val message = exception.asApiMessage(if (isCheckIn) "출근 처리에 실패했습니다." else "퇴근 처리에 실패했습니다.")
                if (exception.code() == 401) {
                    logout()
                }
                _uiState.update { it.copy(submittingAttendance = false, errorMessage = message) }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        submittingAttendance = false,
                        errorMessage = if (isCheckIn) "출근 처리에 실패했습니다." else "퇴근 처리에 실패했습니다."
                    )
                }
            }
        }
    }

    private fun refreshAuthenticatedData() {
        val session = _uiState.value.authSession ?: return
        viewModelScope.launch {
            try {
                val authorization = "Bearer ${session.token}"
                val today = api.getTodayAttendance(authorization)
                val company = api.getCompanySetting(authorization)
                _uiState.update {
                    it.copy(
                        attendanceStatus = TodayAttendanceStatus(
                            checkedInAt = today.checkInTime,
                            checkedOutAt = today.checkOutTime,
                            attendanceDate = today.attendanceDate,
                            status = today.status,
                            companyName = today.companyName ?: company.companyName
                        ),
                        companySetting = company.toUiModel()
                    )
                }
            } catch (exception: HttpException) {
                if (exception.code() == 401) {
                    logout()
                } else {
                    _uiState.update { it.copy(errorMessage = exception.asApiMessage("상태를 불러오지 못했습니다.")) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "상태를 불러오지 못했습니다.") }
            }
        }
    }

    private fun loadPublicCompanySetting() {
        viewModelScope.launch {
            runCatching { api.getPublicCompanySetting() }
                .getOrNull()
                ?.let { response ->
                    _uiState.update {
                        it.copy(
                            companySetting = response.toUiModel(),
                            attendanceStatus = it.attendanceStatus.copy(
                                companyName = response.companyName ?: it.attendanceStatus.companyName
                            )
                        )
                    }
                }
        }
    }

    private fun normalizeLoginError(exception: HttpException): String {
        val message = exception.asApiMessage("로그인에 실패했습니다.")
        return when {
            message.contains("이미 다른 단말이 등록") ->
                "이 계정은 다른 단말에 이미 등록되어 있습니다. 관리자에게 단말 초기화를 요청한 뒤 다시 로그인해 주세요."
            message.contains("사번 또는 비밀번호") ->
                "사번 또는 비밀번호가 올바르지 않습니다. 입력값을 다시 확인해 주세요."
            exception.code() >= 500 ->
                "서버 오류로 로그인하지 못했습니다. 잠시 후 다시 시도해 주세요."
            else -> message
        }
    }

    private fun com.attendance.androidapp.model.CompanySettingResponseBody.toUiModel(): CompanySetting {
        return CompanySetting(
            companyId = companyId,
            companyName = companyName ?: "OpenAI Seoul Office",
            latitude = latitude ?: 37.5665,
            longitude = longitude ?: 126.9780,
            allowedRadiusMeters = allowedRadiusMeters ?: 100,
            lateAfterTime = lateAfterTime
        )
    }
}

@Composable
private fun AttendanceApp(viewModel: AttendanceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locationTracker = androidx.compose.runtime.remember { LocationTracker(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateLocationPermission(granted)
    }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasLocationPermission) {
        viewModel.updateLocationPermission(hasLocationPermission)
    }

    LaunchedEffect(uiState.authSession != null, uiState.locationPermissionGranted) {
        if (uiState.authSession != null && !uiState.locationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(uiState.authSession != null, uiState.locationPermissionGranted) {
        if (uiState.authSession != null && uiState.locationPermissionGranted) {
            viewModel.onLocationLoading(true)
            locationTracker.start(
                onLocation = viewModel::updateCurrentLocation,
                onError = { viewModel.onLocationError("현재 위치를 가져오지 못했습니다.") }
            )
        } else {
            locationTracker.stop()
        }

        onDispose {
            locationTracker.stop()
        }
    }

    if (uiState.authSession == null) {
        LoginScreen(
            state = uiState,
            onEmployeeCodeChange = viewModel::updateEmployeeCode,
            onPasswordChange = viewModel::updatePassword,
            onLogin = viewModel::login
        )
    } else {
        AttendanceScreen(
            state = uiState,
            onRetryPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onCheckIn = viewModel::checkIn,
            onCheckOut = viewModel::checkOut,
            onClearError = viewModel::clearError,
            onLogout = viewModel::logout
        )
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    onEmployeeCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "출퇴근 체크",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF172033)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.companySetting.companyName} 출퇴근 서비스입니다. 로그인 후 현재 위치를 확인하고 출근과 퇴근을 기록해 보세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5A657A)
                )
                if (!state.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            color = Color(0xFFBE123C),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.employeeCode,
                    onValueChange = onEmployeeCodeChange,
                    label = { Text("사번") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("비밀번호") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogin,
                    enabled = !state.loadingLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1463FF))
                ) {
                    if (state.loadingLogin) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("로그인")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceScreen(
    state: AppUiState,
    onRetryPermission: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onClearError: () -> Unit,
    onLogout: () -> Unit
) {
    val currentLocation = state.currentLocation
    val companySetting = state.companySetting
    val nowInSeoul = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
    val effectiveAttendanceStatus = if (shouldEnableCheckInForNewDay(state.attendanceStatus.attendanceDate, nowInSeoul)) {
        state.attendanceStatus.copy(
            checkedInAt = null,
            checkedOutAt = null,
            attendanceDate = nowInSeoul.toLocalDate().toString(),
            status = null
        )
    } else {
        state.attendanceStatus
    }
    val distance = currentLocation?.let {
        DistanceUtils.calculateMeters(it, companySetting.latitude, companySetting.longitude)
    }

    val canCheckIn = state.authSession != null &&
        effectiveAttendanceStatus.checkedInAt == null &&
        !state.submittingAttendance &&
        currentLocation != null &&
        distance != null &&
        currentLocation.accuracyMeters <= 100 &&
        distance <= companySetting.allowedRadiusMeters

    val canCheckOut = state.authSession != null &&
        effectiveAttendanceStatus.checkedInAt != null &&
        effectiveAttendanceStatus.checkedOutAt == null &&
        !state.submittingAttendance &&
        currentLocation != null &&
        distance != null &&
        currentLocation.accuracyMeters <= 100 &&
        distance <= companySetting.allowedRadiusMeters

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEF3FB))
            .safeDrawingPadding()
    ) {
        if (!state.errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE3E3))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = Color(0xFFC92A2A),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LaunchedEffect(state.errorMessage) {
                kotlinx.coroutines.delay(2500)
                onClearError()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${state.authSession?.user?.name ?: "사용자"}님",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF172033)
                )
                Text(
                    text = "${state.authSession?.user?.employeeCode.orEmpty()} · 오늘 출근 ${formatTime(effectiveAttendanceStatus.checkedInAt)} / 퇴근 ${formatTime(effectiveAttendanceStatus.checkedOutAt)}",
                    color = Color(0xFF536076)
                )
                Text(
                    text = "${state.attendanceStatus.companyName ?: companySetting.companyName} 반경 ${companySetting.allowedRadiusMeters}m",
                    color = Color(0xFF6A7487)
                )
            }
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCE8FF))
            ) {
                Text("로그아웃", color = Color(0xFF1447B8))
            }
        }

        Surface(
            modifier = Modifier
                .weight(1.25f)
                .heightIn(min = 320.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFDDE7F4)
        ) {
            when {
                state.loadingLocation -> {
                    CenterMessage(
                        title = "위치 확인 중",
                        message = "현재 위치를 확인하고 있습니다."
                    )
                }

                !state.locationPermissionGranted -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("위치 권한이 필요합니다.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "권한을 허용하면 회사 반경 안에서만 출근과 퇴근 버튼이 활성화됩니다.",
                            textAlign = TextAlign.Center,
                            color = Color(0xFF5C677B)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetryPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1463FF))
                        ) {
                            Text("위치 권한 요청")
                        }
                    }
                }

                else -> {
                    AttendanceMapView(
                        modifier = Modifier.fillMaxSize(),
                        context = LocalContext.current,
                        companySetting = companySetting,
                        currentLocation = currentLocation
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text("오늘 상태", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        effectiveAttendanceStatus.checkedOutAt != null -> "오늘 퇴근까지 완료되었습니다."
                        effectiveAttendanceStatus.checkedInAt != null -> "출근 완료. 회사 반경 안에서 정확한 위치가 확인되면 퇴근 버튼이 활성화됩니다."
                        else -> "회사 반경 안에서 정확한 위치가 확인되면 출근 버튼이 활성화됩니다."
                    },
                    color = Color(0xFF536076)
                )
                Spacer(modifier = Modifier.height(12.dp))
                DetailLine("기준 위치", state.attendanceStatus.companyName ?: companySetting.companyName)
                DetailLine("상태", effectiveAttendanceStatus.status ?: "-")
                DetailLine("근무일", effectiveAttendanceStatus.attendanceDate ?: nowInSeoul.toLocalDate().toString())
                DetailLine("위치 정확도", currentLocation?.accuracyMeters?.let { "약 ${it.toInt()}m" } ?: "-")
                DetailLine("로그인 유지 만료", formatDate(state.authSession?.expiresAt))
                DetailLine("오늘 출근", formatTime(effectiveAttendanceStatus.checkedInAt))
                DetailLine("오늘 퇴근", formatTime(effectiveAttendanceStatus.checkedOutAt))

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCheckIn,
                    enabled = canCheckIn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1463FF))
                ) {
                    if (state.submittingAttendance && effectiveAttendanceStatus.checkedInAt == null) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("출근하기")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCheckOut,
                    enabled = canCheckOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    if (state.submittingAttendance && effectiveAttendanceStatus.checkedInAt != null && effectiveAttendanceStatus.checkedOutAt == null) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("퇴근하기")
                    }
                }

                if (distance != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "현재 거리: ${distance.toInt()}m",
                        color = Color(0xFF1447B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color(0xFF1463FF))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = Color(0xFF5C677B), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF6A7487))
        Text(value, color = Color(0xFF172033), fontWeight = FontWeight.Medium)
    }
}

private fun formatTime(value: String?): String {
    if (value.isNullOrBlank()) {
        return "-"
    }

    return parseDateTime(value)?.let { "%02d:%02d".format(it.hour, it.minute) }
        ?: value.takeLast(8).take(5)
}

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) {
        return "-"
    }

    return parseDateTime(value)?.toLocalDate()?.toString() ?: value
}

private fun parseDateTime(value: String): LocalDateTime? {
    return try {
        Instant.parse(value).atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
}

private fun shouldEnableCheckInForNewDay(attendanceDate: String?, nowInSeoul: LocalDateTime): Boolean {
    if (attendanceDate.isNullOrBlank()) {
        return false
    }

    val savedDate = runCatching { LocalDate.parse(attendanceDate) }.getOrNull() ?: return false
    return nowInSeoul.hour >= 1 && savedDate != nowInSeoul.toLocalDate()
}
