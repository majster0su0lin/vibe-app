package com.aistudio.orbital.kxmpzq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: OrbitalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF101216)
                ) {
                    OrbitalViewerScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun OrbitalViewerScreen(viewModel: OrbitalViewModel) {
    var rotX by remember { mutableFloatStateOf(0.3f) }
    var rotY by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }

    val orbitalNames = arrayOf("s", "p", "d", "f")

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 3D Canvas Visualizer ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        rotY += pan.x * 0.008f
                        rotX += pan.y * 0.008f
                        zoom = (zoom * zoomFactor).coerceIn(0.4f, 4.0f)
                    }
                }
        ) {
            val cloud = viewModel.pointCloud
            if (cloud != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val baseScale = (size.minDimension / 35f) * zoom

                    val cosX = cos(rotX)
                    val sinX = sin(rotX)
                    val cosY = cos(rotY)
                    val sinY = sin(rotY)

                    for (i in 0 until cloud.size) {
                        val x0 = cloud.x[i]
                        val y0 = cloud.y[i]
                        val z0 = cloud.z[i]

                        // Y-axis rotation
                        val x1 = x0 * cosY + z0 * sinY
                        val z1 = -x0 * sinY + z0 * cosY

                        // X-axis rotation
                        val y2 = y0 * cosX - z1 * sinX
                        val z2 = y0 * sinX + z1 * cosX

                        val screenX = centerX + x1 * baseScale
                        val screenY = centerY - y2 * baseScale

                        val phaseColor = if (cloud.phase[i] > 0) Color(0xFF00E5FF) else Color(0xFFFF4081)
                        val depthAlpha = ((z2 + 20f) / 40f).coerceIn(0.15f, 0.9f)

                        drawCircle(
                            color = phaseColor.copy(alpha = depthAlpha),
                            radius = 2.5f,
                            center = Offset(screenX, screenY)
                        )
                    }
                }
            }

            if (viewModel.isGenerating) {
                CircularProgressIndicator(
                    progress = { viewModel.progress },
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00E5FF)
                )
            }
        }

        // --- Quantum Number Controls ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${viewModel.n}${orbitalNames.getOrElse(viewModel.l) { "l" }} (m = ${viewModel.m}) Orbital",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Principal Quantum Number (n)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("n = ${viewModel.n}", color = Color.Gray)
                        Row {
                            Button(
                                onClick = { if (viewModel.n > 1) viewModel.setQuantumNumbers(viewModel.n - 1, viewModel.l, viewModel.m) },
                                enabled = viewModel.n > 1
                            ) { Text("-") }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { if (viewModel.n < 4) viewModel.setQuantumNumbers(viewModel.n + 1, viewModel.l, viewModel.m) },
                                enabled = viewModel.n < 4
                            ) { Text("+") }
                        }
                    }

                    // Azimuthal Quantum Number (l)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("l = ${viewModel.l} (${orbitalNames.getOrElse(viewModel.l) { "" }})", color = Color.Gray)
                        Row {
                            Button(
                                onClick = { if (viewModel.l > 0) viewModel.setQuantumNumbers(viewModel.n, viewModel.l - 1, viewModel.m) },
                                enabled = viewModel.l > 0
                            ) { Text("-") }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { if (viewModel.l < viewModel.n - 1) viewModel.setQuantumNumbers(viewModel.n, viewModel.l + 1, viewModel.m) },
                                enabled = viewModel.l < viewModel.n - 1
                            ) { Text("+") }
                        }
                    }

                    // Magnetic Quantum Number (m)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("m = ${viewModel.m}", color = Color.Gray)
                        Row {
                            Button(
                                onClick = { if (viewModel.m > -viewModel.l) viewModel.setQuantumNumbers(viewModel.n, viewModel.l, viewModel.m - 1) },
                                enabled = viewModel.m > -viewModel.l
                            ) { Text("-") }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { if (viewModel.m < viewModel.l) viewModel.setQuantumNumbers(viewModel.n, viewModel.l, viewModel.m + 1) },
                                enabled = viewModel.m < viewModel.l
                            ) { Text("+") }
                        }
                    }
                }
            }
        }
    }
}
