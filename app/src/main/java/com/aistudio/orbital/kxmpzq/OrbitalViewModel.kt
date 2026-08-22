package com.aistudio.orbital.kxmpzq

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

data class ElementData(val symbol: String, val name: String, val neutrons: Int)

val elements = listOf(
    ElementData("H", "Hydrogen", 0), ElementData("He", "Helium", 2), ElementData("Li", "Lithium", 4), ElementData("Be", "Beryllium", 5),
    ElementData("B", "Boron", 6), ElementData("C", "Carbon", 6), ElementData("N", "Nitrogen", 7), ElementData("O", "Oxygen", 8),
    ElementData("F", "Fluorine", 10), ElementData("Ne", "Neon", 10), ElementData("Na", "Sodium", 12), ElementData("Mg", "Magnesium", 12),
    ElementData("Al", "Aluminum", 14), ElementData("Si", "Silicon", 14), ElementData("P", "Phosphorus", 16), ElementData("S", "Sulfur", 16),
    ElementData("Cl", "Chlorine", 18), ElementData("Ar", "Argon", 22), ElementData("K", "Potassium", 20), ElementData("Ca", "Calcium", 20)
)

fun getSpeciesName(z: Int): String {
    val symbol = elements[z-1].symbol
    if (z == 1) return symbol
    val charge = z - 1
    val superscript = charge.toString().map { 
        when(it) {
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            '0' -> '⁰'
            else -> it
        }
    }.joinToString("") + "⁺"
    return "$symbol$superscript"
}

class OrbitalViewModel : ViewModel() {
    var z by mutableIntStateOf(1)
        private set
    var n by mutableIntStateOf(1)
        private set
    var l by mutableIntStateOf(0)
        private set
    var selectedM by mutableStateOf(setOf(0))
        private set
    var threshold by mutableFloatStateOf(0.01f)
    var sampleMode by mutableIntStateOf(1)
    
    var clouds by mutableStateOf<List<PointCloud>>(emptyList())
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var generationProgress by mutableFloatStateOf(0f)
        private set
        
    private var generationJob: Job? = null
    
    init {
        regenerate()
    }
    
    fun setElement(newZ: Int) {
        if (z == newZ) return
        z = newZ
        regenerate()
    }
    
    fun updateSampleMode(mode: Int) {
        if (sampleMode == mode) return
        sampleMode = mode
        regenerate()
    }
    
    fun selectN(newN: Int) {
        if (n == newN) return
        var newL = l
        if (newL >= newN) newL = newN - 1
        var newM = selectedM.filter { it in -newL..newL }.toSet()
        if (newM.isEmpty()) newM = setOf(0)
        n = newN
        l = newL
        selectedM = newM
        regenerate()
    }
    
    fun selectL(newL: Int) {
        if (l == newL) return
        var newM = selectedM.filter { it in -newL..newL }.toSet()
        if (newM.isEmpty()) newM = setOf(0)
        l = newL
        selectedM = newM
        regenerate()
    }
    
    fun toggleM(m: Int) {
        val newM = selectedM.toMutableSet()
        if (newM.contains(m)) {
            if (newM.size > 1) newM.remove(m)
        } else {
            newM.add(m)
        }
        selectedM = newM
        regenerate()
    }
    
    fun selectShortcut(newN: Int, newL: Int) {
        n = newN
        l = newL
        selectedM = setOf(0)
        regenerate()
    }
    
    fun regenerate() {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            isGenerating = true
            generationProgress = 0f
            
            val el = elements[z-1]
            val a = z + el.neutrons
            val nuclearRadiusFm = 1.2 * Math.pow(a.toDouble(), 1.0/3.0)
            val rExpFm = (52900.0 / z) * (3 * n * n - l * (l + 1)) / 2.0
            val ratio = rExpFm / nuclearRadiusFm
            
            android.util.Log.d("OrbitalPhysics", "=== Nucleus Info ===")
            android.util.Log.d("OrbitalPhysics", "Isotope: ${a}${el.symbol}, Z=$z, N=${el.neutrons}, A=$a")
            android.util.Log.d("OrbitalPhysics", "Computed nuclear radius: ${String.format(java.util.Locale.US, "%.2f fm", nuclearRadiusFm)}")
            android.util.Log.d("OrbitalPhysics", "Computed <r> for orbital: ${String.format(java.util.Locale.US, "%.0f fm", rExpFm)}")
            android.util.Log.d("OrbitalPhysics", "Ratio (Orbital / Nucleus): ${String.format(java.util.Locale.US, "%,d", ratio.toInt())}x")
            
            val targetSamples = when(sampleMode) {
                0 -> 5000
                1 -> 20000
                else -> 50000
            }
            
            val mList = selectedM.toList().sorted()
            val totalTarget = targetSamples * mList.size
            val acceptedCount = AtomicInteger(0)
            
            val deferredClouds = mList.map { m ->
                async(Dispatchers.Default) {
                    var lastReported = 0
                    generatePointCloud(n, l, m, z, targetSamples) { prog ->
                        val currentAccepted = (prog * targetSamples).toInt()
                        val diff = currentAccepted - lastReported
                        lastReported = currentAccepted
                        val currentTotal = acceptedCount.addAndGet(diff)
                        generationProgress = currentTotal.toFloat() / totalTarget
                    }
                }
            }
            
            clouds = deferredClouds.awaitAll()
            isGenerating = false
        }
    }
}
