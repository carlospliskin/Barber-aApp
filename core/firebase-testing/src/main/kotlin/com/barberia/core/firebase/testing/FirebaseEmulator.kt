package com.barberia.core.firebase.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Q1: SDK real de Firebase contra Emulator Suite (proyecto `demo-*`: no existe en la nube).
 * Requiere `cd firebase && npm run emulators` en la Mac. Las reglas de Firestore son las reales.
 *
 * - [reset] borra cuentas y datos entre tests.
 * - [seed]/[read] usan la API REST como administrador (`Bearer owner`): preparan y revisan datos sin reglas.
 * - Todo lo demás pasa por [auth]/[db] como lo haría la app, con un usuario con sesión.
 */
object FirebaseEmulator {
    const val HOST = "10.0.2.2"
    const val PROJECT = "demo-barberia"
    private const val AUTH_PORT = 9199
    private const val FIRESTORE_PORT = 8085
    const val PASSWORD = "secret123"

    val app: FirebaseApp by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirebaseApp.getApps(context).find { it.name == NAME } ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:1:android:1").setApiKey("fake-key").build(),
            NAME
        )
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) } }

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            // Sin caché en disco: cada test ve lo que hay en el servidor.
            firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings { }) }
        }
    }

    /** Borra todas las cuentas y documentos del emulador. */
    fun reset() {
        auth.signOut()
        http("DELETE", "http://$HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT/accounts")
        http("DELETE", "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT/databases/(default)/documents")
    }

    /** Crea la cuenta y deja la sesión iniciada con ella. Devuelve el uid. */
    suspend fun signUp(email: String): String {
        auth.signOut()
        return auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
    }

    /** A14: marca el correo del usuario con sesión como verificado y renueva su token (las reglas lo leen del token). */
    suspend fun verifyEmail() {
        val user = auth.currentUser!!
        http(
            "POST",
            "http://$HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/projects/$PROJECT/accounts:update",
            JSONObject().put("localId", user.uid).put("emailVerified", true).toString()
        )
        user.reload().await()
        user.getIdToken(true).await()
    }

    suspend fun signIn(email: String) {
        auth.signOut()
        auth.signInWithEmailAndPassword(email, PASSWORD).await()
    }

    /** Escribe un documento sin pasar por las reglas. Admite String, números, Boolean, null, Instant, List y Map. */
    fun seed(path: String, data: Map<String, Any?>) {
        // `:commit` con POST: HttpURLConnection no admite PATCH.
        val write = JSONObject().put(
            "update",
            JSONObject()
                .put("name", "projects/$PROJECT/databases/(default)/documents/$path")
                .put("fields", JSONObject().apply { data.forEach { (k, v) -> put(k, toValue(v)) } })
        )
        http("POST", "$DOCS:commit", JSONObject().put("writes", JSONArray().put(write)).toString())
    }

    /** Lee un documento sin pasar por las reglas (null si no existe). */
    fun read(path: String): Map<String, Any?>? {
        val (code, text) = http("GET", "$DOCS/$path")
        if (code == 404) return null
        val fields = JSONObject(text).optJSONObject("fields") ?: return emptyMap()
        return fields.keys().asSequence().associateWith { fromValue(fields.getJSONObject(it)) }
    }

    private const val NAME = "emulator-tests"
    private val DOCS = "http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents"

    private fun toValue(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)

        is Boolean -> JSONObject().put("booleanValue", v)

        is Int, is Long -> JSONObject().put("integerValue", v.toString())

        is Number -> JSONObject().put("doubleValue", v.toDouble())

        is String -> JSONObject().put("stringValue", v)

        is Instant -> JSONObject().put("timestampValue", v.toString())

        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map { toValue(it) })))

        is Map<*, *> -> JSONObject().put(
            "mapValue",
            JSONObject().put("fields", JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), toValue(x)) } })
        )

        else -> error("Tipo no soportado en seed: ${v::class}")
    }

    private fun fromValue(o: JSONObject): Any? = when {
        o.has("nullValue") -> null

        o.has("booleanValue") -> o.getBoolean("booleanValue")

        o.has("integerValue") -> o.getString("integerValue").toLong()

        o.has("doubleValue") -> o.getDouble("doubleValue")

        o.has("stringValue") -> o.getString("stringValue")

        o.has("timestampValue") -> Instant.parse(o.getString("timestampValue"))

        o.has("arrayValue") -> o.getJSONObject("arrayValue").optJSONArray("values")
            ?.let { a -> (0 until a.length()).map { fromValue(a.getJSONObject(it)) } } ?: emptyList<Any?>()

        o.has("mapValue") -> o.getJSONObject("mapValue").optJSONObject("fields")
            ?.let { f -> f.keys().asSequence().associateWith { fromValue(f.getJSONObject(it)) } } ?: emptyMap<String, Any?>()

        else -> error("Valor no soportado: $o")
    }

    private fun http(method: String, url: String, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(code < 400 || code == 404) { "$method $url → $code $text" }
        return code to text
    }
}
