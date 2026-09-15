package com.pfa.driverui.network

import com.pfa.driverui.model.ClimatePreferences
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.model.MirrorsPreferences
import com.pfa.driverui.model.SeatPreferences
import com.pfa.driverui.model.SteeringWheelPreferences
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Résultat d'un événement reçu sur /ws/identify. */
data class IdentifyEvent(
    val speaker: String,
    val score: Float,
    val scores: Map<String, Float>,
    val adapted: Boolean,
    val preferences: DriverPreferences?,
)

/** Événements reçus sur /ws/enroll pendant l'enrôlement automatique au micro. */
sealed class EnrollEvent {
    data class SampleRecorded(val index: Int, val total: Int, val durationSeconds: Float) : EnrollEvent()
    data class Done(val driverName: String, val samplesTotal: Int) : EnrollEvent()
    data class Error(val message: String) : EnrollEvent()
}

/** Convertit un JSONObject (forme de preferences.json) en DriverPreferences. */
fun parseDriverPreferences(json: JSONObject): DriverPreferences {
    val seatJson = json.getJSONObject("seat")
    val seat = SeatPreferences(
        positionAvantArriere = seatJson.getDouble("position_avant_arriere").toFloat(),
        hauteur = seatJson.getDouble("hauteur").toFloat(),
        inclinaisonDossier = seatJson.getDouble("inclinaison_dossier").toFloat(),
    )

    val steeringJson = json.getJSONObject("steering_wheel")
    val steeringWheel = SteeringWheelPreferences(
        hauteur = steeringJson.getDouble("hauteur").toFloat(),
        profondeur = steeringJson.getDouble("profondeur").toFloat(),
    )

    val mirrorsJson = json.getJSONObject("mirrors")
    fun parseMirror(key: String): MirrorPreferences {
        val m = mirrorsJson.getJSONObject(key)
        return MirrorPreferences(
            horizontal = m.getDouble("horizontal").toFloat(),
            vertical = m.getDouble("vertical").toFloat(),
        )
    }
    val mirrors = MirrorsPreferences(
        gauche = parseMirror("retroviseur_gauche"),
        droit = parseMirror("retroviseur_droit"),
        interieur = parseMirror("retroviseur_interieur"),
    )

    val climateJson = json.getJSONObject("climate")
    val climate = ClimatePreferences(
        temperatureCelsius = climateJson.getDouble("temperature_celsius").toFloat(),
        vitesseVentilation = climateJson.getDouble("vitesse_ventilation").toFloat(),
    )

    return DriverPreferences(seat, steeringWheel, mirrors, climate)
}

/** Convertit un DriverPreferences en JSONObject (forme de preferences.json), pour l'envoi PUT. */
fun DriverPreferences.toJson(): JSONObject {
    val root = JSONObject()

    val seatJson = JSONObject()
    seatJson.put("position_avant_arriere", seat.positionAvantArriere)
    seatJson.put("hauteur", seat.hauteur)
    seatJson.put("inclinaison_dossier", seat.inclinaisonDossier)
    root.put("seat", seatJson)

    val steeringJson = JSONObject()
    steeringJson.put("hauteur", steeringWheel.hauteur)
    steeringJson.put("profondeur", steeringWheel.profondeur)
    root.put("steering_wheel", steeringJson)

    fun mirrorJson(m: MirrorPreferences) = JSONObject().apply {
        put("horizontal", m.horizontal)
        put("vertical", m.vertical)
    }
    val mirrorsJson = JSONObject()
    mirrorsJson.put("retroviseur_gauche", mirrorJson(mirrors.gauche))
    mirrorsJson.put("retroviseur_droit", mirrorJson(mirrors.droit))
    mirrorsJson.put("retroviseur_interieur", mirrorJson(mirrors.interieur))
    root.put("mirrors", mirrorsJson)

    val climateJson = JSONObject()
    climateJson.put("temperature_celsius", climate.temperatureCelsius)
    climateJson.put("vitesse_ventilation", climate.vitesseVentilation)
    root.put("climate", climateJson)

    return root
}

/**
 * Client réseau minimal pour l'API du système (api_server.py) : REST
 * pour la liste des conducteurs et les préférences, WebSocket pour
 * l'identification en flux continu.
 */
class ApiClient(private val host: String) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // pas de timeout : le WebSocket reste ouvert en continu
        .build()

    private fun restBaseUrl() = "http://$host"
    private fun wsBaseUrl() = "ws://$host"

    fun connectIdentifyWebSocket(
        onEvent: (IdentifyEvent) -> Unit,
        onFailure: (String) -> Unit,
    ): WebSocket {
        val request = Request.Builder()
            .url("${wsBaseUrl()}/ws/identify?adapt=false")
            .build()

        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        onFailure(json.getString("error"))
                        return
                    }
                    val speaker = json.getString("speaker")
                    val score = json.getDouble("score").toFloat()

                    val scoresJson = json.getJSONObject("scores")
                    val scores = mutableMapOf<String, Float>()
                    val keys = scoresJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        scores[key] = scoresJson.getDouble(key).toFloat()
                    }

                    val adapted = json.optBoolean("adapted", false)
                    val preferences = if (json.isNull("preferences")) {
                        null
                    } else {
                        parseDriverPreferences(json.getJSONObject("preferences"))
                    }

                    onEvent(IdentifyEvent(speaker, score, scores, adapted, preferences))
                } catch (e: JSONException) {
                    onFailure("Réponse invalide du serveur : ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t.message ?: "Connexion perdue")
            }
        })
    }

    /**
     * Ouvre /ws/enroll pour enrôler un nouveau conducteur : le serveur
     * enregistre `samples` échantillons au micro de la machine qui
     * exécute api_server.py, avec un événement de progression après
     * chaque échantillon, puis un événement "done" une fois l'enrôlement
     * terminé (ou "error" en cas d'échec).
     */
    fun connectEnrollWebSocket(
        driverName: String,
        samples: Int,
        onEvent: (EnrollEvent) -> Unit,
        onFailure: (String) -> Unit,
    ): WebSocket {
        val encodedName = URLEncoder.encode(driverName, "UTF-8")
        val request = Request.Builder()
            .url("${wsBaseUrl()}/ws/enroll?driver_name=$encodedName&samples=$samples")
            .build()

        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "sample_recorded" -> onEvent(
                            EnrollEvent.SampleRecorded(
                                index = json.getInt("index"),
                                total = json.getInt("total"),
                                durationSeconds = json.getDouble("duration").toFloat(),
                            )
                        )
                        "done" -> onEvent(
                            EnrollEvent.Done(
                                driverName = json.getString("driver"),
                                samplesTotal = json.getInt("samples_total"),
                            )
                        )
                        "error" -> onEvent(EnrollEvent.Error(json.optString("message", "Erreur inconnue")))
                        else -> onFailure("Message inattendu du serveur : $text")
                    }
                } catch (e: JSONException) {
                    onFailure("Réponse invalide du serveur : ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t.message ?: "Connexion perdue")
            }
        })
    }

    fun fetchDrivers(onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url("${restBaseUrl()}/drivers").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Erreur réseau")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Erreur serveur (${it.code})")
                        return
                    }
                    val body = it.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val array = json.getJSONArray("drivers")
                    val list = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        list.add(array.getString(i))
                    }
                    onResult(list)
                }
            }
        })
    }

    fun deleteDriver(driverName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder()
            .url("${restBaseUrl()}/drivers/$driverName")
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Erreur réseau")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Erreur serveur (${it.code})")
                        return
                    }
                    onSuccess()
                }
            }
        })
    }

    fun renameDriver(
        oldName: String,
        newName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val encodedNewName = URLEncoder.encode(newName, "UTF-8")
        val request = Request.Builder()
            .url("${restBaseUrl()}/drivers/$oldName/rename?new_name=$encodedNewName")
            .post("".toRequestBody(null))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Erreur réseau")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        onError("Erreur serveur (${it.code}) : $errorBody")
                        return
                    }
                    onSuccess()
                }
            }
        })
    }

    fun fetchPreferences(
        driverName: String,
        onResult: (DriverPreferences?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val request = Request.Builder().url("${restBaseUrl()}/preferences/$driverName").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Erreur réseau")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code == 404) {
                        onResult(null)
                        return
                    }
                    if (!it.isSuccessful) {
                        onError("Erreur serveur (${it.code})")
                        return
                    }
                    val body = it.body?.string() ?: "{}"
                    onResult(parseDriverPreferences(JSONObject(body)))
                }
            }
        })
    }

    fun savePreferences(
        driverName: String,
        preferences: DriverPreferences,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val jsonBody = preferences.toJson().toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${restBaseUrl()}/preferences/$driverName")
            .put(jsonBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Erreur réseau")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string()
                        onError("Erreur serveur (${it.code}) : $errorBody")
                        return
                    }
                    onSuccess()
                }
            }
        })
    }
}
