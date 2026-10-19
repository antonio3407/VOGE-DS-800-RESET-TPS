package com.example.data

import com.example.model.ActionIcon
import com.example.model.MotorcycleSequence
import com.example.model.SequenceCategory
import com.example.model.SequenceStep

object SequencesRepository {

    val tpsResetSequence = MotorcycleSequence(
        id = "voge_tps_reset",
        title = "Reset del Sensor TPS y Calibración Bosch MSE 6.0",
        category = SequenceCategory.BOSCH_ECU,
        shortSummary = "Guía paso a paso para el reseteo del sensor de posición del acelerador (TPS) y mariposa de la Voge DS 800 Rally.",
        difficulty = "Precisión de tiempos",
        estimatedDuration = "10 - 15 min",
        prerequisites = listOf(
            "Moto en un lugar tranquilo y ventilado",
            "Sobre caballete o pata de cabra",
            "Motor completamente apagado",
            "Llave de contacto a mano"
        ),
        applicability = "Voge DS 800 Rally • Bosch MSE 6.0",
        steps = listOf(
            SequenceStep(
                stepNumber = 1,
                title = "Preparación",
                instruction = "Asegúrate de que la moto esté en un lugar tranquilo, sobre el caballete o la pata de cabra, y con el motor completamente apagado.",
                voiceInstruction = "Paso uno. Preparación. Asegúrate de que la moto esté en un lugar tranquilo, sobre el caballete o la pata de cabra, y con el motor completamente apagado.",
                durationSeconds = 0,
                actionTarget = "Moto en Caballete / Pata",
                actionIcon = ActionIcon.HANDLEBAR_BUTTON,
                tip = "La moto debe estar estable y en punto muerto antes de empezar."
            ),
            SequenceStep(
                stepNumber = 2,
                title = "Corte de encendido (Kill Switch en OFF)",
                instruction = "Pon el botón rojo de la piña derecha en posición de OFF (bloqueo de motor).",
                voiceInstruction = "Paso dos. Corte de encendido. Pon el botón rojo de la piña derecha en posición de apagado, bloqueo de motor.",
                durationSeconds = 0,
                actionTarget = "Botón rojo piña derecha -> OFF",
                actionIcon = ActionIcon.HANDLEBAR_BUTTON,
                tip = "Verifica que el interruptor basculante rojo esté en posición de corte de motor."
            ),
            SequenceStep(
                stepNumber = 3,
                title = "Contacto (Llave en ON)",
                instruction = "Gira la llave a la posición de encendido. Espera a que la pantalla TFT se encienda por completo y termine de hacer el saludo inicial.",
                voiceInstruction = "Paso tres. Contacto. Gira la llave a la posición de encendido. Espera a que la pantalla TFT se encienda por completo y termine de hacer el saludo inicial.",
                durationSeconds = 6,
                actionTarget = "Llave de contacto -> ON",
                actionIcon = ActionIcon.IGNITION_KEY,
                tip = "Deja que la pantalla TFT y los testigos completen el inicio antes de continuar."
            ),
            SequenceStep(
                stepNumber = 4,
                title = "Cambio de modo a Sport",
                instruction = "En la piña derecha del manillar selecciona el Modo Sport. Esto es vital para que abra el rango al 100%.",
                voiceInstruction = "Paso cuatro. Cambio de modo. En la piña derecha del manillar selecciona el Modo Sport. Esto es vital para que abra el rango al cien por cien.",
                durationSeconds = 0,
                actionTarget = "Modo de conducción -> SPORT",
                actionIcon = ActionIcon.HANDLEBAR_BUTTON,
                tip = "El modo Sport activa la apertura máxima del potenciómetro de la mariposa."
            ),
            SequenceStep(
                stepNumber = 5,
                title = "Habilitar sistema (Kill Switch en ON)",
                instruction = "Sin quitar la llave ni tocar la pantalla, cambia el botón rojo de la piña derecha a la posición de ON (posición normal de marcha), pero NO pulses el botón de arranque. No enciendas el motor.",
                voiceInstruction = "Paso cinco. Habilitar sistema. Sin quitar la llave ni tocar la pantalla, cambia el botón rojo de la piña derecha a la posición de encendido normal, pero no pulses el botón de arranque. No enciendas el motor.",
                durationSeconds = 0,
                actionTarget = "Botón rojo -> ON (Sin arrancar)",
                actionIcon = ActionIcon.HANDLEBAR_BUTTON,
                tip = "NO toques el botón de arranque. El motor debe permanecer apagado."
            ),
            SequenceStep(
                stepNumber = 6,
                title = "Apertura lenta del acelerador",
                instruction = "Gira el puño del acelerador de forma muy constante y muy despacio hasta llegar al tope del fondo. Debes tardar entre 6 y 8 segundos en hacer todo el recorrido.",
                voiceInstruction = "Paso seis. Apertura lenta. Gira el puño del acelerador de forma muy constante y muy despacio hasta llegar al tope del fondo. Tienes ocho segundos para abrirlo suavemente.",
                durationSeconds = 8,
                actionTarget = "Puño de gas (Abrir en 8s)",
                actionIcon = ActionIcon.THROTTLE,
                tip = "Movimiento constante y progresivo. No abras de golpe."
            ),
            SequenceStep(
                stepNumber = 7,
                title = "Mantener acelerador a fondo",
                instruction = "Una vez que el acelerador esté abierto a tope, mantenlo ahí fijo, sin moverlo, durante 20 segundos exactos.",
                voiceInstruction = "Paso siete. Mantener a fondo. Mantén el acelerador abierto a tope, fijo y sin moverlo, durante veinte segundos exactos.",
                durationSeconds = 20,
                actionTarget = "Puño a tope 100% (20s)",
                actionIcon = ActionIcon.THROTTLE,
                tip = "No sueltes ni aflojes la presión contra el tope durante toda la cuenta atrás."
            ),
            SequenceStep(
                stepNumber = 8,
                title = "Cierre lento del acelerador",
                instruction = "Transcurridos los 20 segundos, empieza a soltar el acelerador de forma muy suave y progresiva hasta que regrese a su posición inicial de reposo. Debes tardar entre 6 y 8 segundos en cerrarlo.",
                voiceInstruction = "Paso ocho. Cierre lento. Empieza a soltar el acelerador de forma muy suave y progresiva hasta que regrese a su posición inicial de reposo. Tienes ocho segundos para cerrarlo suavemente.",
                durationSeconds = 8,
                actionTarget = "Puño de gas (Cerrar en 8s)",
                actionIcon = ActionIcon.THROTTLE,
                tip = "Acompaña el puño con la mano; no permitas que golpee el muelle de retorno."
            ),
            SequenceStep(
                stepNumber = 9,
                title = "Guardado en memoria (Llave en OFF)",
                instruction = "Quita el contacto girando la llave a la posición de OFF.",
                voiceInstruction = "Paso nueve. Guardado en memoria. Quita el contacto girando la llave a la posición de apagado.",
                durationSeconds = 0,
                actionTarget = "Llave de contacto -> OFF",
                actionIcon = ActionIcon.IGNITION_KEY,
                tip = "Gira la llave a OFF sin tocar el puño del acelerador."
            ),
            SequenceStep(
                stepNumber = 10,
                title = "Espera crucial (30 segundos)",
                instruction = "Deja la moto completamente apagada durante 30 segundos. En este momento, la ECU Bosch guarda los nuevos valores de voltaje en su memoria permanente.",
                voiceInstruction = "Paso diez. Espera crucial. Deja la moto completamente apagada durante treinta segundos. La centralita Bosch guarda los nuevos valores de voltaje en su memoria permanente.",
                durationSeconds = 30,
                actionTarget = "Moto apagada (30s de guardado)",
                actionIcon = ActionIcon.TIMER,
                tip = "No toques la moto ni enciendas la llave hasta que termine el contador."
            ),
            SequenceStep(
                stepNumber = 11,
                title = "Ciclo térmico (Electroventilador)",
                instruction = "Enciende la moto y espera a que salte el electroventilador y se vuelva a apagar. Mantén el motor a ralentí sin acelerar.",
                voiceInstruction = "Paso once. Ciclo térmico. Enciende la moto y espera a que salte el electroventilador y se vuelva a apagar. No toques el acelerador.",
                durationSeconds = 0,
                actionTarget = "Motor a ralentí hasta electroventilador",
                actionIcon = ActionIcon.ENGINE,
                tip = "El motor debe subir de temperatura hasta que el ventilador se active y se detenga solo."
            ),
            SequenceStep(
                stepNumber = 12,
                title = "Apagado y reposo (30 segundos)",
                instruction = "A continuación apagas la moto y esperas 30 segundos.",
                voiceInstruction = "Paso doce. Apaga la moto con la llave y espera treinta segundos.",
                durationSeconds = 30,
                actionTarget = "Llave a OFF (30s)",
                actionIcon = ActionIcon.TIMER,
                tip = "La ECU asienta los parámetros del ciclo térmico."
            ),
            SequenceStep(
                stepNumber = 13,
                title = "¡Proceso terminado y prueba!",
                instruction = "Una vez pasados los 30 segundos, el proceso habrá terminado. Ya puedes encender la moto de forma normal, arrancar y salir a probarla (puedes volver a poner el modo de conducción que uses habitualmente).",
                voiceInstruction = "Proceso terminado con éxito. Ya puedes encender la moto de forma normal, arrancar y salir a probarla. Puedes volver a poner el modo de conducción que uses habitualmente.",
                durationSeconds = 0,
                actionTarget = "Prueba de conducción",
                actionIcon = ActionIcon.CHECK_LIGHT,
                tip = "Comprueba que la respuesta del gas sea suave y sin tirones."
            )
        )
    )

    val sequences: List<MotorcycleSequence> = listOf(tpsResetSequence)
}
