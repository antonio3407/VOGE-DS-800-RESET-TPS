package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SequencesRepository
import com.example.util.ObdConnectionStatus
import com.example.util.ObdManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Voge 800 MSE", appName)
    }

    @Test
    fun `verify TPS reset sequence has 13 exact steps`() {
        val tpsSeq = SequencesRepository.tpsResetSequence
        assertNotNull(tpsSeq)
        assertEquals(13, tpsSeq.steps.size)

        // Check key steps and their timers
        val step6 = tpsSeq.steps[5] // Apertura lenta
        assertEquals(8, step6.durationSeconds)

        val step7 = tpsSeq.steps[6] // Mantener a fondo
        assertEquals(20, step7.durationSeconds)

        val step8 = tpsSeq.steps[7] // Cierre lento
        assertEquals(8, step8.durationSeconds)

        val step10 = tpsSeq.steps[9] // Espera crucial
        assertEquals(30, step10.durationSeconds)

        val step12 = tpsSeq.steps[11] // Apagado y reposo
        assertEquals(30, step12.durationSeconds)

        // Verify each step has instruction and voice text
        tpsSeq.steps.forEach { step ->
            assertTrue(step.instruction.isNotBlank())
            assertTrue(step.voiceInstruction.isNotBlank())
        }
    }

    @Test
    fun `verify OBD manager simulation and volatile reset`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val obdManager = ObdManager(app)

        obdManager.startSimulationMode()
        assertEquals(ObdConnectionStatus.SIMULATED, obdManager.connectionStatus.value)

        // Verify initial telemetry
        val initialTelemetry = obdManager.telemetry.value
        assertTrue("RPM should be around idle in simulation", initialTelemetry.rpm > 1000)

        // Verify volatile memory reset
        val resetResult = obdManager.resetVolatileMemory()
        assertTrue("Volatile memory reset should succeed in simulation", resetResult)
        assertEquals(true, obdManager.telemetry.value.resetSuccess)

        obdManager.disconnect()
        assertEquals(ObdConnectionStatus.DISCONNECTED, obdManager.connectionStatus.value)
    }
}
