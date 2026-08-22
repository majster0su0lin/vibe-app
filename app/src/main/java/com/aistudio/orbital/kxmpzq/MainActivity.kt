package com.aistudio.orbital.kxmpzq

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
 
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0D17)
                ) {
                    OrbitalApp()
                }
            }
        }
    }
}

object RenderBuffer {
    val posPts = FloatArray(1_000_000)
    val negPts = FloatArray(1_000_000)
}

val orbitalColors = listOf(
    Color(0xFFFF3366) to Color(0xFF33CCFF),
    Color(0xFF33FF66) to Color(0xFFFF9933),
    Color(0xFFFFFF33) to Color(0xFFCC33FF),
    Color(0xFFFFFFFF) to Color(0xFF888888),
    Color(0xFFFFAA00) to Color(0xFF00AAFF)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitalApp(viewModel: OrbitalViewModel = viewModel()) {
    var showElementPicker by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetContainerColor = Color(0xFF1E1E2C),
        sheetContentColor = Color.White,
        containerColor = Color(0xFF0B0D17),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = getSpeciesName(viewModel.z),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${viewModel.n}${getLString(viewModel.l)} orbital",
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.regenerate() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        sheetContent = {
            ControlsSheet(
                viewModel = viewModel,
                onOpenElementPicker = { showElementPicker = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OrbitalRenderer(
                clouds = viewModel.clouds,
                n = viewModel.n,
                z = viewModel.z,
                threshold = viewModel.threshold
            )
            
            if (viewModel.isGenerating) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { viewModel.generationProgress },
                        color = Color(0xFF33CCFF)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Sampling...", color = Color.White)
                }
            }
        }
    }
    
    if (showElementPicker) {
        ModalBottomSheet(
            onDismissRequest = { showElementPicker = false },
            containerColor = Color(0xFF1E1E2C)
        ) {
            ElementPicker(
                currentZ = viewModel.z,
                onSelect = { 
                    viewModel.setElement(it)
                    showElementPicker = false
                }
            )
        }
    }
    
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = Color(0xFF1E1E2C),
            title = { Text("Orbital Information", color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Species: ${getSpeciesName(viewModel.z)}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${elements[viewModel.z-1].name} — ${viewModel.z} protons, ${elements[viewModel.z-1].neutrons} neutrons", color = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    Text("Ionization note: Only one electron remains after ionization, so the exact hydrogen-like solution applies.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Quantum Numbers:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Principal (n): ${viewModel.n}", color = Color.LightGray)
                    Text("Azimuthal (l): ${viewModel.l}", color = Color.LightGray)
                    Text("Magnetic (m): ${viewModel.selectedM.joinToString(", ")}", color = Color.LightGray)
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Energy Level:", color = Color.White, fontWeight = FontWeight.Bold)
                    val energy = -13.6 * (viewModel.z * viewModel.z).toDouble() / (viewModel.n * viewModel.n).toDouble()
                    Text(String.format(java.util.Locale.US, "%.2f eV", energy), color = Color.LightGray)
                    
                    val a = viewModel.z + elements[viewModel.z-1].neutrons
                    val nuclearRadiusFm = 1.2 * Math.pow(a.toDouble(), 1.0/3.0)
                    val rExpFm = (52900.0 / viewModel.z) * (3 * viewModel.n * viewModel.n - viewModel.l * (viewModel.l + 1)) / 2.0
                    val ratio = rExpFm / nuclearRadiusFm
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Physical Nucleus:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(String.format(java.util.Locale.US, "Nuclear radius: ~%.2f fm", nuclearRadiusFm), color = Color.LightGray)
                    Text("The nucleus is about ${String.format(java.util.Locale.US, "%,d", ratio.toInt())}× smaller than the electron cloud shown here — it is rendered enlarged so it's visible. Ionization changes the electron(s), not the nucleus — the core shown here has ${viewModel.z} protons and ${elements[viewModel.z-1].neutrons} neutrons regardless of charge.", color = Color.LightGray, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close", color = Color(0xFF33CCFF)) }
            }
        )
    }
}

data class Nucleon(val x: Float, val y: Float, val z: Float, val isProton: Boolean)

@Composable
fun rememberNucleus(z: Int, neutrons: Int): List<Nucleon> {
    return remember(z, neutrons) {
        val total = z + neutrons
        val list = mutableListOf<Nucleon>()
        val phi = Math.PI * (3.0 - Math.sqrt(5.0))
        
        var pLeft = z
        var nLeft = neutrons
        
        for (i in 0 until total) {
            val y = if (total > 1) 1f - (i / (total - 1f)) * 2f else 0f
            val radius = Math.sqrt(1.0 - y * y).toFloat()
            val theta = phi * i
            
            val x = (Math.cos(theta) * radius).toFloat()
            val zCoord = (Math.sin(theta) * radius).toFloat()
            
            val isProton = if (pLeft > 0 && nLeft > 0) {
                if (Math.random() < pLeft.toDouble() / (pLeft + nLeft)) {
                    pLeft--; true
                } else {
                    nLeft--; false
                }
            } else if (pLeft > 0) {
                pLeft--; true
            } else {
                nLeft--; false
            }
            
            list.add(Nucleon(x, y, zCoord, isProton))
        }
        list
    }
}

@Composable
fun OrbitalRenderer(
    clouds: List<PointCloud>,
    n: Int,
    z: Int,
    threshold: Float
) {
    var targetRotX by remember { mutableFloatStateOf(0f) }
    var targetRotY by remember { mutableFloatStateOf(0f) }
    var targetScale by remember { mutableFloatStateOf(1f) }
    
    val rotX by animateFloatAsState(targetRotX, spring(stiffness = Spring.StiffnessLow), label = "rotX")
    val rotY by animateFloatAsState(targetRotY, spring(stiffness = Spring.StiffnessLow), label = "rotY")
    val scale by animateFloatAsState(targetScale, spring(stiffness = Spring.StiffnessLow), label = "scale")
    
    val maxRho = (2.5f * n * n + 5f) / z
    
    val paintPos = remember {
        android.graphics.Paint().apply {
            strokeWidth = 4f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
    }
    val paintNeg = remember {
        android.graphics.Paint().apply {
            strokeWidth = 4f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
    }
    
    val nucleus = rememberNucleus(z, elements[z-1].neutrons)
    val nucleusPaintProton = remember { android.graphics.Paint().apply { color = android.graphics.Color.RED; isAntiAlias = true } }
    val nucleusPaintNeutron = remember { android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; isAntiAlias = true } }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    targetRotX += pan.y * 0.005f
                    targetRotY -= pan.x * 0.005f
                    targetScale = (targetScale * zoom).coerceIn(0.2f, 5f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        targetRotX = 0f
                        targetRotY = 0f
                        targetScale = 1f
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val baseScale = (min(width, height) * 0.4f) / maxRho
        val finalScale = baseScale * scale
        
        val cx = cos(rotX)
        val sx = sin(rotX)
        val cy = cos(rotY)
        val sy = sin(rotY)
        
        val centerX = width / 2
        val centerY = height / 2
        
        drawContext.canvas.nativeCanvas.apply {
            clouds.forEachIndexed { index, cloud ->
                val (colorPos, colorNeg) = orbitalColors[index % orbitalColors.size]
                paintPos.color = colorPos.toArgb()
                paintPos.alpha = 150
                paintNeg.color = colorNeg.toArgb()
                paintNeg.alpha = 150
                
                var posCount = 0
                var negCount = 0
                
                for (i in 0 until cloud.size) {
                    if (cloud.prob[i] < threshold) continue
                    
                    val px = cloud.x[i]
                    val py = cloud.y[i]
                    val pz = cloud.z[i]
                    
                    // Y rotation
                    val x1 = px * cy + pz * sy
                    val y1 = py
                    val z1 = -px * sy + pz * cy
                    
                    // X rotation
                    val x2 = x1
                    val y2 = y1 * cx - z1 * sx
                    val z2 = y1 * sx + z1 * cx
                    
                    val finalX = x2 * finalScale + centerX
                    val finalY = y2 * finalScale + centerY
                    
                    if (cloud.phase[i] > 0) {
                        if (posCount * 2 + 1 < RenderBuffer.posPts.size) {
                            RenderBuffer.posPts[posCount * 2] = finalX
                            RenderBuffer.posPts[posCount * 2 + 1] = finalY
                            posCount++
                        }
                    } else {
                        if (negCount * 2 + 1 < RenderBuffer.negPts.size) {
                            RenderBuffer.negPts[negCount * 2] = finalX
                            RenderBuffer.negPts[negCount * 2 + 1] = finalY
                            negCount++
                        }
                    }
                }
                
                drawPoints(RenderBuffer.posPts, 0, posCount * 2, paintPos)
                drawPoints(RenderBuffer.negPts, 0, negCount * 2, paintNeg)
            }
            
            // Draw Nucleus
            val nucRadius = maxRho * 0.04f * finalScale // scale nucleus size
            val nucleonRadius = nucRadius / Math.pow(nucleus.size.toDouble(), 1.0/3.0).toFloat()
            
            val projectedNucleons = nucleus.map { nuc ->
                val x1 = nuc.x * cy + nuc.z * sy
                val y1 = nuc.y
                val z1 = -nuc.x * sy + nuc.z * cy
                
                val x2 = x1
                val y2 = y1 * cx - z1 * sx
                val z2 = y1 * sx + z1 * cx
                
                Triple(x2 * nucRadius + centerX, y2 * nucRadius + centerY, z2)
            }.zip(nucleus).sortedBy { it.first.third }
            
            projectedNucleons.forEach { (proj, nucleon) ->
                drawCircle(
                    proj.first, 
                    proj.second, 
                    nucleonRadius, 
                    if (nucleon.isProton) nucleusPaintProton else nucleusPaintNeutron
                )
            }
        }
    }
}

@Composable
fun ControlsSheet(
    viewModel: OrbitalViewModel,
    onOpenElementPicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Row: Element Picker Button
        Button(
            onClick = onOpenElementPicker,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C3E))
        ) {
            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Element: ${getSpeciesName(viewModel.z)} (Z=${viewModel.z})")
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Shortcuts
        Text("Shortcuts", color = Color.LightGray, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShortcutChip("1s", viewModel.n == 1 && viewModel.l == 0) { viewModel.selectShortcut(1, 0) }
            ShortcutChip("2s", viewModel.n == 2 && viewModel.l == 0) { viewModel.selectShortcut(2, 0) }
            ShortcutChip("2p", viewModel.n == 2 && viewModel.l == 1) { viewModel.selectShortcut(2, 1) }
            ShortcutChip("3s", viewModel.n == 3 && viewModel.l == 0) { viewModel.selectShortcut(3, 0) }
            ShortcutChip("3p", viewModel.n == 3 && viewModel.l == 1) { viewModel.selectShortcut(3, 1) }
            ShortcutChip("3d", viewModel.n == 3 && viewModel.l == 2) { viewModel.selectShortcut(3, 2) }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Quantum N
        Text("Principal Number (n)", color = Color.LightGray, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..4) {
                SelectableChip(
                    text = i.toString(),
                    selected = viewModel.n == i,
                    onClick = { viewModel.selectN(i) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Quantum L
        Text("Azimuthal Number (l)", color = Color.LightGray, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until viewModel.n) {
                SelectableChip(
                    text = "$i (${getLString(i)})",
                    selected = viewModel.l == i,
                    onClick = { viewModel.selectL(i) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Quantum M
        Text("Magnetic Number (m) - Tap multiple for overlay", color = Color.LightGray, fontSize = 14.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in -viewModel.l..viewModel.l) {
                SelectableChip(
                    text = i.toString(),
                    selected = viewModel.selectedM.contains(i),
                    onClick = { viewModel.toggleM(i) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Density Slider
        Text("Density Threshold", color = Color.LightGray, fontSize = 14.sp)
        Slider(
            value = viewModel.threshold,
            onValueChange = { viewModel.threshold = it },
            valueRange = 0f..0.2f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF33CCFF),
                activeTrackColor = Color(0xFF33CCFF)
            )
        )
        
        // Sample Mode
        Text("Sample Count", color = Color.LightGray, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectableChip("Low (5k)", viewModel.sampleMode == 0, { viewModel.updateSampleMode(0) }, Modifier.weight(1f))
            SelectableChip("Med (20k)", viewModel.sampleMode == 1, { viewModel.updateSampleMode(1) }, Modifier.weight(1f))
            SelectableChip("High (50k)", viewModel.sampleMode == 2, { viewModel.updateSampleMode(2) }, Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

fun getLString(l: Int) = when(l) {
    0 -> "s"; 1 -> "p"; 2 -> "d"; 3 -> "f"; else -> "?"
}

@Composable
fun ShortcutChip(text: String, selected: Boolean, onClick: () -> Unit) {
    SelectableChip(text, selected, onClick)
}

@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (selected) Color(0xFF33CCFF) else Color(0xFF2C2C3E),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ElementPicker(
    currentZ: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Select Element (Z = 1 to 20)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(elements) { index, item ->
                val z = index + 1
                val isSelected = currentZ == z
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(
                            color = if (isSelected) Color(0xFF33CCFF) else Color(0xFF2C2C3E),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(z) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.symbol,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = z.toString(),
                            color = if (isSelected) Color.DarkGray else Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

