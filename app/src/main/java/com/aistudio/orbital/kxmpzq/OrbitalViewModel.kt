package com.aistudio.orbital.kxmpzq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrbitalViewModel : ViewModel() {
    var n by mutableIntStateOf(2)
        private set
    var l by mutableIntStateOf(1)
        private set
    var m by mutableIntStateOf(0)
        private set
    var Z by mutableIntStateOf(1)
        private set

    var pointCloud by mutableStateOf<PointCloud?>(null)
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var generationJob: Job? = null

    init {
        generate()
    }

    fun setQuantumNumbers(newN: Int, newL: Int, newM: Int) {
        n = newN
        l = newL.coerceIn(0, n - 1)
        m = newM.coerceIn(-l, l)
        generate()
    }

    fun generate() {
        generationJob?.cancel()
        isGenerating = true
        progress = 0f

        generationJob = viewModelScope.launch {
            val result = generatePointCloud(
                n = n,
                l = l,
                m = m,
                Z = Z,
                targetSamples = 6000
            ) { currentProgress ->
                progress = currentProgress
            }
            pointCloud = result
            isGenerating = false
        }
    }
}
