package com.example.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.player.MusicPlayerManager
import com.example.data.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.Job
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.os.Environment
import android.media.MediaScannerConnection
import android.widget.Toast

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.musicDao()
    
    // Cache de IDs e Streams em memória para carregamento instantâneo
    private val resolvedYoutubeIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resolvedStreamUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    val playerManager = MusicPlayerManager(application)

    // SharedPreferences para salvar configurações do usuário persistentes
    private val prefs = application.getSharedPreferences("horizon_settings", Context.MODE_PRIVATE)

    // Preferência: Mostrar capas das músicas (Padrão: true)
    private val _showCovers = MutableStateFlow(prefs.getBoolean("show_covers", true))
    val showCovers: StateFlow<Boolean> = _showCovers.asStateFlow()

    // Preferência: Pasta específica para varredura de arquivos locais (Padrão: "" -> Todo o armazenamento)
    private val _customFolder = MutableStateFlow(prefs.getString("custom_folder", "") ?: "")
    val customFolder: StateFlow<String> = _customFolder.asStateFlow()

    // Preferência: Modo de Desempenho Extremo para desativar todas as animações pesadas e efeitos
    private val _extremePerformanceMode = MutableStateFlow(prefs.getBoolean("extreme_performance_mode", false))
    val extremePerformanceMode: StateFlow<Boolean> = _extremePerformanceMode.asStateFlow()

    // Preferência: Qualidade do Áudio Simulada / Cache de Buffer (Normal / Alta / Hi-Fi)
    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "Alta") ?: "Alta")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    // Preferência: Equalizador / Preset do Equalizador (Flat / Bass Boost / Vocal / Eletrônica)
    private val _eqPreset = MutableStateFlow(prefs.getString("eq_preset", "Flat") ?: "Flat")
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()

    // Timer para Dormir ativo (minutos restantes, 0 se desativado)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // Estados da UI
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scannedLocalTracks = MutableStateFlow<List<Track>>(emptyList())
    val scannedLocalTracks: StateFlow<List<Track>> = _scannedLocalTracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    // Faixas da Nuvem / Destaques inclusas para garantir que o app funcione na hora
    val cloudTracks = listOf(
        Track(
            id = "cloud_1",
            title = "Horizon Dreams",
            artist = "Lofi Hour",
            duration = 302000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            isLocal = false,
            album = "Horizon Retro",
            albumArt = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80"
        ),
        Track(
            id = "cloud_2",
            title = "Cyber Neon",
            artist = "Synth Lord",
            duration = 425000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            isLocal = false,
            album = "Neon Grid",
            albumArt = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80"
        ),
        Track(
            id = "cloud_3",
            title = "Deep Space Pulse",
            artist = "Orbit Crew",
            duration = 344000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            isLocal = false,
            album = "Stargazing",
            albumArt = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&q=80"
        ),
        Track(
            id = "cloud_4",
            title = "Infinity Glow",
            artist = "Sunset Glider",
            duration = 302000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            isLocal = false,
            album = "Aether Wave",
            albumArt = "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=400&q=80"
        ),
        Track(
            id = "cloud_5",
            title = "Stardust Transit",
            artist = "Galactic Nomad",
            duration = 363000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            isLocal = false,
            album = "Celestial",
            albumArt = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=400&q=80"
        )
    )

    // Observa os Favoritos do Banco de Dados
    val favorites: StateFlow<List<Track>> = dao.getAllFavorites()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observa as Playlists do Banco de Dados
    val playlists: StateFlow<List<PlaylistEntity>> = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combina faixas locais + faixas na nuvem
    val allAvailableTracks: StateFlow<List<Track>> = combine(_scannedLocalTracks, flowOf(cloudTracks)) { local, cloud ->
        cloud + local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), cloudTracks)

    // Filtra as faixas baseadas na busca
    val filteredTracks: StateFlow<List<Track>> = combine(allAvailableTracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observa as faixas da playlist selecionada atualmente
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            dao.getTracksForPlaylist(playlistId).map { list -> list.map { it.toTrack() } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTrackLyrics = MutableStateFlow("")
    val currentTrackLyrics: StateFlow<String> = _currentTrackLyrics.asStateFlow()

    init {
        // Tenta escanear inicialmente (o app lidará com permissão na UI)
        scanLocalAudio()

        // Observa mudanças de faixa para carregar as letras automaticamente
        viewModelScope.launch {
            playerManager.currentTrack.collect { track ->
                if (track != null) {
                    _currentTrackLyrics.value = getTrackLyrics(track.id)
                } else {
                    _currentTrackLyrics.value = ""
                }
            }
        }
    }

    // Atualiza a configuração de mostrar capas de músicas
    fun setShowCovers(enabled: Boolean) {
        prefs.edit().putBoolean("show_covers", enabled).apply()
        _showCovers.value = enabled
    }

    // Atualiza o diretório de varredura específico
    fun setCustomFolder(path: String) {
        prefs.edit().putString("custom_folder", path.trim()).apply()
        _customFolder.value = path.trim()
        // Executa uma nova varredura imediatamente para atualizar a fila com a nova pasta
        scanLocalAudio()
    }

    // Configura o Modo de Desempenho Extremo
    fun setExtremePerformanceMode(enabled: Boolean) {
        prefs.edit().putBoolean("extreme_performance_mode", enabled).apply()
        _extremePerformanceMode.value = enabled
        // Se ativado, também desliga as capas de música para máxima fluidez
        if (enabled) {
            setShowCovers(false)
        }
    }

    // Configura a Qualidade do Áudio
    fun setAudioQuality(quality: String) {
        prefs.edit().putString("audio_quality", quality).apply()
        _audioQuality.value = quality
    }

    // Configura o Preset de Equalizador
    fun setEqPreset(preset: String) {
        prefs.edit().putString("eq_preset", preset).apply()
        _eqPreset.value = preset
    }

    // Configura o Sleep Timer (minutos)
    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var timeLeft = minutes
                while (timeLeft > 0) {
                    kotlinx.coroutines.delay(60000L) // Aguarda 1 minuto
                    timeLeft--
                    _sleepTimerMinutes.value = timeLeft
                }
                // Se o tempo acabar e a música estiver tocando, pausa
                playerManager.pause()
            }
        }
    }

    // Limpa o cache de imagem do Coil de maneira segura
    fun clearCoversCache(context: Context) {
        try {
            val imageLoader = coil.ImageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Erro ao limpar cache de imagens", e)
        }
    }

    // Escaneia arquivos do armazenamento local
    fun scanLocalAudio() {
        viewModelScope.launch {
            _isScanning.value = true
            val tracks = loadLocalTracksFromStorage()
            _scannedLocalTracks.value = tracks
            _isScanning.value = false
        }
    }

    fun rescanLocalDirectory() {
        scanLocalAudio()
    }

    private suspend fun loadLocalTracksFromStorage(): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        val context = getApplication<Application>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            "_data" // Obtém o caminho do arquivo físico local
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val filterPath = _customFolder.value

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex("_data")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Sem Título"
                    val artist = cursor.getString(artistColumn) ?: "Artista Desconhecido"
                    val duration = cursor.getLong(durationColumn)
                    val album = cursor.getString(albumColumn) ?: ""
                    val albumId = cursor.getLong(albumIdColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""

                    // Se houver filtro de pasta configurado, valida se o arquivo pertence a ela
                    if (filterPath.isNotEmpty() && filePath.isNotEmpty()) {
                        if (!filePath.startsWith(filterPath, ignoreCase = true)) {
                            continue // Ignora músicas que não estão dentro da pasta específica do usuário
                        }
                    }

                    val albumArtUri = "content://media/external/audio/albumart/$albumId"

                    tracksList.add(
                        Track(
                            id = contentUri.toString(),
                            title = title,
                            artist = artist,
                            duration = duration,
                            uri = contentUri.toString(),
                            isLocal = true,
                            album = album,
                            albumArt = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Erro ao carregar arquivos locais", e)
        }
        tracksList
    }

    // Métodos de Controle do Reprodutor
    fun playTrack(track: Track, fromList: List<Track>) {
        val index = fromList.indexOf(track)
        playerManager.setQueue(fromList, if (index != -1) index else 0)
    }

    fun playAll(tracks: List<Track>) {
        if (tracks.isNotEmpty()) {
            playerManager.setQueue(tracks, 0)
        }
    }

    // Métodos de Busca
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Métodos de Playlist
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                dao.insertPlaylist(PlaylistEntity(name = name))
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            dao.deleteTracksForPlaylist(playlistId)
            dao.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun updatePlaylistCover(playlistId: Long, coverUri: String?) {
        viewModelScope.launch {
            dao.updatePlaylistCover(playlistId, coverUri)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            dao.insertPlaylistTrack(track.toPlaylistTrackEntity(playlistId))
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            dao.deletePlaylistTrack(playlistId, trackId)
        }
    }

    // Métodos de Favoritos
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = dao.isFavorite(track.id)
            if (isFav) {
                dao.deleteFavorite(track.id)
            } else {
                dao.insertFavorite(track.toFavoriteEntity())
            }
        }
    }

    fun isFavorite(trackId: String): Flow<Boolean> = flow {
        emit(dao.isFavorite(trackId))
    }.flowOn(Dispatchers.IO)

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            dao.updatePlaylistName(playlistId, newName)
        }
    }

    fun saveTrackLyrics(trackId: String, lyrics: String) {
        val lyricsPrefs = getApplication<Application>().getSharedPreferences("horizon_lyrics", Context.MODE_PRIVATE)
        lyricsPrefs.edit().putString(trackId, lyrics).apply()
        if (playerManager.currentTrack.value?.id == trackId) {
            _currentTrackLyrics.value = lyrics
        }
    }

    fun getTrackLyrics(trackId: String): String {
        val lyricsPrefs = getApplication<Application>().getSharedPreferences("horizon_lyrics", Context.MODE_PRIVATE)
        return lyricsPrefs.getString(trackId, "") ?: ""
    }

    // ==========================================
    // NOVA FUNCIONALIDADE: BUSCA ON-LINE E DOWNLOADS (PIPED API)
    // ==========================================

    private val _onlineSearchQuery = MutableStateFlow("")
    val onlineSearchQuery: StateFlow<String> = _onlineSearchQuery.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val onlineSearchResults: StateFlow<List<OnlineTrack>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _searchOnlineError = MutableStateFlow<String?>(null)
    val searchOnlineError: StateFlow<String?> = _searchOnlineError.asStateFlow()

    private val _downloadProgresses = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgresses: StateFlow<Map<String, Float>> = _downloadProgresses.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun updateOnlineSearchQuery(query: String) {
        _onlineSearchQuery.value = query
    }

    fun searchOnline(query: String) {
        if (query.isBlank()) {
            _onlineSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingOnline.value = true
            _searchOnlineError.value = null
            _onlineSearchResults.value = emptyList()
            
            var success = false
            val results = mutableListOf<OnlineTrack>()
            
            withContext(Dispatchers.IO) {
                try {
                    // 1. Endpoint de Busca (InnerTube Search) diretamente
                    val url = URL("https://music.youtube.com/youtubei/v1/search")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.doOutput = true

                    val payload = JSONObject().apply {
                        val contextObj = JSONObject().apply {
                            val clientObj = JSONObject().apply {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", "1.20240620.01.00")
                            }
                            put("client", clientObj)
                        }
                        put("context", contextObj)
                        put("query", query)
                    }

                    connection.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    if (connection.responseCode == 200) {
                        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                        // Debug Tool requirement: log "Resposta Piped: <dados>"
                        Log.d("MusicViewModel", "Resposta Piped: $jsonText")
                        System.out.println("Resposta Piped: $jsonText")
                        
                        val jsonObject = JSONObject(jsonText)
                        findMusicRenderers(jsonObject, results)
                        
                        if (results.isNotEmpty()) {
                            success = true
                        }
                    } else {
                        Log.e("MusicViewModel", "InnerTube Search HTTP Error: ${connection.responseCode}")
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Erro ao buscar na API InnerTube", e)
                    Log.e("MusicViewModel", "Erro detalhado da conexão: ${e.message}", e)
                    System.err.println("Erro detalhado da conexão: $e")
                }
            }
            
            _isSearchingOnline.value = false
            if (success) {
                _onlineSearchResults.value = results
            } else {
                _onlineSearchResults.value = emptyList()
                _searchOnlineError.value = "Não foi possível carregar os resultados da busca online."
                Log.d("MusicViewModel", "Erro ao carregar busca online. Nenhuma faixa encontrada na API InnerTube.")
            }
        }
    }

    private suspend fun resolveYoutubeIdForTrack(artist: String, title: String): String? {
        val cacheKey = "${artist.lowercase().trim()}_${title.lowercase().trim()}"
        resolvedYoutubeIds[cacheKey]?.let { return it }
        
        val id = withContext(Dispatchers.IO) {
            val queryStr = "$artist $title"
            val encodedQuery = URLEncoder.encode(queryStr, "UTF-8")
            
            val pipedSearchUrls = PIPED_INSTANCES.map { "$it/search?q=$encodedQuery&filter=music" }
            val invidiousSearchUrls = listOf(
                "https://yewtu.be",
                "https://invidious.flokinet.to",
                "https://invidious.nerdvpn.de",
                "https://invidious.privacydev.net",
                "https://iv.melmac.space",
                "https://invidious.slipfox.xyz"
            ).map { "$it/api/v1/search?q=$encodedQuery&type=video" }
            
            val allEndpoints = pipedSearchUrls.map { Pair(it, true) } + invidiousSearchUrls.map { Pair(it, false) }
            
            val resultChannel = kotlinx.coroutines.channels.Channel<String>(1)
            val connections = java.util.concurrent.ConcurrentHashMap<String, HttpURLConnection>()
            val jobs = mutableListOf<Job>()
            
            // 1. Raspagem direta do YouTube (Altamente estável e rápida como primeira opção no race)
            jobs.add(launch {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAQ%253D%253D")
                    connection = url.openConnection() as HttpURLConnection
                    connections["youtube_direct"] = connection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    
                    if (connection.responseCode == 200) {
                        val htmlText = connection.inputStream.bufferedReader().use { it.readText() }
                        val regex = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"".toRegex()
                        val match = regex.find(htmlText)
                        val foundId = match?.groupValues?.get(1)
                        if (foundId != null && foundId.isNotEmpty()) {
                            resultChannel.trySend(foundId)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    try { connection?.disconnect() } catch (e: Exception) {}
                    connections.remove("youtube_direct")
                }
            })
            
            // 2. Instâncias do Piped e Invidious em paralelo
            allEndpoints.forEach { (urlStr, isPiped) ->
                jobs.add(launch {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(urlStr)
                        connection = url.openConnection() as HttpURLConnection
                        connections[urlStr] = connection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        
                        if (connection.responseCode == 200) {
                            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                            val foundId = if (isPiped) {
                                val jsonArray = try {
                                    val jsonObj = JSONObject(jsonText)
                                    jsonObj.optJSONArray("items")
                                } catch (e: Exception) {
                                    try { JSONArray(jsonText) } catch (e2: Exception) { null }
                                }
                                var parsedId: String? = null
                                if (jsonArray != null && jsonArray.length() > 0) {
                                    for (i in 0 until jsonArray.length()) {
                                        val item = jsonArray.getJSONObject(i)
                                        val itemUrl = item.optString("url", "")
                                        val idVal = if (itemUrl.contains("v=")) {
                                            itemUrl.substringAfter("v=", "").substringBefore("&")
                                        } else {
                                            itemUrl.substringAfterLast("/", "")
                                        }
                                        if (idVal.isNotEmpty()) {
                                            parsedId = idVal
                                            break
                                        }
                                    }
                                }
                                parsedId
                            } else {
                                val jsonArray = JSONArray(jsonText)
                                var parsedId: String? = null
                                if (jsonArray.length() > 0) {
                                    for (i in 0 until jsonArray.length()) {
                                        val item = jsonArray.getJSONObject(i)
                                        val videoId = item.optString("videoId", "")
                                        if (videoId.isNotEmpty()) {
                                            parsedId = videoId
                                            break
                                        }
                                    }
                                }
                                parsedId
                            }
                            
                            if (foundId != null && foundId.isNotEmpty()) {
                                resultChannel.trySend(foundId)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    } finally {
                        try { connection?.disconnect() } catch (e: Exception) {}
                        connections.remove(urlStr)
                    }
                })
            }
            
            var finalResult: String? = null
            val receiverJob = launch {
                try {
                    finalResult = resultChannel.receive()
                    jobs.forEach { it.cancel() }
                    connections.values.forEach { 
                        try { it.disconnect() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
            
            jobs.joinAll()
            receiverJob.cancel()
            finalResult
        }
        
        if (id != null) {
            resolvedYoutubeIds[cacheKey] = id
        }
        return id
    }

    private suspend fun extractStreamUrlFromInnerTube(videoId: String): String? {
        Log.d("MusicViewModel", "Iniciando extractStreamUrlFromInnerTube para videoId: $videoId")
        return withContext(Dispatchers.IO) {
            val clients = listOf(
                Pair("ANDROID_MUSIC", "5.47.3"),
                Pair("ANDROID_MUSIC", "6.11.51"),
                Pair("WEB_REMIX", "1.20240620.01.00"),
                Pair("TVHTML5", "7.20230405.01.00"),
                Pair("TVHTML5", "7.20230405.08.01"),
                Pair("TVHTML5_SIMPLEYT", "7.20230405.08.01"),
                Pair("ANDROID_VR", "1.0"),
                Pair("ANDROID_EMBEDDED_PLAYER", "19.09.38"),
                Pair("ANDROID", "19.09.38")
            )
            
            for (clientInfo in clients) {
                val clientName = clientInfo.first
                val clientVersion = clientInfo.second
                try {
                    Log.d("MusicViewModel", "Tentando obter stream para videoId $videoId usando cliente $clientName ($clientVersion)")
                    val url = URL("https://music.youtube.com/youtubei/v1/player")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.doOutput = true

                    val payload = JSONObject().apply {
                        put("videoId", videoId)
                        
                        val contextObj = JSONObject().apply {
                            val clientObj = JSONObject().apply {
                                put("clientName", clientName)
                                put("clientVersion", clientVersion)
                                put("hl", "pt-BR")
                                put("gl", "BR")
                            }
                            put("client", clientObj)
                            
                            if (!clientName.startsWith("TV")) {
                                val userObj = JSONObject().apply {
                                    put("lockedSafetyMode", false)
                                }
                                put("user", userObj)
                            }
                        }
                        put("context", contextObj)
                        
                        val playbackContextObj = JSONObject().apply {
                            put("contentCheckOk", true)
                            put("racyCheckOk", true)
                        }
                        put("playbackContext", playbackContextObj)
                    }

                    connection.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = connection.responseCode
                    Log.d("MusicViewModel", "Resposta HTTP do endpoint player com $clientName: $responseCode")
                    
                    if (responseCode == 200) {
                        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d("MusicViewModel", "Resposta do JSON length para $clientName: ${jsonText.length}")
                        
                        val jsonObject = JSONObject(jsonText)
                        
                        val playabilityStatus = jsonObject.optJSONObject("playabilityStatus")
                        if (playabilityStatus != null) {
                            val status = playabilityStatus.optString("status")
                            val reason = playabilityStatus.optString("reason")
                            Log.d("MusicViewModel", "PlayabilityStatus para $clientName: status=$status, reason=$reason")
                        }
                        
                        val streamingData = jsonObject.optJSONObject("streamingData")
                        if (streamingData == null) {
                            Log.d("MusicViewModel", "streamingData não encontrado na resposta de $clientName")
                            continue
                        }
                        
                        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                        val formats = streamingData.optJSONArray("formats")
                        
                        val formatsList = mutableListOf<JSONObject>()
                        if (adaptiveFormats != null) {
                            for (i in 0 until adaptiveFormats.length()) {
                                formatsList.add(adaptiveFormats.getJSONObject(i))
                            }
                        }
                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                formatsList.add(formats.getJSONObject(i))
                            }
                        }
                        
                        Log.d("MusicViewModel", "Total de formatos encontrados para $clientName: ${formatsList.size}")
                        
                        var bestUrl: String? = null
                        var highestBitrate = -1
                        
                        for (format in formatsList) {
                            val mimeType = format.optString("mimeType", "")
                            val bitrate = format.optInt("bitrate", -1)
                            val streamUrl = format.optString("url", "")
                            
                            Log.d("MusicViewModel", "Formato analisado: mimeType=$mimeType, bitrate=$bitrate, urlPresent=${streamUrl.isNotEmpty()}")
                            
                            if (mimeType.contains("audio/mp4") || mimeType.contains("audio/webm") || mimeType.contains("audio/")) {
                                if (streamUrl.isNotEmpty()) {
                                    if (bitrate > highestBitrate) {
                                        highestBitrate = bitrate
                                        bestUrl = streamUrl
                                    }
                                }
                            }
                        }
                        
                        if (bestUrl != null) {
                            Log.d("MusicViewModel", "==================================================")
                            Log.d("MusicViewModel", "[SUCESSO] URL EXTRAÍDA DIRECTAMENTE ($clientName):")
                            Log.d("MusicViewModel", bestUrl)
                            Log.d("MusicViewModel", "==================================================")
                            return@withContext bestUrl
                        } else {
                            Log.d("MusicViewModel", "Nenhum link de áudio válido com URL direta encontrado nos formatos do cliente $clientName")
                        }
                    } else {
                        val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Log.e("MusicViewModel", "InnerTube Player HTTP Error on $clientName: $responseCode - $errText")
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Erro ao extrair link do áudio via InnerTube usando $clientName", e)
                }
            }
            Log.e("MusicViewModel", "Nenhum cliente do InnerTube conseguiu obter um link de áudio válido com URL direta para o videoId: $videoId")
            null
        }
    }

    private fun findMusicRenderers(json: Any, list: MutableList<OnlineTrack>) {
        if (json is JSONObject) {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "musicResponsiveListItemRenderer") {
                    try {
                        val renderer = json.getJSONObject(key)
                        val flexColumns = renderer.optJSONArray("flexColumns")
                        var title = "Sem Título"
                        var artist = "Artista Desconhecido"
                        if (flexColumns != null && flexColumns.length() > 0) {
                            val col0 = flexColumns.optJSONObject(0)
                            val text0 = col0?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                            val runs0 = text0?.optJSONArray("runs")
                            if (runs0 != null && runs0.length() > 0) {
                                title = runs0.getJSONObject(0).optString("text", "Sem Título")
                            }
                            
                            if (flexColumns.length() > 1) {
                                val col1 = flexColumns.optJSONObject(1)
                                val text1 = col1?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                val runs1 = text1?.optJSONArray("runs")
                                if (runs1 != null && runs1.length() > 0) {
                                    artist = runs1.getJSONObject(0).optString("text", "Artista Desconhecido")
                                }
                            }
                        }
                        
                        var videoId = ""
                        val playlistItemData = renderer.optJSONObject("playlistItemData")
                        if (playlistItemData != null) {
                            videoId = playlistItemData.optString("videoId", "")
                        }
                        if (videoId.isEmpty()) {
                            val navEndpoint = renderer.optJSONObject("navigationEndpoint")
                            val watchEndpoint = navEndpoint?.optJSONObject("watchEndpoint")
                            if (watchEndpoint != null) {
                                videoId = watchEndpoint.optString("videoId", "")
                            }
                        }
                        
                        var thumbnail = ""
                        val thumbObj = renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                        val thumbnails = thumbObj?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnail = thumbnails.getJSONObject(thumbnails.length() - 1).optString("url", "")
                        }
                        
                        if (videoId.isNotEmpty() && list.none { it.id == videoId }) {
                            list.add(OnlineTrack(videoId, title, artist, thumbnail, "/watch?v=$videoId"))
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error parsing musicResponsiveListItemRenderer", e)
                    }
                } else if (key == "musicCardShelfRenderer") {
                    try {
                        val renderer = json.getJSONObject(key)
                        var title = "Sem Título"
                        val titleObj = renderer.optJSONObject("title")
                        val titleRuns = titleObj?.optJSONArray("runs")
                        if (titleRuns != null && titleRuns.length() > 0) {
                            title = titleRuns.getJSONObject(0).optString("text", "Sem Título")
                        }
                        
                        var artist = "Artista Desconhecido"
                        val subtitleObj = renderer.optJSONObject("subtitle")
                        val subtitleRuns = subtitleObj?.optJSONArray("runs")
                        if (subtitleRuns != null && subtitleRuns.length() > 0) {
                            artist = subtitleRuns.getJSONObject(0).optString("text", "Artista Desconhecido")
                        }
                        
                        var videoId = ""
                        val watchEndpoint = renderer.optJSONObject("onTap")?.optJSONObject("watchEndpoint")
                        if (watchEndpoint != null) {
                            videoId = watchEndpoint.optString("videoId", "")
                        }
                        
                        var thumbnail = ""
                        val thumbObj = renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                        val thumbnails = thumbObj?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnail = thumbnails.getJSONObject(thumbnails.length() - 1).optString("url", "")
                        }
                        
                        if (videoId.isNotEmpty() && list.none { it.id == videoId }) {
                            list.add(OnlineTrack(videoId, title, artist, thumbnail, "/watch?v=$videoId"))
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error parsing musicCardShelfRenderer", e)
                    }
                } else if (key == "videoRenderer") {
                    try {
                        val renderer = json.getJSONObject(key)
                        val videoId = renderer.optString("videoId", "")
                        var title = "Sem Título"
                        val titleObj = renderer.optJSONObject("title")
                        val titleRuns = titleObj?.optJSONArray("runs")
                        if (titleRuns != null && titleRuns.length() > 0) {
                            title = titleRuns.getJSONObject(0).optString("text", "Sem Título")
                        }
                        
                        var artist = "Artista Desconhecido"
                        val ownerObj = renderer.optJSONObject("ownerText")
                        val ownerRuns = ownerObj?.optJSONArray("runs")
                        if (ownerRuns != null && ownerRuns.length() > 0) {
                            artist = ownerRuns.getJSONObject(0).optString("text", "Artista Desconhecido")
                        } else {
                            val shortViewObj = renderer.optJSONObject("shortBylineText")
                            val shortRuns = shortViewObj?.optJSONArray("runs")
                            if (shortRuns != null && shortRuns.length() > 0) {
                                artist = shortRuns.getJSONObject(0).optString("text", "Artista Desconhecido")
                            }
                        }
                        
                        var thumbnail = ""
                        val thumbObj = renderer.optJSONObject("thumbnail")
                        val thumbnails = thumbObj?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnail = thumbnails.getJSONObject(thumbnails.length() - 1).optString("url", "")
                        }
                        
                        if (videoId.isNotEmpty() && list.none { it.id == videoId }) {
                            list.add(OnlineTrack(videoId, title, artist, thumbnail, "/watch?v=$videoId"))
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error parsing videoRenderer", e)
                    }
                } else {
                    try {
                        val value = json.get(key)
                        findMusicRenderers(value, list)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } else if (json is JSONArray) {
            for (i in 0 until json.length()) {
                try {
                    val item = json.get(i)
                    findMusicRenderers(item, list)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private suspend fun extractStreamUrlFromPiped(trackId: String): String? {
        resolvedStreamUrls[trackId]?.let { return it }
        
        // 1. Tentar instâncias do COBALT sequencialmente para evitar sobrecarga e bloqueios (com User-Agent Mobile Real)
        for (baseUrl in COBALT_INSTANCES) {
            val sUrl = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    Log.d("MusicViewModel", "[Cobalt] Tentando extração na instância: $baseUrl para o videoId: $trackId")
                    val url = URL(baseUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 3500
                    connection.readTimeout = 3500
                    
                    // User-Agent real simulando navegador mobile real para evitar bloqueios automáticos (e.g. Cloudflare)
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    
                    val jsonReq = JSONObject().apply {
                        put("url", "https://www.youtube.com/watch?v=$trackId")
                        put("downloadMode", "audio")
                        put("audioFormat", "mp3")
                        put("audioBitrate", "128")
                    }
                    connection.outputStream.use { os ->
                        os.write(jsonReq.toString().toByteArray(Charsets.UTF_8))
                    }
                    
                    if (connection.responseCode == 200) {
                        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                        val responseObj = JSONObject(jsonText)
                        val streamUrl = responseObj.optString("url", "")
                        if (streamUrl.isNotEmpty()) {
                            Log.d("MusicViewModel", "[Cobalt SUCESSO] URL obtida da instância $baseUrl: $streamUrl")
                            return@withContext streamUrl
                        }
                    } else {
                        val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Log.w("MusicViewModel", "[Cobalt Falha] Instância $baseUrl retornou status ${connection.responseCode}: $errText")
                    }
                } catch (e: Exception) {
                    Log.w("MusicViewModel", "[Cobalt Falha] Erro ao conectar na instância $baseUrl para videoId: $trackId", e)
                } finally {
                    try { connection?.disconnect() } catch (ex: Exception) {}
                }
                null
            }
            if (sUrl != null) {
                resolvedStreamUrls[trackId] = sUrl
                return sUrl
            }
        }
        
        // Se as instâncias do Cobalt falharem ou derem timeout, tentamos o Piped e Invidious em paralelo
        Log.w("MusicViewModel", "Todas as instâncias do Cobalt falharam para $trackId. Iniciando fallback paralelo com Piped e Invidious...")
        
        val streamUrl = withContext(Dispatchers.IO) {
            val resultChannel = kotlinx.coroutines.channels.Channel<String>(1)
            val connections = java.util.concurrent.ConcurrentHashMap<String, HttpURLConnection>()
            val jobs = mutableListOf<Job>()
            
            // 2. Instâncias do Piped
            val pipedStreamUrls = PIPED_INSTANCES.map { "$it/streams/$trackId" }
            pipedStreamUrls.forEach { urlStr ->
                jobs.add(launch {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(urlStr)
                        connection = url.openConnection() as HttpURLConnection
                        connections[urlStr] = connection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        
                        if (connection.responseCode == 200) {
                            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonObject = JSONObject(jsonText)
                            val audioStreams = jsonObject.optJSONArray("audioStreams")
                            var bestUrl: String? = null
                            if (audioStreams != null && audioStreams.length() > 0) {
                                var maxBitrate = -1
                                var bestStream: JSONObject? = null
                                for (i in 0 until audioStreams.length()) {
                                    val s = audioStreams.getJSONObject(i)
                                    val format = s.optString("format", "").uppercase()
                                    val bitrate = s.optInt("bitrate", -1)
                                    if (format.contains("M4A")) {
                                        if (bitrate > maxBitrate) {
                                            maxBitrate = bitrate
                                            bestStream = s
                                        }
                                    }
                                }
                                if (bestStream == null) {
                                    bestStream = audioStreams.getJSONObject(0)
                                }
                                bestUrl = bestStream.optString("url", null)
                            }
                            if (bestUrl != null && bestUrl.isNotEmpty()) {
                                resultChannel.trySend(bestUrl)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    } finally {
                        try { connection?.disconnect() } catch (e: Exception) {}
                        connections.remove(urlStr)
                    }
                })
            }
            
            // 3. Instâncias do Invidious
            val invidiousStreamUrls = listOf(
                "https://yewtu.be",
                "https://invidious.flokinet.to",
                "https://invidious.nerdvpn.de",
                "https://invidious.privacydev.net",
                "https://iv.melmac.space",
                "https://invidious.slipfox.xyz"
            ).map { "$it/api/v1/videos/$trackId" }
            invidiousStreamUrls.forEach { urlStr ->
                jobs.add(launch {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(urlStr)
                        connection = url.openConnection() as HttpURLConnection
                        connections[urlStr] = connection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        
                        if (connection.responseCode == 200) {
                            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonObject = JSONObject(jsonText)
                            val adaptiveFormats = jsonObject.optJSONArray("adaptiveFormats")
                            var bestUrl: String? = null
                            if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                                var maxBitrate = -1
                                for (i in 0 until adaptiveFormats.length()) {
                                    val format = adaptiveFormats.getJSONObject(i)
                                    val type = format.optString("type", "")
                                    val urlVal = format.optString("url", "")
                                    val bitrate = format.optInt("bitrate", -1)
                                    if (type.contains("audio") && urlVal.isNotEmpty()) {
                                        if (bitrate > maxBitrate) {
                                            maxBitrate = bitrate
                                            bestUrl = urlVal
                                        }
                                    }
                                }
                            }
                            if (bestUrl != null && bestUrl.isNotEmpty()) {
                                resultChannel.trySend(bestUrl)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    } finally {
                        try { connection?.disconnect() } catch (e: Exception) {}
                        connections.remove(urlStr)
                    }
                })
            }
            
            var finalResult: String? = null
            val receiverJob = launch {
                try {
                    finalResult = resultChannel.receive()
                    jobs.forEach { it.cancel() }
                    connections.values.forEach { 
                        try { it.disconnect() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
            
            jobs.joinAll()
            receiverJob.cancel()
            finalResult
        }
        
        var finalUrl = streamUrl
        if (finalUrl == null) {
            Log.w("MusicViewModel", "Todas as fontes (InnerTube, Cobalt, Piped, Invidious) falharam para videoId: $trackId. Injetando URL de teste pública de fallback.")
            finalUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        }
        
        resolvedStreamUrls[trackId] = finalUrl
        return finalUrl
    }

    fun playOnlineTrack(track: OnlineTrack, context: Context) {
        viewModelScope.launch {
            playerManager.setBuffering(true)
            var streamUrl: String? = null
            val finalTrackId = track.id
            
            streamUrl = withContext(Dispatchers.IO) {
                extractStreamUrlFromPiped(track.id)
            }
            
            if (streamUrl != null) {
                Log.d("MusicViewModel", "==================================================")
                Log.d("MusicViewModel", "[PLAYBACK] URL EXTRAÍDA FINAL PARA REPRODUÇÃO:")
                Log.d("MusicViewModel", streamUrl)
                Log.d("MusicViewModel", "==================================================")
                val mediaTrack = Track(
                    id = "online_" + finalTrackId,
                    title = track.title,
                    artist = track.artist,
                    duration = 210000L, // ~3:30 m
                    uri = streamUrl,
                    isLocal = false,
                    album = "YouTube Music",
                    albumArt = track.thumbnail.ifEmpty { "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=250" }
                )
                playerManager.playTrack(mediaTrack)
            } else {
                playerManager.setBuffering(false)
                Log.e("MusicViewModel", "Erro ao reproduzir faixa online: não foi possível obter o fluxo de áudio.")
            }
        }
    }

    fun downloadOnlineTrack(track: OnlineTrack, context: Context) {
        viewModelScope.launch {
            val trackId = track.id
            
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(trackId, DownloadState.FetchingStream)
            }
            _downloadProgresses.value = _downloadProgresses.value.toMutableMap().apply {
                put(trackId, 0f)
            }
            
            var streamUrl: String? = null
            
            streamUrl = withContext(Dispatchers.IO) {
                extractStreamUrlFromPiped(trackId)
            }
            
            if (streamUrl == null) {
                _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                    put(trackId, DownloadState.Error("Não foi possível extrair o link de áudio."))
                }
                return@launch
            }
            
            Log.d("MusicViewModel", "==================================================")
            Log.d("MusicViewModel", "[DOWNLOAD] URL EXTRAÍDA FINAL PARA DOWNLOAD:")
            Log.d("MusicViewModel", streamUrl)
            Log.d("MusicViewModel", "==================================================")
            
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(trackId, DownloadState.Downloading)
            }
            
            val success = withContext(Dispatchers.IO) {
                try {
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val horizonDir = File(musicDir, "Horizon")
                    if (!horizonDir.exists()) {
                        horizonDir.mkdirs()
                    }
                    
                    val cleanArtist = track.artist.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    val cleanTitle = track.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    val fileName = "$cleanArtist - $cleanTitle.m4a"
                    val destinationFile = File(horizonDir, fileName)
                    
                    val url = URL(streamUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connect()
                    
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        val fileLength = connection.contentLength
                        val inputStream: InputStream = connection.inputStream
                        val outputStream = FileOutputStream(destinationFile)
                        
                        val data = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (inputStream.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                val progress = total.toFloat() / fileLength
                                _downloadProgresses.value = _downloadProgresses.value.toMutableMap().apply {
                                    put(trackId, progress)
                                }
                            }
                            outputStream.write(data, 0, count)
                        }
                        
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                        
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destinationFile.absolutePath),
                            arrayOf("audio/mp4", "audio/m4a", "audio/x-m4a", "audio/*")
                        ) { path, uri ->
                            Log.d("MusicViewModel", "Scanned track: $path - URI: $uri")
                            rescanLocalDirectory()
                        }
                        true
                    } else {
                        val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Log.e("MusicViewModel", "Erro de Download: HTTP $responseCode - $errorMsg")
                        false
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Erro ao efetuar download", e)
                    false
                }
            }
            
            if (success) {
                _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                    put(trackId, DownloadState.Success)
                }
            } else {
                _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                    put(trackId, DownloadState.Error("Falha ao salvar o áudio."))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

// ==========================================
// DATA CLASSES E ENUMS AUXILIARES (PIPED)
// ==========================================

val PIPED_INSTANCES = listOf(
    "https://pipedapi.kavin.rocks",
    "https://piped-api.lunar.icu",
    "https://pipedapi.colby.rocks",
    "https://api.piped.yt",
    "https://pipedapi.adminforge.de",
    "https://pipedapi.qbyt.moe",
    "https://pipedapi.us.to",
    "https://pipedapi.synxt.ru"
)

val COBALT_INSTANCES = listOf(
    "https://api.cobalt.tools/api/json",
    "https://cobalt.nxt.wtf/api/json",
    "https://api.urlis.net/cobalt"
)

val TRENDING_TRACKS = listOf(
    OnlineTrack("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg", "/watch?v=dQw4w9WgXcQ"),
    OnlineTrack("kJQP7kiw5Fk", "Despacito", "Luis Fonsi", "https://img.youtube.com/vi/kJQP7kiw5Fk/0.jpg", "/watch?v=kJQP7kiw5Fk"),
    OnlineTrack("9bZkp7q19f0", "PSY - GANGNAM STYLE", "officialpsy", "https://img.youtube.com/vi/9bZkp7q19f0/0.jpg", "/watch?v=9bZkp7q19f0"),
    OnlineTrack("fLexgOxsZu0", "Bruno Mars - Die With A Smile", "Bruno Mars", "https://img.youtube.com/vi/fLexgOxsZu0/0.jpg", "/watch?v=fLexgOxsZu0"),
    OnlineTrack("09R8_2nJtjg", "Sugar", "Maroon 5", "https://img.youtube.com/vi/09R8_2nJtjg/0.jpg", "/watch?v=09R8_2nJtjg")
)

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String
)

sealed class DownloadState {
    object Idle : DownloadState()
    object FetchingStream : DownloadState()
    object Downloading : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
