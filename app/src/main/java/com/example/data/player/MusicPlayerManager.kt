package com.example.data.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.data.db.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode {
    OFF, ALL, ONE
}

class MusicPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    // Lista original antes de embaralhar (shuffle)
    private var originalQueue = listOf<Track>()

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration.toLong()
                    mp.start()
                    _isPlaying.value = true
                    startProgressPolling()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicPlayerManager", "MediaPlayer Error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    stopProgressPolling()
                    // Avança em caso de erro para não travar
                    skipToNext()
                    true
                }
            }
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        originalQueue = tracks
        if (_isShuffle.value) {
            val currentTrack = if (startIndex in tracks.indices) tracks[startIndex] else null
            val shuffled = tracks.shuffled().toMutableList()
            if (currentTrack != null) {
                shuffled.remove(currentTrack)
                shuffled.add(0, currentTrack)
            }
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            _queue.value = tracks
            _currentIndex.value = if (startIndex in tracks.indices) startIndex else 0
        }
        playCurrent()
    }

    fun playCurrent() {
        val tracks = _queue.value
        val index = _currentIndex.value
        if (index in tracks.indices) {
            val track = tracks[index]
            playTrack(track)
        }
    }

    fun playTrack(track: Track) {
        try {
            stopProgressPolling()
            _isPlaying.value = false
            _currentPosition.value = 0L
            _currentTrack.value = track

            mediaPlayer?.reset()
            if (track.isLocal) {
                val uri = Uri.parse(track.uri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mediaPlayer?.setDataSource(pfd.fileDescriptor)
                } ?: run {
                    mediaPlayer?.setDataSource(context, uri)
                }
            } else {
                mediaPlayer?.setDataSource(track.uri)
            }
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Erro ao iniciar faixa: ${track.title}", e)
            _isPlaying.value = false
            skipToNext()
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _isPlaying.value = false
            stopProgressPolling()
        } else {
            if (_currentTrack.value != null) {
                mp.start()
                _isPlaying.value = true
                startProgressPolling()
            } else {
                // Se a fila estiver vazia mas houver faixas, toca a primeira
                if (_queue.value.isNotEmpty()) {
                    _currentIndex.value = 0
                    playCurrent()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
        }
    }

    fun skipToNext() {
        val tracks = _queue.value
        if (tracks.isEmpty()) return

        val nextIndex = _currentIndex.value + 1
        if (nextIndex < tracks.size) {
            _currentIndex.value = nextIndex
            playCurrent()
        } else {
            if (_repeatMode.value == RepeatMode.ALL) {
                _currentIndex.value = 0
                playCurrent()
            } else {
                // Fim da fila
                _isPlaying.value = false
                _currentPosition.value = 0L
                stopProgressPolling()
            }
        }
    }

    fun skipToPrevious() {
        val mp = mediaPlayer ?: return
        if (_currentPosition.value > 3000) {
            // Se já passou de 3s, reinicia a música
            seekTo(0)
            return
        }

        val tracks = _queue.value
        if (tracks.isEmpty()) return

        val prevIndex = _currentIndex.value - 1
        if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            playCurrent()
        } else {
            if (_repeatMode.value == RepeatMode.ALL) {
                _currentIndex.value = tracks.size - 1
                playCurrent()
            } else {
                seekTo(0)
            }
        }
    }

    fun toggleShuffle() {
        val currentShuffle = !_isShuffle.value
        _isShuffle.value = currentShuffle
        
        val activeTrack = _currentTrack.value
        val tracks = originalQueue
        
        if (currentShuffle) {
            val shuffled = tracks.shuffled().toMutableList()
            if (activeTrack != null) {
                shuffled.remove(activeTrack)
                shuffled.add(0, activeTrack)
            }
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            _queue.value = tracks
            val originalIndex = tracks.indexOf(activeTrack)
            _currentIndex.value = if (originalIndex != -1) originalIndex else 0
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private fun handleTrackCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0)
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressPolling()
            }
            else -> {
                skipToNext()
            }
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPosition.value = mp.currentPosition.toLong()
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        playerScope.cancel()
        stopProgressPolling()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
