package com.aistudio.orbital.kxmpzq

import android.util.Log
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.*

class PointCloud(val size: Int) {
    val x = FloatArray(size)
    val y = FloatArray(size)
    val z = FloatArray(size)
    val phase = ByteArray(size)
    val prob = FloatArray(size)
}

fun factorial(n: Int): Float {
    var f = 1f
    for (i in 2..n) f *= i
    return f
}

fun choose(n: Int, k: Int): Float {
    if (k < 0 || k > n) return 0f
    return factorial(n) / (factorial(k) * factorial(n - k))
}

// Generalized (associated) Laguerre polynomial L_n^alpha(x)
fun laguerre(n: Int, alpha: Int, x: Float): Float {
    var sum = 0f
    for (k in 0..n) {
        val sign = if (k % 2 == 0) 1f else -1f
        val term = sign * choose(n + alpha, n - k) * x.pow(k) / factorial(k)
        sum += term
    }
    return sum
}

fun radial(n: Int, l: Int, r: Float, Z: Int): Float {
    val rho = (2f * Z * r) / n
    val prefactor = sqrt(
        (2f * Z / n).pow(3) * factorial(n - l - 1) / (2f * n * factorial(n + l).pow(3))
    )
    return prefactor * exp(-Z * r / n) * rho.pow(l) * laguerre(n - l - 1, 2 * l + 1, rho)
}

fun angular(l: Int, m: Int, x: Float, y: Float, z: Float, r: Float): Float {
    if (r == 0f) return if (l == 0) sqrt(1f / (4f * PI.toFloat())) else 0f
    val pi = PI.toFloat()
    
    return when (l) {
        0 -> sqrt(1f / (4f * pi))
        1 -> {
            val factor = sqrt(3f / (4f * pi))
            when (m) {
                0 -> factor * (z / r)
                1 -> factor * (x / r)
                -1 -> factor * (y / r)
                else -> 0f
            }
        }
        2 -> {
            when (m) {
                0 -> sqrt(5f / (16f * pi)) * (3f * z * z - r * r) / (r * r)
                1 -> sqrt(15f / (4f * pi)) * (x * z) / (r * r)
                -1 -> sqrt(15f / (4f * pi)) * (y * z) / (r * r)
                2 -> sqrt(15f / (16f * pi)) * (x * x - y * y) / (r * r)
                -2 -> sqrt(15f / (4f * pi)) * (x * y) / (r * r)
                else -> 0f
            }
        }
        3 -> {
            when (m) {
                0 -> sqrt(7f / (16f * pi)) * z * (5f * z * z - 3f * r * r) / (r * r * r)
                1 -> sqrt(21f / (32f * pi)) * x * (5f * z * z - r * r) / (r * r * r)
                -1 -> sqrt(21f / (32f * pi)) * y * (5f * z * z - r * r) / (r * r * r)
                2 -> sqrt(105f / (16f * pi)) * z * (x * x - y * y) / (r * r * r)
                -2 -> sqrt(105f / (4f * pi)) * (x * y * z) / (r * r * r)
                3 -> sqrt(35f / (32f * pi)) * x * (x * x - 3f * y * y) / (r * r * r)
                -3 -> sqrt(35f / (32f * pi)) * y * (3f * x * x - y * y) / (r * r * r)
                else -> 0f
            }
        }
        else -> 0f
    }
}

fun evalWavefunction(n: Int, l: Int, m: Int, x: Float, y: Float, z: Float, Z: Int): Float {
    val r = sqrt(x*x + y*y + z*z)
    val R = radial(n, l, r, Z)
    val Y = angular(l, m, x, y, z, r)
    return R * Y
}

fun checkRadialNormalization(n: Int, l: Int, Z: Int): Float {
    var sum = 0.0
    val dr = 0.01f
    for (i in 0..15000) {
        val r = i * dr
        val R = radial(n, l, r, Z)
        sum += (R * R) * (r * r) * dr
    }
    return sum.toFloat()
}

suspend fun generatePointCloud(
    n: Int, l: Int, m: Int, Z: Int,
    targetSamples: Int,
    progressCallback: (Float) -> Unit
): PointCloud = withContext(Dispatchers.Default) {
    val cloud = PointCloud(targetSamples)
    
    // Expectation value of r
    val rExp = (3f * n * n - l * (l + 1)) / (2f * Z)
    // 3.5x expectation value safely captures the outer lobes for plotting
    val bounds = rExp * 3.5f
    
    var maxProb = 0f
    // Find maximum probability density to set rejection threshold
    for (i in 0 until 20000) {
        val x = Random.nextFloat() * 2 * bounds - bounds
        val y = Random.nextFloat() * 2 * bounds - bounds
        val z = Random.nextFloat() * 2 * bounds - bounds
        if (x*x + y*y + z*z > bounds*bounds) continue
        val psi = evalWavefunction(n, l, m, x, y, z, Z)
        val p = psi * psi
        if (p > maxProb) maxProb = p
    }
    
    maxProb *= 1.2f
    if (maxProb == 0f) maxProb = 1f
    
    // Validation Output (Dev-only)
    val radialNorm = checkRadialNormalization(n, l, Z)
    val radialNodes = n - l - 1
    val species = getSpeciesName(Z)
    Log.d("OrbitalPhysics", "=== Orbital Generated: $species ($n, $l, $m) ===")
    Log.d("OrbitalPhysics", "Expected <r> = $rExp, Bounds = $bounds")
    Log.d("OrbitalPhysics", "Radial Nodes = $radialNodes, Angular Nodes = $l")
    Log.d("OrbitalPhysics", "Radial Normalization Integral (should be ~1.0) = $radialNorm")
    
    var accepted = 0
    var totalTries = 0
    while (accepted < targetSamples) {
        val x = Random.nextFloat() * 2 * bounds - bounds
        val y = Random.nextFloat() * 2 * bounds - bounds
        val z = Random.nextFloat() * 2 * bounds - bounds
        if (x*x + y*y + z*z > bounds*bounds) continue
        
        val psi = evalWavefunction(n, l, m, x, y, z, Z)
        val prob = psi * psi
        
        if (Random.nextFloat() * maxProb < prob) {
            cloud.x[accepted] = x
            cloud.y[accepted] = y
            cloud.z[accepted] = z
            cloud.phase[accepted] = if (psi > 0) 1 else -1
            cloud.prob[accepted] = prob / maxProb
            accepted++
            
            if (accepted % 2000 == 0) {
                progressCallback(accepted.toFloat() / targetSamples)
                yield()
            }
        }
        totalTries++
        if (totalTries > targetSamples * 4000) {
            Log.d("OrbitalPhysics", "Sampling hit iteration cap. MaxProb might be too high or bounds too large.")
            break
        }
    }
    cloud
}
