package com.celiocastro.h8tvcenter

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.ViewportAlignment
import java.io.File
import java.util.Locale

class EmulatorActivity : ComponentActivity() {
    private lateinit var retroView: GLRetroView
    private lateinit var romFile: File
    private lateinit var stateFile: File
    private var fastForward = false
    private var audioOn = true
    private var cheatCodes = mutableListOf<Pair<String, String>>()

    private val cheatPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadCheatFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        val romUri = intent.getStringExtra(EXTRA_ROM_URI) ?: return finish()
        val system = intent.getStringExtra(EXTRA_SYSTEM) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Jogo"
        val core = coreFor(system)
        val coreFile = File(applicationInfo.nativeLibraryDir, core)
        if (!coreFile.exists()) {
            Toast.makeText(this, "Núcleo não encontrado: $core", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            romFile = copyRomToCache(Uri.parse(romUri), title)
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao abrir ROM: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val stateDir = File(filesDir, "states").apply { mkdirs() }
        stateFile = File(stateDir, "$safeTitle.state")

        val data = GLRetroViewData(this).apply {
            coreFilePath = coreFile.absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = File(filesDir, "saves").apply { mkdirs() }.absolutePath
            shader = ShaderConfig.Default
            viewportAlignment = ViewportAlignment.CENTER
            preferLowLatencyAudio = true
        }
        retroView = GLRetroView(this, data).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        lifecycle.addObserver(retroView)

        val frame = FrameLayout(this)
        frame.setBackgroundColor(android.graphics.Color.BLACK)
        frame.addView(retroView, FrameLayout.LayoutParams(-1, -1))
        val menu = Button(this).apply {
            text = "☰  MENU"
            isAllCaps = false
            setOnClickListener { showGameMenu(title) }
        }
        val menuParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        menuParams.setMargins(12, 12, 18, 12)
        frame.addView(menu, menuParams)
        setContentView(frame)
        retroView.requestFocus()
    }

    private fun coreFor(system: String): String = when (system.uppercase(Locale.ROOT)) {
        "ATARI 2600" -> "stella_libretro_android.so"
        "NES" -> "nestopia_libretro_android.so"
        "MASTER SYSTEM", "MEGA DRIVE" -> "genesis_plus_gx_libretro_android.so"
        "GAME BOY", "GAME BOY COLOR" -> "gambatte_libretro_android.so"
        "GAME BOY ADVANCE" -> "mgba_libretro_android.so"
        "SUPER NINTENDO" -> "snes9x_libretro_android.so"
        "ARCADE" -> "fbneo_libretro_android.so"
        "PLAYSTATION" -> "pcsx_rearmed_libretro_android.so"
        "NINTENDO 64" -> "mupen64plus_next_libretro_android.so"
        "NINTENDO DS" -> "desmume_libretro_android.so"
        "DREAMCAST" -> "flycast_libretro_android.so"
        "PSP" -> "ppsspp_libretro_android.so"
        "SEGA SATURN" -> "beetle_saturn_libretro_android.so"
        else -> throw IllegalArgumentException("Sistema ainda sem núcleo configurado: $system")
    }

    private fun copyRomToCache(uri: Uri, title: String): File {
        val extension = title.substringAfterLast('.', "rom").take(8).let { ".$it" }
        val target = File(cacheDir, "active_rom$extension")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "O Android não forneceu acesso ao arquivo." }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun showGameMenu(title: String) {
        val options = arrayOf(
            "Continuar",
            "Salvar estado",
            "Carregar estado",
            "Reiniciar jogo",
            if (fastForward) "Desativar avanço rápido" else "Avanço rápido",
            if (audioOn) "Desativar áudio" else "Ativar áudio",
            "Filtro CRT",
            "Imagem nítida",
            "Carregar cheats (.cht)",
            "Gerenciar cheats",
            "Fechar jogo"
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> retroView.requestFocus()
                    1 -> saveState()
                    2 -> loadState()
                    3 -> confirmReset()
                    4 -> { fastForward = !fastForward; retroView.frameSpeed = if (fastForward) 2 else 1 }
                    5 -> { audioOn = !audioOn; retroView.audioEnabled = audioOn }
                    6 -> retroView.shader = ShaderConfig.CRT
                    7 -> retroView.shader = ShaderConfig.Sharp
                    8 -> cheatPicker.launch(arrayOf("*/*"))
                    9 -> showCheats()
                    10 -> finish()
                }
            }.show()
    }

    private fun saveState() {
        try {
            stateFile.writeBytes(retroView.serializeState())
            Toast.makeText(this, "Estado salvo.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Este núcleo não conseguiu salvar o estado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadState() {
        try {
            if (!stateFile.exists()) {
                Toast.makeText(this, "Ainda não existe um estado salvo.", Toast.LENGTH_SHORT).show()
                return
            }
            if (!retroView.unserializeState(stateFile.readBytes())) {
                Toast.makeText(this, "O núcleo recusou este estado.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao carregar estado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this).setMessage("Reiniciar o jogo?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Reiniciar") { _, _ -> retroView.reset() }.show()
    }

    private fun loadCheatFile(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Arquivo vazio")
            val codes = linkedMapOf<Int, Pair<String, String>>()
            val descRegex = Regex("""cheat(\d+)_desc\s*=\s*["']?(.*?)["']?\s*$""", RegexOption.IGNORE_CASE)
            val codeRegex = Regex("""cheat(\d+)_code\s*=\s*["'](.*?)["']\s*$""", RegexOption.IGNORE_CASE)
            raw.lineSequence().forEach { line ->
                descRegex.find(line)?.let { m ->
                    val i = m.groupValues[1].toInt()
                    val old = codes[i] ?: Pair("", "")
                    codes[i] = Pair(m.groupValues[2].trim(), old.second)
                }
                codeRegex.find(line)?.let { m ->
                    val i = m.groupValues[1].toInt()
                    val old = codes[i] ?: Pair("Cheat $i", "")
                    codes[i] = Pair(old.first, m.groupValues[2].trim())
                }
            }
            cheatCodes = codes.toSortedMap().values.filter { it.second.isNotBlank() }.toMutableList()
            if (cheatCodes.isEmpty()) {
                Toast.makeText(this, "Nenhum código compatível encontrado no arquivo.", Toast.LENGTH_LONG).show()
            } else showCheats()
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível ler o arquivo de cheats.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCheats() {
        if (cheatCodes.isEmpty()) {
            AlertDialog.Builder(this).setMessage("Carregue um arquivo .cht primeiro.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = cheatCodes.map { it.first.ifBlank { it.second } }.toTypedArray()
        val enabled = BooleanArray(cheatCodes.size)
        AlertDialog.Builder(this).setTitle("Cheats")
            .setMultiChoiceItems(labels, enabled) { _, index, checked ->
                try { retroView.setCheat(index, checked, cheatCodes[index].second) } catch (_: Exception) {}
            }
            .setPositiveButton("Aplicar", null)
            .setNeutralButton("Desativar todos") { _, _ ->
                cheatCodes.indices.forEach { index -> try { retroView.setCheat(index, false, cheatCodes[index].second) } catch (_: Exception) {} }
            }.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::retroView.isInitialized) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
                showGameMenu(intent.getStringExtra(EXTRA_TITLE) ?: "Jogo")
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                showGameMenu(intent.getStringExtra(EXTRA_TITLE) ?: "Jogo")
                return true
            }
            retroView.sendKeyEvent(event.action, event.keyCode)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (::retroView.isInitialized) {
            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD,
                event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onPause() {
        if (::retroView.isInitialized && isFinishing) saveState()
        super.onPause()
    }

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_TITLE = "title"
    }
}
