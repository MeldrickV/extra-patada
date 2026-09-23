package com.xtra.kick.ui.channel

import android.annotation.SuppressLint
import android.net.http.HttpEngine
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xtra.kick.XtraApp
import com.xtra.kick.model.NotificationUser
import com.xtra.kick.model.ShownNotification
import com.xtra.kick.model.ui.LocalChannelFollow
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.model.ui.User
import com.xtra.kick.repository.BookmarksRepository
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.repository.LocalChannelFollowsRepository
import com.xtra.kick.repository.NotificationsRepository
import com.xtra.kick.repository.OfflineVideosRepository
import com.xtra.kick.util.C
import com.xtra.kick.util.KickSession
import com.xtra.kick.util.NetworkUtils
import com.xtra.kick.util.NetworkUtils.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.chromium.net.CronetEngine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import kotlin.time.Instant

class ChannelPagerViewModel(
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val notificationsRepository: NotificationsRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
    private val kickSession: KickSession,
    private val httpEngine: Lazy<HttpEngine?>,
    private val cronetEngine: Lazy<CronetEngine?>,
    private val cronetExecutor: Lazy<ExecutorService>,
    private val okHttpClient: Lazy<OkHttpClient>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    private val args = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val _notificationsEnabled = MutableStateFlow<Boolean?>(null)
    val notificationsEnabled: StateFlow<Boolean?> = _notificationsEnabled
    val notifications = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private var updatedLocalUser = false

    private val _stream = MutableStateFlow<Stream?>(null)
    val stream: StateFlow<Stream?> = _stream
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    fun loadStream(networkLibrary: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_stream.value == null) {
            if (args.channelId?.startsWith(C.KICK_USER_PREFIX) == true) {
                viewModelScope.launch {
                    val channel = runCatching {
                        kickRepository.getChannel(args.channelLogin ?: "")
                    }.getOrNull()
                    channel?.let { kickChannel ->
                        val login = args.channelLogin ?: kickChannel.slug
                        kickChannel.livestream?.takeIf { it.isLive }?.let {
                            _stream.value = Stream(
                                id = it.id.toString(),
                                channelId = args.channelId,
                                channelLogin = login,
                                channelName = kickChannel.user?.username,
                                channelImageURL = kickChannel.user?.profilePicture,
                                gameId = it.category?.id,
                                gameSlug = it.category?.slug,
                                gameName = it.category?.name,
                                title = it.sessionTitle,
                                viewerCount = it.viewerCount,
                                createdAt = it.startedAt,
                                platform = C.KICK,
                            )
                        }
                        _user.value = User(
                            id = args.channelId,
                            login = login,
                            name = kickChannel.user?.username,
                            profileImageURL = kickChannel.user?.profilePicture,
                            followerCount = kickChannel.followersCount.toInt(),
                            isLive = kickChannel.livestream?.isLive ?: false,
                            platform = C.KICK,
                        )
                    }
                }
            } else {
                viewModelScope.launch {
                try {
                    val response = graphQLRepository.loadQueryUserChannelPage(networkLibrary, gqlHeaders, args.channelId, if (args.channelId.isNullOrBlank()) args.channelLogin else null)
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            return@launch
                        }
                    }
                    response.data!!.user?.let {
                        _stream.value = Stream(
                            id = it.stream?.id,
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            channelImageURL = it.profileImageURL,
                            gameId = it.stream?.game?.id,
                            gameSlug = it.stream?.game?.slug,
                            gameName = it.stream?.game?.displayName,
                            title = it.stream?.title,
                            thumbnailURL = it.stream?.previewImageURL,
                            createdAt = it.stream?.createdAt?.toString(),
                            viewerCount = it.stream?.viewersCount,
                        )
                        _user.value = User(
                            id = it.id,
                            login = it.login,
                            name = it.displayName,
                            profileImageURL = it.profileImageURL,
                            type = when {
                                it.roles?.isStaff == true -> "staff"
                                else -> null
                            },
                            broadcasterType = when {
                                it.roles?.isPartner == true -> "partner"
                                it.roles?.isAffiliate == true -> "affiliate"
                                else -> null
                            },
                            createdAt = it.createdAt?.toString(),
                            followerCount = it.followers?.totalCount,
                            bannerImageURL = it.bannerImageURL,
                            lastBroadcast = it.lastBroadcast?.startedAt?.toString(),
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getStreams(
                                networkLibrary = networkLibrary,
                                headers = helixHeaders,
                                ids = args.channelId?.let { listOf(it) },
                                logins = if (args.channelId.isNullOrBlank()) args.channelLogin?.let { listOf(it) } else null
                            ).data.firstOrNull()?.let {
                                _stream.value = Stream(
                                    id = it.id,
                                    channelId = it.channelId,
                                    channelLogin = it.channelLogin,
                                    channelName = it.channelName,
                                    gameId = it.gameId,
                                    gameName = it.gameName,
                                    title = it.title,
                                    thumbnailURL = it.thumbnailURL,
                                    createdAt = it.startedAt,
                                    viewerCount = it.viewerCount,
                                    tags = it.tags,
                                )
                            }
                            helixRepository.getUsers(
                                networkLibrary = networkLibrary,
                                headers = helixHeaders,
                                ids = args.channelId?.let { listOf(it) },
                                logins = if (args.channelId.isNullOrBlank()) args.channelLogin?.let { listOf(it) } else null
                            ).data.firstOrNull()?.let {
                                _user.value = User(
                                    id = it.id,
                                    login = it.login,
                                    name = it.displayName,
                                    profileImageURL = it.profileImageURL,
                                    type = it.type,
                                    broadcasterType = it.broadcasterType,
                                    createdAt = it.createdAt,
                                )
                            }
                        } catch (e: Exception) {

                        }
                    }
                }
            }
            }
        }
    }

    fun enableNotifications(userId: String?, channelId: String?, setting: Int, notificationsEnabled: Boolean, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId && _isFollowing.value == true) {
                        val errorMessage = graphQLRepository.loadToggleNotificationsUser(networkLibrary, gqlHeaders, channelId, false).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("enableNotifications")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            notifications.value = Pair(true, errorMessage)
                        } else {
                            _notificationsEnabled.value = true
                            notifications.value = Pair(true, errorMessage)
                            if (notificationsEnabled) {
                                _stream.value?.createdAt.takeUnless { it.isNullOrBlank() }?.let {
                                    Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                                }?.let {
                                    notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        notificationsRepository.saveUser(NotificationUser(channelId))
                        _notificationsEnabled.value = true
                        notifications.value = Pair(true, null)
                        if (notificationsEnabled) {
                            _stream.value?.createdAt.takeUnless { it.isNullOrBlank() }?.let {
                                Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                            }?.let {
                                notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun disableNotifications(userId: String?, channelId: String?, setting: Int, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId && _isFollowing.value == true) {
                        val errorMessage = graphQLRepository.loadToggleNotificationsUser(networkLibrary, gqlHeaders, channelId, true).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("disableNotifications")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            notifications.value = Pair(false, errorMessage)
                        } else {
                            _notificationsEnabled.value = false
                            notifications.value = Pair(false, errorMessage)
                        }
                    } else {
                        notificationsRepository.deleteUser(NotificationUser(channelId))
                        _notificationsEnabled.value = false
                        notifications.value = Pair(false, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateNotifications(networkLibrary: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        viewModelScope.launch {
            notificationsRepository.getNewStreams(networkLibrary, gqlHeaders, helixHeaders, kickSession.accessToken())
        }
    }

    fun isFollowingChannel(userId: String?, channelId: String?, channelLogin: String?, setting: Int, networkLibrary: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (channelId.startsWith(C.KICK_USER_PREFIX)) {
                            val token = kickSession.accessToken()
                            val id = channelId.removePrefix(C.KICK_USER_PREFIX).toLongOrNull()
                            if (!token.isNullOrBlank()) {
                                val followedIds = runCatching {
                                    kickSession.repository.getFollowedChannels(token, 100, null).mapNotNull { it.id }
                                }.getOrDefault(emptyList())
                                _isFollowing.value = id != null && (id in followedIds || localChannelFollowsRepository.getById(channelId) != null)
                                _notificationsEnabled.value = notificationsRepository.getUserById(channelId) != null
                            } else {
                                _isFollowing.value = localChannelFollowsRepository.getById(channelId) != null
                                _notificationsEnabled.value = notificationsRepository.getUserById(channelId) != null
                            }
                            return@launch
                        }
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val follower = graphQLRepository.loadQueryFollowingUser(
                                networkLibrary = networkLibrary,
                                headers = gqlHeaders,
                                id = channelId,
                                login = channelLogin.takeIf { channelId.isBlank() },
                            ).data?.user?.self?.follower
                            _isFollowing.value = follower?.followedAt != null
                            _notificationsEnabled.value = follower?.notificationSettings?.isEnabled == true
                        } else {
                            _isFollowing.value = localChannelFollowsRepository.getById(channelId) != null
                            _notificationsEnabled.value = notificationsRepository.getUserById(channelId) != null
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(userId: String?, channelId: String?, channelLogin: String?, channelName: String?, setting: Int, liveNotificationsEnabled: Boolean, disableNotifications: Boolean, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (channelId.startsWith(C.KICK_USER_PREFIX)) {
                        val id = channelId.removePrefix(C.KICK_USER_PREFIX).toLongOrNull()
                        val token = kickSession.accessToken()
                        if (id == null) {
                            follow.value = Pair(true, kickSession.followFailedMessage())
                            return@launch
                        }
                        if (!token.isNullOrBlank()) {
                            val success = runCatching { kickSession.repository.followChannel(token, id) }.getOrDefault(false)
                            if (success) {
                                _isFollowing.value = true
                                follow.value = Pair(true, null)
                                localChannelFollowsRepository.getById(channelId)?.let { localChannelFollowsRepository.delete(it) }
                                if (!disableNotifications) {
                                    notificationsRepository.saveUser(NotificationUser(channelId))
                                    _notificationsEnabled.value = true
                                }
                                if (liveNotificationsEnabled) {
                                    kickStreamStartedAt(channelLogin)?.let {
                                        notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                    }
                                }
                            } else {
                                follow.value = Pair(true, kickSession.followFailedMessage())
                            }
                        } else {
                            localChannelFollowsRepository.save(LocalChannelFollow(channelId, channelLogin, channelName))
                            _isFollowing.value = true
                            follow.value = Pair(true, null)
                            if (!disableNotifications) {
                                notificationsRepository.saveUser(NotificationUser(channelId))
                                _notificationsEnabled.value = true
                            }
                            if (liveNotificationsEnabled) {
                                kickStreamStartedAt(channelLogin)?.let {
                                    notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                        return@launch
                    }
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadFollowUser(networkLibrary, gqlHeaders, channelId, disableNotifications).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("follow")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(true, errorMessage)
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, null)
                            if (!disableNotifications) {
                                _notificationsEnabled.value = true
                            }
                            if (liveNotificationsEnabled) {
                                _stream.value?.createdAt.takeUnless { it.isNullOrBlank() }?.let {
                                    Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                                }?.let {
                                    notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        localChannelFollowsRepository.save(LocalChannelFollow(channelId, channelLogin, channelName))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                        if (!disableNotifications) {
                            notificationsRepository.saveUser(NotificationUser(channelId))
                            _notificationsEnabled.value = true
                        }
                        if (liveNotificationsEnabled) {
                            _stream.value?.createdAt.takeUnless { it.isNullOrBlank() }?.let {
                                Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }
                            }?.let {
                                notificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(userId: String?, channelId: String?, setting: Int, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (channelId.startsWith(C.KICK_USER_PREFIX)) {
                        val id = channelId.removePrefix(C.KICK_USER_PREFIX).toLongOrNull()
                        val token = kickSession.accessToken()
                        if (id == null) {
                            follow.value = Pair(false, kickSession.followFailedMessage())
                            return@launch
                        }
                        if (!token.isNullOrBlank()) {
                            val success = runCatching { kickSession.repository.unfollowChannel(token, id) }.getOrDefault(false)
                            if (success) {
                                _isFollowing.value = false
                                follow.value = Pair(false, null)
                                localChannelFollowsRepository.getById(channelId)?.let { localChannelFollowsRepository.delete(it) }
                                notificationsRepository.deleteUser(NotificationUser(channelId))
                                _notificationsEnabled.value = false
                            } else {
                                follow.value = Pair(false, kickSession.followFailedMessage())
                            }
                        } else {
                            localChannelFollowsRepository.getById(channelId)?.let { localChannelFollowsRepository.delete(it) }
                            _isFollowing.value = false
                            follow.value = Pair(false, null)
                            notificationsRepository.deleteUser(NotificationUser(channelId))
                            _notificationsEnabled.value = false
                        }
                        return@launch
                    }
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadUnfollowUser(networkLibrary, gqlHeaders, channelId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("unfollow")
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(false, errorMessage)
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, null)
                            _notificationsEnabled.value = false
                        }
                    } else {
                        localChannelFollowsRepository.getById(channelId)?.let { localChannelFollowsRepository.delete(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                        notificationsRepository.deleteUser(NotificationUser(channelId))
                        _notificationsEnabled.value = false
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    private suspend fun kickStreamStartedAt(channelLogin: String?): Long? {
        if (channelLogin.isNullOrBlank()) return null
        return runCatching { kickSession.repository.getChannel(channelLogin).livestream?.startedAt }
            .getOrNull()
            ?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }
    }

    fun updateLocalUser(networkLibrary: String?, filesDir: String, user: User) {
        if (!updatedLocalUser) {
            updatedLocalUser = true
            user.id.takeIf { !it.isNullOrBlank() }?.let { userId ->
                viewModelScope.launch {
                    val downloadedLogo = user.profileImage.takeIf { !it.isNullOrBlank() }?.let { url ->
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                when {
                                    networkLibrary == C.HTTP_ENGINE && httpEngine.value != null -> @SuppressLint("NewApi") {
                                        val response = suspendCancellableCoroutine { continuation ->
                                            val timeout = NetworkUtils.HttpEngineTimeout()
                                            val request = httpEngine.value!!.newUrlRequestBuilder(
                                                url,
                                                cronetExecutor.value,
                                                NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
                                            ).build()
                                            timeout.start(request, continuation)
                                            request.start()
                                            continuation.invokeOnCancellation {
                                                request.cancel()
                                                timeout.stop()
                                            }
                                        }
                                        if (response.info.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.body)
                                            }
                                        }
                                    }
                                    networkLibrary == C.CRONET && cronetEngine.value != null -> {
                                        val response = suspendCancellableCoroutine { continuation ->
                                            val timeout = NetworkUtils.CronetTimeout()
                                            val request = cronetEngine.value!!.newUrlRequestBuilder(
                                                url,
                                                NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                                                cronetExecutor.value
                                            ).build()
                                            timeout.start(request, continuation)
                                            request.start()
                                            continuation.invokeOnCancellation {
                                                request.cancel()
                                                timeout.stop()
                                            }
                                        }
                                        if (response.info.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.body)
                                            }
                                        }
                                    }
                                    else -> {
                                        okHttpClient.value.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                                            if (response.isSuccessful) {
                                                FileOutputStream(path).use { outputStream ->
                                                    response.body.byteStream().use { inputStream ->
                                                        inputStream.copyTo(outputStream)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                    localChannelFollowsRepository.getById(userId)?.let {
                        localChannelFollowsRepository.update(it.apply {
                            userLogin = user.login
                            userName = user.name
                        })
                    }
                    offlineVideosRepository.getByUserId(userId).forEach {
                        offlineVideosRepository.update(it.apply {
                            channelLogin = user.login
                            channelName = user.name
                            channelLogo = downloadedLogo
                        })
                    }
                    bookmarksRepository.getByUserId(userId).forEach {
                        bookmarksRepository.update(it.apply {
                            userLogin = user.login
                            userName = user.name
                            userLogo = downloadedLogo
                        })
                    }
                }
            }
        }
    }

    companion object {
        val ChannelPagerViewModelFactory = viewModelFactory {
            initializer {
                val savedStateHandle = createSavedStateHandle()
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                ChannelPagerViewModel(xtraModule.localChannelFollowsRepository, xtraModule.offlineVideosRepository, xtraModule.bookmarksRepository, xtraModule.notificationsRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.kickRepository, KickSession(application, xtraModule.kickRepository), xtraModule.httpEngine, xtraModule.cronetEngine, xtraModule.cronetExecutor, xtraModule.okHttpClient, savedStateHandle)
            }
        }
    }
}
