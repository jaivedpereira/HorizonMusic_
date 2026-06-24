package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    // Configurações e preferências
    val showCovers by viewModel.showCovers.collectAsState()
    val customFolder by viewModel.customFolder.collectAsState()

    // UI States locais
    var currentTab by remember { mutableStateOf(0) } // 0: Músicas, 1: Playlists, 2: Favoritos
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var playlistDetailToShow by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    // Diálogos e Modais
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<Track?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        topBar = {
            if (!isPlayerExpanded) {
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
                                onClick = { showSettingsDialog = true }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("Músicas", "Playlists", "Favoritos").forEachIndexed { index, title ->
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
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgValue)
                                    .clickable {
                                        currentTab = index
                                        playlistDetailToShow = null // Reseta detalhes de playlist ao mudar de aba
                                    }
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
            if (!isPlayerExpanded && currentTrack != null) {
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
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = TextWhite,
                                modifier = Modifier.size(26.dp)
                            )
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
                    position = currentPosition,
                    duration = duration,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    favorites = favorites,
                    showCovers = showCovers,
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

        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(24.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header do Dialog
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ajustes do Horizon",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextWhite
                                )
                                IconButton(onClick = { showSettingsDialog = false }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Fechar",
                                        tint = TextMuted
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            
                            SettingsSection(
                                showCovers = showCovers,
                                customFolder = customFolder,
                                onShowCoversChanged = { viewModel.setShowCovers(it) },
                                onCustomFolderChanged = { viewModel.setCustomFolder(it) },
                                onScanAudio = { viewModel.scanLocalAudio() }
                            )
                        }
                    }
                }
            }
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
            Column {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = TextWhite
                )
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
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = HorizonSunset,
                        modifier = Modifier.size(18.dp)
                    )
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
    position: Long,
    duration: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    favorites: List<Track>,
    showCovers: Boolean,
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

            // Área de Capa / Visualizador de Acordo com Ajustes de Desempenho
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showCovers) {
                    // Grande CD/Vinil giratório de alta fidelidade
                    TrackCoverArt(
                        track = track,
                        showCovers = true,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(1f)
                    )
                } else {
                    // Procedural Canvas Horizon Sunset Visualizer (Máximo desempenho/fluidez)
                    HorizonCanvasVisualizer(
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                    )
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
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Tocar/Pausar",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
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
    modifier: Modifier = Modifier
) {
    if (showCovers) {
        val hash = remember(track.id) { track.title.hashCode() }
        val rotationAngle = if (isPlaying) {
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
fun SettingsSection(
    showCovers: Boolean,
    customFolder: String,
    onShowCoversChanged: (Boolean) -> Unit,
    onCustomFolderChanged: (String) -> Unit,
    onScanAudio: () -> Unit
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Text(
                text = "Configurações & Ajustes",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personalize sua experiência de áudio no Horizon",
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        // Card de Visualização / Fluidic Design
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APARÊNCIA E FLUIDEZ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TwilightGlow,
                        letterSpacing = 1.sp
                    )
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
                                text = "Carrega capas coloridas dinâmicas para as faixas. Desative para máxima fluidez nas animações.",
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

        // Card de Diretório de Varredura
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DIRETÓRIO E ORIGEM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TwilightGlow,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pasta de Escaneamento do Horizon",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Escolha um diretório específico para o Horizon listar suas músicas. Deixe em branco para buscar em todo o armazenamento.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // TextField de Entrada do Caminho da Pasta
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

                    // Ações de Caminho
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
                                text = "Ativo: $customFolder",
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
                                        Toast.makeText(context, "Horizon redefinido para todo o armazenamento!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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
                        text = "Horizon Music Player v1.2",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Developed with Elegant Dark Theme",
                        color = TextMuted.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
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
