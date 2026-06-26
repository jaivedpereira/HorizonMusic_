package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import android.content.Context
import androidx.compose.ui.layout.ContentScale
import com.example.ui.OnlineTrack
import com.example.ui.DownloadState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.db.PlaylistEntity
import com.example.data.db.Track
import com.example.data.player.RepeatMode
import com.example.ui.MusicViewModel
import com.example.ui.components.HorizonCanvasVisualizer
import com.example.ui.components.SimpleBarVisualizer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizonHomeScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe Flows from ViewModel
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val allTracks by viewModel.allAvailableTracks.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    
    val currentTrack by viewModel.playerManager.currentTrack.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPosition by viewModel.playerManager.currentPosition.collectAsState()
    val duration by viewModel.playerManager.duration.collectAsState()
    val queue by viewModel.playerManager.queue.collectAsState()
    val isShuffle by viewModel.playerManager.isShuffle.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsState()

    // Configurações e preferências
    val showCovers by viewModel.showCovers.collectAsState()
    val customFolder by viewModel.customFolder.collectAsState()
    val extremePerformanceMode by viewModel.extremePerformanceMode.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val eqPreset by viewModel.eqPreset.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()

    // UI States locais
    var currentTab by remember { mutableStateOf(0) } // 0: Músicas, 1: Playlists, 2: Favoritos
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var playlistDetailToShow by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    // Diálogos e Modais
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<Track?>(null) }
    var isSettingsScreenOpen by remember { mutableStateOf(false) }

    // Interceptar o botão voltar físico/sistema para fechar telas secundárias/overlays
    BackHandler(enabled = isSettingsScreenOpen) {
        isSettingsScreenOpen = false
    }

    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    BackHandler(enabled = playlistDetailToShow != null) {
        playlistDetailToShow = null
    }

    // Verificação e Solicitação de Permissões
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
        if (isGranted) {
            viewModel.scanLocalAudio()
            Toast.makeText(context, "Armazenamento sincronizado!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão negada. Apenas faixas da nuvem estarão disponíveis.", Toast.LENGTH_LONG).show()
        }
    }

    // Efeito para sincronizar localmente se já tiver a permissão
    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            viewModel.scanLocalAudio()
        }
    }

    // Solicitação de permissão de notificação para Android 13+ (necessário para o foreground service)
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (notificationPermission != null) {
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && notificationPermission != null && !hasNotificationPermission) {
            notificationLauncher.launch(notificationPermission)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBlack,
            topBar = {
                if (!isPlayerExpanded && !isSettingsScreenOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBlack)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Logo do Horizon Music
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(HorizonSunset, CircleShape)
                                    .graphicsLayer {
                                        shadowElevation = 8f
                                    }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Horizon Music",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1.5).sp,
                                    color = TextWhite
                                )
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (hasStoragePermission) {
                                        viewModel.scanLocalAudio()
                                        Toast.makeText(context, "Escanando armazenamento...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        launcher.launch(storagePermission)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Escanear armazenamento",
                                    tint = TextWhite
                                )
                            }

                            IconButton(
                                onClick = { isSettingsScreenOpen = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Ajustes",
                                    tint = TextWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Barra de busca minimalista
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Pesquisar músicas, artistas...", color = TextMuted, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Procurar",
                                tint = TextMuted
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Limpar busca",
                                        tint = TextMuted
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSlate,
                            unfocusedContainerColor = ObsidianSlate,
                            disabledContainerColor = ObsidianSlate,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("search_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Navegação por abas customizada (Pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("Músicas", "Playlists", "Favoritos", "Busca On-line").forEachIndexed { index, title ->
                            val selected = currentTab == index
                            val bgValue by animateColorAsState(
                                targetValue = if (selected) HorizonSunset else ObsidianSlate,
                                animationSpec = tween(200)
                            )
                            val textValue by animateColorAsState(
                                targetValue = if (selected) Color.Black else TextMuted,
                                animationSpec = tween(200)
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgValue)
                                    .clickable {
                                        currentTab = index
                                        playlistDetailToShow = null // Reseta detalhes de playlist ao mudar de aba
                                    }
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textValue
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isPlayerExpanded && !isSettingsScreenOpen && currentTrack != null) {
                // Mini Player flutuante com visual glassmorphic elegante
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianSlate.copy(alpha = 0.95f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .clickable { isPlayerExpanded = true }
                        .testTag("mini_player")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Capa rotativa customizada do Horizon ou ícone alternativo
                        TrackCoverArt(
                            track = currentTrack!!,
                            showCovers = showCovers,
                            isPlaying = isPlaying,
                            extremePerformanceMode = extremePerformanceMode,
                            modifier = Modifier.size(46.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Título e Artista
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack?.title ?: "Sem faixa tocando",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack?.artist ?: "Nenhum artista",
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Mini visualizador de frequências bar
                        SimpleBarVisualizer(
                            isPlaying = isPlaying,
                            extremePerformanceMode = extremePerformanceMode,
                            modifier = Modifier
                                .height(20.dp)
                                .width(28.dp)
                                .padding(end = 6.dp),
                            barCount = 4,
                            barColor = HorizonSunset.copy(alpha = 0.8f)
                        )

                        // Botão Play/Pause
                        IconButton(
                            onClick = { viewModel.playerManager.togglePlayPause() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = HorizonSunset,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = TextWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Botão Próximo
                        IconButton(
                            onClick = { viewModel.playerManager.skipToNext() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Próxima faixa",
                                tint = TextWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    
                    // Barra de progresso ultrafina no miniplayer
                    val progressRatio = if (duration > 0) currentPosition.toFloat() / duration else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                            .align(Alignment.BottomStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .fillMaxHeight()
                                .background(HorizonSunset)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        
        // Área Principal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .drawBehind {
                    // Gradiente atmosférico sutil no fundo
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TwilightGlow.copy(alpha = 0.04f),
                                ObsidianBlack,
                                HorizonSunset.copy(alpha = 0.03f)
                            )
                        )
                    )
                }
        ) {
            when (currentTab) {
                0 -> {
                    // ABA MÚSICAS
                    TracksListSection(
                        hasPermission = hasStoragePermission,
                        tracks = filteredTracks,
                        favorites = favorites,
                        showCovers = showCovers,
                        isScanning = isScanning,
                        onRequestPermission = { launcher.launch(storagePermission) },
                        onTrackSelected = { track ->
                            viewModel.playTrack(track, filteredTracks)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onAddToPlaylist = { showAddToPlaylistDialogForTrack = it }
                    )
                }
                1 -> {
                    // ABA PLAYLISTS
                    if (playlistDetailToShow != null) {
                        // Detalhes da Playlist selecionada
                        PlaylistDetailSection(
                            playlist = playlistDetailToShow!!,
                            viewModel = viewModel,
                            favorites = favorites,
                            showCovers = showCovers,
                            onBack = { playlistDetailToShow = null },
                            onTrackSelected = { track, list ->
                                viewModel.playTrack(track, list)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                    } else {
                        // Lista de todas as playlists
                        PlaylistsListSection(
                            playlists = playlists,
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onPlaylistClick = { playlistDetailToShow = it },
                            onDeletePlaylist = { viewModel.deletePlaylist(it.id) }
                        )
                    }
                }
                2 -> {
                    // ABA FAVORITOS
                    FavoritesSection(
                        favorites = favorites,
                        showCovers = showCovers,
                        onTrackSelected = { track ->
                            viewModel.playTrack(track, favorites)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onExploreClick = { currentTab = 0 },
                        onAddToPlaylist = { showAddToPlaylistDialogForTrack = it }
                    )
                }
                3 -> {
                    // ABA BUSCA ON-LINE (PIPED & YOUTUBE MUSIC-LIKE DESIGN)
                    OnlineSearchSection(
                        viewModel = viewModel,
                        context = context
                    )
                }
            }
        }

        // Expanded Player View (Sobreposição deslizante)
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        ) {
            if (currentTrack != null) {
                FullPlayerView(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    position = currentPosition,
                    duration = duration,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    favorites = favorites,
                    showCovers = showCovers,
                    extremePerformanceMode = extremePerformanceMode,
                    viewModel = viewModel,
                    onCollapse = { isPlayerExpanded = false },
                    onPlayPauseToggle = { viewModel.playerManager.togglePlayPause() },
                    onNext = { viewModel.playerManager.skipToNext() },
                    onPrevious = { viewModel.playerManager.skipToPrevious() },
                    onSeek = { viewModel.playerManager.seekTo(it) },
                    onShuffleToggle = { viewModel.playerManager.toggleShuffle() },
                    onRepeatToggle = { viewModel.playerManager.toggleRepeat() },
                    onFavoriteToggle = { viewModel.toggleFavorite(currentTrack!!) },
                    onAddToPlaylist = { showAddToPlaylistDialogForTrack = currentTrack }
                )
            }
        }

        // Modais / Diálogos
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                    Toast.makeText(context, "Playlist '$name' criada!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showAddToPlaylistDialogForTrack != null) {
            AddToPlaylistDialog(
                track = showAddToPlaylistDialogForTrack!!,
                playlists = playlists,
                onDismiss = { showAddToPlaylistDialogForTrack = null },
                onPlaylistSelected = { playlist ->
                    viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!)
                    showAddToPlaylistDialogForTrack = null
                    Toast.makeText(context, "Música adicionada à playlist '${playlist.name}'", Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylist = { name ->
                    viewModel.createPlaylist(name)
                }
            )
        }
    }

    AnimatedVisibility(
        visible = isSettingsScreenOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        FullSettingsScreen(
            showCovers = showCovers,
            customFolder = customFolder,
            extremePerformanceMode = extremePerformanceMode,
            audioQuality = audioQuality,
            eqPreset = eqPreset,
            sleepTimerMinutes = sleepTimerMinutes,
            onShowCoversChanged = { viewModel.setShowCovers(it) },
            onCustomFolderChanged = { viewModel.setCustomFolder(it) },
            onExtremePerformanceModeChanged = { viewModel.setExtremePerformanceMode(it) },
            onAudioQualityChanged = { viewModel.setAudioQuality(it) },
            onEqPresetChanged = { viewModel.setEqPreset(it) },
            onSleepTimerChanged = { viewModel.setSleepTimer(it) },
            onClearCoversCache = { viewModel.clearCoversCache(context) },
            onScanAudio = { viewModel.scanLocalAudio() },
            onBack = { isSettingsScreenOpen = false }
        )
    }
}
}

// ---------------------- SUB-SEÇÕES DA DASHBOARD ----------------------

@Composable
fun TracksListSection(
    hasPermission: Boolean,
    tracks: List<Track>,
    favorites: List<Track>,
    showCovers: Boolean,
    isScanning: Boolean,
    onRequestPermission: () -> Unit,
    onTrackSelected: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        
        // Banner informativo sobre permissões de armazenamento se não concedido
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ObsidianSlate, ObsidianGray)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = "Armazenamento",
                            tint = HorizonSunset,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Importar Músicas Locais",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Escaneie o armazenamento do seu aparelho para carregar seus arquivos MP3 nativos.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(38.dp)
                    ) {
                        Text("Escanear", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HorizonSunset)
            }
        } else if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Sem músicas",
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma música encontrada",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tente escanear novamente ou limpe o campo de pesquisa.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
            ) {
                item {
                    // Featured Card (Horizon Flow - Personal Mix) from Elegant Dark design
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(150.dp)
                            .testTag("featured_flow_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(ObsidianGray, ObsidianSlate)
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = HorizonSunset.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(52.dp)
                                    .align(Alignment.TopEnd)
                            )

                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(
                                    text = "PERSONAL MIX",
                                    color = TwilightGlow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Horizon Flow",
                                    color = TextWhite,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Com base nas suas músicas locais",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Seção de Cabeçalho da Lista
                    Text(
                        text = "Recentemente Tocadas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(tracks, key = { it.id }) { track ->
                    val isFav = favorites.any { it.id == track.id }
                    TrackRowItem(
                        track = track,
                        isFavorite = isFav,
                        showCovers = showCovers,
                        onPlayClick = { onTrackSelected(track) },
                        onFavoriteToggle = { onToggleFavorite(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsListSection(
    playlists: List<PlaylistEntity>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Minhas Playlists",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextWhite
            )
            
            Button(
                onClick = onCreatePlaylistClick,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSlate),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Criar Playlist",
                    tint = HorizonSunset,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Criar", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = "Nenhuma playlist",
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sem playlists no Horizon",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crie coleções personalizadas para seus momentos.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(playlists) { playlist ->
                    PlaylistItemCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDelete = { onDeletePlaylist(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailSection(
    playlist: PlaylistEntity,
    viewModel: MusicViewModel,
    favorites: List<Track>,
    showCovers: Boolean,
    onBack: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit
) {
    val tracks by viewModel.selectedPlaylistTracks.collectAsState()
    val context = LocalContext.current

    // Launcher para selecionar imagem da galeria
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("PlaylistDetail", "Error taking persistable permission", e)
            }
            viewModel.updatePlaylistCover(playlist.id, uri.toString())
        }
    }

    var isRenaming by remember { mutableStateOf(false) }
    var renameNameText by remember { mutableStateOf(playlist.name) }

    if (isRenaming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("Renomear Playlist", color = TextWhite) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameNameText,
                    onValueChange = { renameNameText = it },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = HorizonSunset,
                        unfocusedBorderColor = ObsidianGray
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (renameNameText.isNotBlank()) {
                            viewModel.renamePlaylist(playlist.id, renameNameText)
                            isRenaming = false
                        }
                    }
                ) {
                    Text("Salvar", color = HorizonSunset)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { isRenaming = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
            containerColor = ObsidianGray
        )
    }

    LaunchedEffect(playlist.id) {
        viewModel.selectPlaylist(playlist.id)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Topbar da Playlist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.selectPlaylist(null)
                    onBack()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Voltar",
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Capa da playlist clicável para alterar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianGray)
                    .clickable { launcher.launch(arrayOf("image/*")) },
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUri != null) {
                    AsyncImage(
                        model = playlist.coverUri,
                        contentDescription = "Capa da playlist",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = HorizonSunset,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Badge pequeno indicando que pode editar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(2.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Alterar capa",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = TextWhite,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = {
                            renameNameText = playlist.name
                            isRenaming = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Renomear Playlist",
                            tint = HorizonSunset,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${tracks.size} faixas",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // Botão flutuante Play All se tiver faixas
        if (tracks.isNotEmpty()) {
            Button(
                onClick = { viewModel.playAll(tracks) },
                colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Tocar todas",
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("TOCAR TODAS", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Vazia",
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sua playlist está vazia",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vá para a aba de Músicas e adicione faixas aqui.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.clickable { onBack() }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isFav = favorites.any { it.id == track.id }
                    TrackRowItem(
                        track = track,
                        isFavorite = isFav,
                        showCovers = showCovers,
                        onPlayClick = { onTrackSelected(track, tracks) },
                        onFavoriteToggle = { onToggleFavorite(track) },
                        onAddToPlaylist = null, // já está na playlist, podemos adicionar ação de remover
                        onRemoveFromPlaylist = {
                            viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesSection(
    favorites: List<Track>,
    showCovers: Boolean,
    onTrackSelected: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onExploreClick: () -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Músicas Favoritas",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextWhite,
            modifier = Modifier.padding(16.dp)
        )

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = "Nenhum favorito",
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sem favoritos ainda",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque no ícone de coração nas músicas para guardá-las.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSlate),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Text("Explorar Horizon", color = TextWhite)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(favorites, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isFavorite = true,
                        showCovers = showCovers,
                        onPlayClick = { onTrackSelected(track) },
                        onFavoriteToggle = { onToggleFavorite(track) },
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    }
}

// ---------------------- PEQUENOS COMPONENTES REUTILIZÁVEIS ----------------------

@Composable
fun TrackRowItem(
    track: Track,
    isFavorite: Boolean,
    showCovers: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Capa customizada ou ícone procedural minimalista dependendo das configurações
        TrackCoverArt(
            track = track,
            showCovers = showCovers,
            isPlaying = false,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Dados da faixa
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.album.isNotEmpty()) {
                    Text(
                        text = " • ${track.album}",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Tempo
        Text(
            text = formatDuration(track.duration),
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Botão Coração
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) HorizonGold else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        // Ações adicionais (Adicionar ou Remover)
        if (onRemoveFromPlaylist != null) {
            IconButton(onClick = onRemoveFromPlaylist, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remover da Playlist",
                    tint = TextMuted
                )
            }
        } else if (onAddToPlaylist != null) {
            IconButton(onClick = { onAddToPlaylist(track) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistAdd,
                    contentDescription = "Adicionar à Playlist",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
fun PlaylistItemCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Vinyl icon decoration
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.coverUri != null) {
                        AsyncImage(
                            model = playlist.coverUri,
                            contentDescription = "Capa da playlist",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = HorizonSunset,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = playlist.name,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Playlist Horizon",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Delete button tucked at the top right
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Excluir Playlist",
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------------------- REPRODUTOR EM TELA CHEIA (GORGEOUS) ----------------------

@Composable
fun FullPlayerView(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    position: Long,
    duration: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    favorites: List<Track>,
    showCovers: Boolean,
    extremePerformanceMode: Boolean = false,
    viewModel: MusicViewModel,
    onCollapse: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val isFav = favorites.any { it.id == track.id }
    var playerTab by remember { mutableStateOf("Música") } // "Música", "Letras", "Fila"
    
    // UI Layout do Reprodutor Expandido
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* Consumir cliques para evitar propagação para as músicas atrás */ }
            .testTag("full_player")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header do Player
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimizar",
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Text(
                    text = "TOCANDO AGORA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextMuted
                )
                
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = "Adicionar à Playlist",
                        tint = TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Seleção de Abas do Player
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianGray)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Música", "Letras", "Fila").forEach { tab ->
                    val isSelected = playerTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) HorizonSunset else Color.Transparent)
                            .clickable { playerTab = tab }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else TextMuted
                        )
                    }
                }
            }

            // Área de Capa / Visualizador de Acordo com Ajustes de Desempenho
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (playerTab == "Música") {
                    if (showCovers) {
                        // Grande CD/Vinil giratório de alta fidelidade
                        TrackCoverArt(
                            track = track,
                            showCovers = true,
                            isPlaying = isPlaying,
                            extremePerformanceMode = extremePerformanceMode,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .aspectRatio(1f)
                        )
                    } else {
                        // Procedural Canvas Horizon Sunset Visualizer (Máximo desempenho/fluidez)
                        HorizonCanvasVisualizer(
                            isPlaying = isPlaying,
                            extremePerformanceMode = extremePerformanceMode,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f)
                        )
                    }
                } else if (playerTab == "Letras") {
                    val lyrics by viewModel.currentTrackLyrics.collectAsState()
                    var isEditingLyrics by remember { mutableStateOf(false) }
                    var lyricsInputText by remember { mutableStateOf("") }

                    if (isEditingLyrics) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { isEditingLyrics = false },
                            title = { Text("Editar Letra", color = TextWhite) },
                            text = {
                                androidx.compose.material3.OutlinedTextField(
                                    value = lyricsInputText,
                                    onValueChange = { lyricsInputText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedBorderColor = HorizonSunset,
                                        unfocusedBorderColor = ObsidianGray
                                    ),
                                    placeholder = { Text("Cole ou digite a letra aqui...", color = TextMuted) }
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        viewModel.saveTrackLyrics(track.id, lyricsInputText)
                                        isEditingLyrics = false
                                    }
                                ) {
                                    Text("Salvar", color = HorizonSunset)
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { isEditingLyrics = false }) {
                                    Text("Cancelar", color = TextMuted)
                                }
                            },
                            containerColor = ObsidianGray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianSlate)
                            .padding(16.dp)
                    ) {
                        if (lyrics.isBlank()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EditNote,
                                    contentDescription = null,
                                    tint = HorizonSunset.copy(alpha = 0.6f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nenhuma letra salva para esta música.",
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        lyricsInputText = lyrics
                                        isEditingLyrics = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Adicionar Letra", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Letra da Música",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HorizonSunset,
                                        letterSpacing = 1.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            lyricsInputText = lyrics
                                            isEditingLyrics = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Editar letra",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = lyrics,
                                        fontSize = 16.sp,
                                        color = TextWhite,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (playerTab == "Fila") {
                    val queue by viewModel.playerManager.queue.collectAsState()
                    val queueIndex by viewModel.playerManager.currentIndex.collectAsState()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianSlate)
                            .padding(12.dp)
                    ) {
                        if (queue.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "A fila está vazia",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Fila de Reprodução (${queue.size} faixas)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HorizonSunset,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(queue) { index, item ->
                                        val isCurrent = index == queueIndex
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrent) ObsidianGray else Color.Transparent)
                                                .clickable { viewModel.playerManager.playTrack(item) }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Mini capa
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ObsidianGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (showCovers && item.albumArt != null) {
                                                    AsyncImage(
                                                        model = item.albumArt,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MusicNote,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) HorizonSunset else TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) HorizonSunset else TextWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.artist,
                                                    fontSize = 11.sp,
                                                    color = if (isCurrent) HorizonGold else TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (isCurrent) {
                                                Text(
                                                    text = "Tocando",
                                                    color = HorizonSunset,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = formatDuration(item.duration),
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Detalhes da música atual
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.artist,
                            fontSize = 15.sp,
                            color = HorizonSunset,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favoritar",
                            tint = if (isFav) HorizonGold else TextWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                
                if (track.album.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ObsidianSlate)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.album,
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de Progresso e Timers
            Column(modifier = Modifier.fillMaxWidth()) {
                val progressRatio = if (duration > 0) position.toFloat() / duration else 0f
                
                Slider(
                    value = progressRatio,
                    onValueChange = { ratio ->
                        onSeek((ratio * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = HorizonSunset,
                        activeTrackColor = HorizonSunset,
                        inactiveTrackColor = ObsidianGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(position),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = formatDuration(duration),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controles Principais de Áudio (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Modo Aleatório",
                        tint = if (isShuffle) HorizonSunset else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Prev
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Faixa Anterior",
                        tint = TextWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Play / Pause (Big Circular Glow Button)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HorizonSunset, TwilightGlow)
                            )
                        )
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Tocar/Pausar",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Next
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Próxima Faixa",
                        tint = TextWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat Modes
                IconButton(onClick = onRepeatToggle) {
                    val icon = when (repeatMode) {
                        RepeatMode.OFF -> Icons.Rounded.Repeat
                        RepeatMode.ALL -> Icons.Rounded.Repeat
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    }
                    val tint = when (repeatMode) {
                        RepeatMode.OFF -> TextMuted
                        else -> HorizonSunset
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repetir",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ---------------------- DIÁLOGOS (POPUPS DE PLAYLIST) ----------------------

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nova Playlist",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nome da playlist", color = TextMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ObsidianBlack,
                        unfocusedContainerColor = ObsidianBlack,
                        focusedIndicatorColor = HorizonSunset,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(name)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CRIAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateSubdialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adicionar à Playlist",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { showCreateSubdialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Criar nova playlist",
                            tint = HorizonSunset
                        )
                    }
                }
                
                Text(
                    text = track.title,
                    color = HorizonSunset,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhuma playlist encontrada", color = TextMuted, fontSize = 12.sp)
                            TextButton(onClick = { showCreateSubdialog = true }) {
                                Text("Criar sua primeira playlist", color = HorizonSunset, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBlack)
                                    .clickable { onPlaylistSelected(playlist) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = playlist.name,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("FECHAR", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }

    if (showCreateSubdialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateSubdialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateSubdialog = false
            }
        )
    }
}

// ---------------------- COMPONENTES DA APARÊNCIA E AJUSTES DE PERMANÊNCIA ----------------------

@Composable
fun TrackCoverArt(
    track: Track,
    showCovers: Boolean,
    isPlaying: Boolean = false,
    extremePerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (showCovers) {
        val hash = remember(track.id) { track.title.hashCode() }
        val rotationAngle = if (isPlaying && !extremePerformanceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "cover_rotation")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "angle"
            )
            angle
        } else {
            0f
        }

        val gradientColors = remember(track.id) {
            val color1 = Color((hash and 0xFFFFFF) or 0xFF000000.toInt())
            val color2 = Color(((hash shr 8) and 0xFFFFFF) or 0xFF000000.toInt())
            listOf(color1.copy(alpha = 0.85f), color2.copy(alpha = 0.85f))
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .graphicsLayer {
                    if (isPlaying) {
                        rotationZ = rotationAngle
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            var hasLoadedImage by remember(track.id) { mutableStateOf(false) }

            if (track.albumArt != null) {
                AsyncImage(
                    model = track.albumArt,
                    contentDescription = "Capa da música",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onSuccess = { hasLoadedImage = true },
                    onError = { hasLoadedImage = false }
                )
            }

            if (!hasLoadedImage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.4f)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.4f)
                                .background(Color.Black, CircleShape)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.2f)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.4f)
                            .background(Color.Black, CircleShape)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSlate)
                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (track.isLocal) Icons.Rounded.Storage else Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = if (track.isLocal) TwilightGlow.copy(alpha = 0.8f) else HorizonSunset.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FullSettingsScreen(
    showCovers: Boolean,
    customFolder: String,
    extremePerformanceMode: Boolean,
    audioQuality: String,
    eqPreset: String,
    sleepTimerMinutes: Int,
    onShowCoversChanged: (Boolean) -> Unit,
    onCustomFolderChanged: (String) -> Unit,
    onExtremePerformanceModeChanged: (Boolean) -> Unit,
    onAudioQualityChanged: (String) -> Unit,
    onEqPresetChanged: (String) -> Unit,
    onSleepTimerChanged: (Int) -> Unit,
    onClearCoversCache: () -> Unit,
    onScanAudio: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var folderInput by remember(customFolder) { mutableStateOf(customFolder) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persiste as permissões de leitura da URI de pasta
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("HorizonMusic", "Erro persistência permissão URI", e)
            }
            
            // Tenta obter o caminho legível
            val readablePath = if ("com.android.externalstorage.documents" == it.authority) {
                val docId = it.path?.split(":")?.getOrNull(1) ?: ""
                if (docId.isNotEmpty()) "/storage/emulated/0/$docId" else null
            } else {
                null
            }

            if (readablePath != null) {
                onCustomFolderChanged(readablePath)
                Toast.makeText(context, "Pasta selecionada: $readablePath", Toast.LENGTH_SHORT).show()
            } else {
                // Se falhar na conversão, salva a URI string em si como fallback
                onCustomFolderChanged(it.toString())
                Toast.makeText(context, "Pasta URI configurada!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ajustes do Horizon",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextWhite
                )
            }
        },
        containerColor = ObsidianBlack,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            // Seção de Desempenho e Fluidez
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DESEMPENHO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TwilightGlow,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Desempenho Extremo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Desativa as animações rotativas das capas, os visualizadores dinâmicos por ondas e efeitos de partículas para máxima velocidade e menor consumo de bateria.",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = extremePerformanceMode,
                                onCheckedChange = onExtremePerformanceModeChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = HorizonSunset,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = ObsidianGray
                                )
                            )
                        }

                        if (!extremePerformanceMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mostrar Capas das Músicas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Carrega capas coloridas dinâmicas para as faixas. Desative para economizar consumo de dados e acelerar a exibição da lista.",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                                Switch(
                                    checked = showCovers,
                                    onCheckedChange = onShowCoversChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = HorizonSunset,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = ObsidianGray
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Seção de Áudio e Personalização
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PREFERÊNCIAS DE ÁUDIO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TwilightGlow,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Qualidade da Transmissão (Buffer)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Normal", "Alta", "Hi-Fi").forEach { quality ->
                                val selected = audioQuality == quality
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) HorizonSunset else ObsidianBlack)
                                        .border(
                                            1.dp,
                                            if (selected) Color.Transparent else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onAudioQualityChanged(quality) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = quality,
                                        color = if (selected) Color.Black else TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Preset do Equalizador",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Flat", "Bass Boost", "Vocal", "Eletrônica").forEach { preset ->
                                val selected = eqPreset == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) TwilightGlow else ObsidianBlack)
                                        .border(
                                            1.dp,
                                            if (selected) Color.Transparent else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onEqPresetChanged(preset) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (selected) Color.Black else TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Seção Timer para Dormir (Sleep Timer)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TIMER PARA DORMIR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TwilightGlow,
                                letterSpacing = 1.sp
                            )
                            if (sleepTimerMinutes > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(HorizonSunset.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(HorizonSunset)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$sleepTimerMinutes min restantes",
                                            color = HorizonSunset,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Parar a reprodução de música após:",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 15, 30, 45, 60).forEach { mins ->
                                val isActualMatch = (mins == 0 && sleepTimerMinutes == 0) || 
                                                    (mins > 0 && sleepTimerMinutes > 0 && mins == ((sleepTimerMinutes + 14) / 15 * 15))
                                val text = if (mins == 0) "Desligar" else "${mins} min"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActualMatch) HorizonSunset else ObsidianBlack)
                                        .border(
                                            1.dp,
                                            if (isActualMatch) Color.Transparent else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onSleepTimerChanged(mins)
                                            if (mins > 0) {
                                                Toast.makeText(context, "Timer configurado para $mins minutos", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Timer desativado", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = text,
                                        color = if (isActualMatch) Color.Black else TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Seção de Origem e Gerenciamento de Arquivos
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ORIGEM DE MÚSICA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TwilightGlow,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Pasta de Escaneamento do Horizon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure uma pasta exclusiva para leitura das faixas locais no Horizon. Caso prefira listar todas, basta limpar o filtro.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = folderInput,
                            onValueChange = { folderInput = it },
                            placeholder = { Text("Ex: /storage/emulated/0/Music", color = TextMuted, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = ObsidianBlack,
                                unfocusedContainerColor = ObsidianBlack,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedIndicatorColor = HorizonSunset,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianGray),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = HorizonSunset,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Selecionar", color = TextWhite, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    onCustomFolderChanged(folderInput)
                                    Toast.makeText(context, "Caminho salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aplicar", color = Color.Black, fontSize = 12.sp)
                            }
                        }

                        if (customFolder.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filtro ativo: $customFolder",
                                    color = TwilightGlow,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Text(
                                    text = "Limpar Filtro",
                                    color = HorizonSunset,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            onCustomFolderChanged("")
                                            folderInput = ""
                                            Toast.makeText(context, "Filtro redefinido!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cache de Capas de Música",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Exclua as imagens em cache local para liberar espaço de armazenamento interno e limpar memória RAM.",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            Button(
                                onClick = {
                                    onClearCoversCache()
                                    Toast.makeText(context, "Cache de capas limpo com sucesso!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianGray),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Limpar", color = TextWhite, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Informação da Versão
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Horizon Music Player v1.5 Pro",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Otimizado para Desempenho Extremo",
                            color = HorizonSunset.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------------------- UTILS ----------------------

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun OnlineSearchSection(
    viewModel: MusicViewModel,
    context: Context
) {
    val query by viewModel.onlineSearchQuery.collectAsState()
    val results by viewModel.onlineSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingOnline.collectAsState()
    val searchError by viewModel.searchOnlineError.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadProgresses by viewModel.downloadProgresses.collectAsState()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsState()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsState()

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Campo de Entrada para Pesquisa On-line com botão de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.updateOnlineSearchQuery(it) },
                placeholder = { Text("Música, artista ou gênero...", color = TextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Busca Global",
                        tint = HorizonSunset
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { 
                            viewModel.updateOnlineSearchQuery("") 
                            viewModel.searchOnline("")
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpar",
                                tint = TextMuted
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ObsidianSlate,
                    unfocusedContainerColor = ObsidianSlate,
                    disabledContainerColor = ObsidianSlate,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("online_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { 
                    viewModel.searchOnline(query) 
                    keyboardController?.hide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HorizonSunset),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("online_search_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontally Scrollable Genre Chips (Quick Search)
        val genres = listOf(
            "Sertanejo" to "🤠",
            "Pop Nacional" to "🇧🇷",
            "Lofi Beats" to "☕",
            "Rock Hits" to "🎸",
            "Eletrônica" to "⚡",
            "Hip Hop" to "🎤",
            "Funk Remix" to "🔥",
            "Anos 80" to "📼"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { (genre, emoji) ->
                val isSelected = query.lowercase() == genre.lowercase()
                val bgValue by animateColorAsState(
                    targetValue = if (isSelected) HorizonSunset else ObsidianSlate,
                    label = "chipBg"
                )
                val textValue by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else TextWhite,
                    label = "chipText"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgValue)
                        .clickable {
                            viewModel.updateOnlineSearchQuery(genre)
                            viewModel.searchOnline(genre)
                            keyboardController?.hide()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = genre,
                        color = textValue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Exibição dos resultados ou estados
        if (isSearching) {
            // Skeleton Loader Animado - Super premium e fluido!
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Buscando rotas seguras...",
                    color = HorizonGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                repeat(6) {
                    OnlineTrackSkeletonItem()
                }
            }
        } else if (searchError != null && results.isEmpty()) {
            // Estado de Erro
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Erro de conexão com o servidor de música.",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Iniciamos automaticamente a estação de streaming offline.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.searchOnline(query.ifEmpty { "Pop" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSlate),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tentar Reconectar", color = TextWhite)
                }
            }
        } else {
            val isInitialState = query.isEmpty() && results.isEmpty()
            val listToDisplay = if (isInitialState) {
                com.example.ui.TRENDING_TRACKS
            } else {
                results
            }

            if (listToDisplay.isEmpty()) {
                // Estado: Lista Vazia
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhum resultado encontrado.",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isInitialState) {
                        // CAROUSEL DE DESTAQUES (YOUTUBE STYLE)
                        item {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Músicas em Destaque 🔥",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HorizonSunset,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Text(
                                        text = "Toque para ouvir online",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    listToDisplay.forEach { track ->
                                        val state = downloadStates[track.id] ?: DownloadState.Idle
                                        val progress = downloadProgresses[track.id] ?: 0f

                                        Card(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .height(210.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    viewModel.playOnlineTrack(track, context)
                                                },
                                            colors = CardDefaults.cardColors(containerColor = ObsidianSlate)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                Column {
                                                    // Thumbnail
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(115.dp)
                                                            .background(ObsidianGray)
                                                    ) {
                                                        AsyncImage(
                                                            model = track.thumbnail,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        // Circular Play Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .align(Alignment.Center)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.6f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isBuffering && currentTrack?.id == "online_" + track.id) {
                                                                CircularProgressIndicator(
                                                                    color = HorizonSunset,
                                                                    strokeWidth = 2.dp,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.PlayArrow,
                                                                    contentDescription = "Ouvir",
                                                                    tint = HorizonSunset,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Text Details
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text(
                                                            text = track.title,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextWhite,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = track.artist,
                                                            fontSize = 10.sp,
                                                            color = TextMuted,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                // Floating Download Button on Bottom Right
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(ObsidianGray.copy(alpha = 0.9f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    when (state) {
                                                        is DownloadState.Idle -> {
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.downloadOnlineTrack(track, context)
                                                                    Toast.makeText(context, "Iniciando download...", Toast.LENGTH_SHORT).show()
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Download,
                                                                    contentDescription = "Baixar",
                                                                    tint = HorizonSunset,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        is DownloadState.FetchingStream -> {
                                                            CircularProgressIndicator(
                                                                color = HorizonGold,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                        is DownloadState.Downloading -> {
                                                            CircularProgressIndicator(
                                                                progress = { progress },
                                                                color = HorizonSunset,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        is DownloadState.Success -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.CheckCircle,
                                                                contentDescription = "Baixado",
                                                                tint = Color.Green,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        is DownloadState.Error -> {
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.downloadOnlineTrack(track, context)
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Error,
                                                                    contentDescription = "Erro",
                                                                    tint = Color.Red,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Estação de Streaming & Busca",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = HorizonSunset,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(listToDisplay) { track ->
                        val state = downloadStates[track.id] ?: DownloadState.Idle
                        val progress = downloadProgresses[track.id] ?: 0f

                        // Row item stylizado
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianSlate)
                                .clickable {
                                    viewModel.playOnlineTrack(track, context)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Miniatura do Vídeo/Música (Cover art)
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianGray),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = track.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Pulsing hover play icon overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isBuffering && currentTrack?.id == "online_" + track.id) {
                                        CircularProgressIndicator(
                                            color = HorizonSunset,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Ouvir",
                                            tint = HorizonSunset,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Título e Autor
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Custom online streamer tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(HorizonSunset.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ONLINE",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HorizonSunset
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = track.artist,
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Botão / Estado de Download Lateral
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (state) {
                                    is DownloadState.Idle -> {
                                        IconButton(
                                            onClick = {
                                                viewModel.downloadOnlineTrack(track, context)
                                                Toast.makeText(context, "Iniciando download...", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = "Baixar Música",
                                                tint = HorizonSunset,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    is DownloadState.FetchingStream -> {
                                        CircularProgressIndicator(
                                            color = HorizonGold,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    is DownloadState.Downloading -> {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { progress },
                                                color = HorizonSunset,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = "${(progress * 100).toInt()}%",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = HorizonSunset
                                            )
                                        }
                                    }
                                    is DownloadState.Success -> {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Concluído",
                                            tint = Color.Green,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    is DownloadState.Error -> {
                                        IconButton(
                                            onClick = {
                                                viewModel.downloadOnlineTrack(track, context)
                                                Toast.makeText(context, "Reiniciando download...", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Error,
                                                contentDescription = "Erro. Toque para tentar novamente",
                                                tint = Color.Red,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineTrackSkeletonItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSlate.copy(alpha = alpha))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianGray)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianGray)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianGray)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ObsidianGray)
        )
    }
}
