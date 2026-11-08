package com.celiocastro.h8tvcenter

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.util.ArrayDeque
import java.util.Locale

class EmulatorActivity : ComponentActivity() {
    private lateinit var retroView: GLRetroView
    private lateinit var romFile: File
    private lateinit var stateFile: File
    private lateinit var sramFile: File
    private lateinit var stateDirectory: File
    private lateinit var systemDirectory: File
    private var stateBaseName = "game"
    private var currentSlot = 1
    private var fastForward = false
    private var audioOn = true
    private var cheatCodes = mutableListOf<Pair<String, String>>()
    private val rewindStates = ArrayDeque<ByteArray>()
    private val rewindHandler = Handler(Looper.getMainLooper())
    private val rewindCapture = object : Runnable {
        override fun run() {
            if (::retroView.isInitialized) {
                try {
                    val snapshot = retroView.serializeState()
                    synchronized(rewindStates) {
                        rewindStates.addLast(snapshot)
                        while (rewindStates.size > 2) rewindStates.removeFirst()
                    }
                } catch (_: Exception) { }
            }
            rewindHandler.postDelayed(this, 5000)
        }
    }

    private val cheatPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadCheatFile(uri)
    }
    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) importBiosFolder(uri)
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
        stateBaseName = "$system-$safeTitle"
        val savesDir = File(filesDir, "saves").apply { mkdirs() }
        sramFile = File(savesDir, "$stateBaseName.srm")
        stateDirectory = File(filesDir, "states").apply { mkdirs() }
        updateStateFile()

        systemDirectory = File(filesDir, "system").apply { mkdirs() }
        val data = GLRetroViewData(this).apply {
            coreFilePath = coreFile.absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = this@EmulatorActivity.systemDirectory.absolutePath
            savesDirectory = savesDir.absolutePath
            saveRAMState = if (sramFile.exists()) sramFile.readBytes() else null
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
        "NINTENDO 64" -> "mupen64plus_next_gles3_libretro_android.so"
        "NINTENDO DS" -> "melonds_libretro_android.so"
        "DREAMCAST" -> "flycast_libretro_android.so"
        "PSP" -> "ppsspp_libretro_android.so"
        "SEGA SATURN" -> "yabasanshiro_libretro_android.so"
        "NATIVE32" -> "native32emu_libretro_android.so"
        else -> throw IllegalArgumentException("Sistema ainda sem núcleo configurado: $system")
    }

    private fun copyRomToCache(uri: Uri, title: String): File {
        val extension = title.substringAfterLast('.', "rom").take(8).let { ".$it" }
        val safeFolder = title.substringBeforeLast('.', title).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val folder = File(cacheDir, "roms/$safeFolder").apply { mkdirs() }
        val mainFile = File(folder, "game$extension")
        copyDocument(uri, mainFile)
        val ext = extension.lowercase(Locale.ROOT)
        if (ext == ".cue" || ext == ".gdi" || ext == ".m3u") {
            val text = mainFile.readText()
            val referenced = linkedSetOf<String>()
            if (ext == ".cue") {
                val quoted = Regex("""(?i)^\s*FILE\s+"([^"]+)"\s+\w+""")
                val plain = Regex("""(?i)^\s*FILE\s+(\S+)\s+\w+""")
                text.lineSequence().forEach { line ->
                    (quoted.find(line)?.groupValues?.get(1) ?: plain.find(line)?.groupValues?.get(1))
                        ?.let { referenced.add(it.substringAfterLast('/').substringAfterLast('\\')) }
                }
            } else if (ext == ".gdi") {
                val gdiTrack = Regex("""(?i)^\s*\d+\s+\d+\s+\d+\s+\d+\s+"?(.+?\.(?:bin|raw|wav|iso))"?\s+\d+\s*$""")
                text.lineSequence().forEach { line ->
                    gdiTrack.find(line)?.groupValues?.get(1)?.let {
                        referenced.add(it.substringAfterLast('/').substringAfterLast('\\'))
                    }
                }
            } else {
                text.lineSequence().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { referenced.add(it.substringAfterLast('/').substringAfterLast('\\')) }
            }
            copySiblingFiles(uri, folder, referenced)
        }
        return mainFile
    }

    private fun copyDocument(uri: Uri, destination: File) {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "O Android não forneceu acesso ao arquivo." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copySiblingFiles(sourceUri: Uri, destinationFolder: File, names: Set<String>) {
        if (names.isEmpty()) return
        val treeRaw = getSharedPreferences("h8tv_library", MODE_PRIVATE).getString("rom_tree_uri", null)
            ?: throw IllegalStateException("A pasta original de ROMs não está vinculada.")
        val tree = Uri.parse(treeRaw)
        val documentId = DocumentsContract.getDocumentId(sourceUri)
        val parentId = documentId.substringBeforeLast('/', "")
        require(parentId.isNotEmpty()) { "Não foi possível localizar os arquivos da mesma pasta." }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val found = HashSet<String>()
        contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                if (names.any { it.equals(name, ignoreCase = true) } &&
                    !DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeCol))) {
                    val id = cursor.getString(idCol)
                    val siblingUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    copyDocument(siblingUri, File(destinationFolder, name))
                    found.add(name.lowercase(Locale.ROOT))
                }
            }
        }
        val missing = names.filterNot { found.contains(it.lowercase(Locale.ROOT)) }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Arquivos associados não encontrados: ${missing.joinToString()}")
        }
    }

    private fun showGameMenu(title: String) {
        val options = arrayOf(
            "Continuar",
            "Salvar estado (slot $currentSlot)",
            "Carregar estado (slot $currentSlot)",
            "Trocar slot de save",
            "Reiniciar jogo",
            if (fastForward) "Desativar avanço rápido" else "Avanço rápido",
            if (audioOn) "Desativar áudio" else "Ativar áudio",
            "Filtro CRT",
            "Imagem nítida",
            "Filtro LCD",
            "Imagem padrão",
            "Rewind (voltar 5 segundos)",
            "Carregar cheats (.cht)",
            "Gerenciar cheats",
            "Configurar controle",
            "Trocar disco (jogos multidisco)",
            "Importar BIOS/arquivos do sistema",
            "Fechar jogo"
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> retroView.requestFocus()
                    1 -> saveState()
                    2 -> loadState()
                    3 -> chooseSaveSlot()
                    4 -> confirmReset()
                    5 -> { fastForward = !fastForward; retroView.frameSpeed = if (fastForward) 2 else 1 }
                    6 -> { audioOn = !audioOn; retroView.audioEnabled = audioOn }
                    7 -> retroView.shader = ShaderConfig.CRT
                    8 -> retroView.shader = ShaderConfig.Sharp
                    9 -> retroView.shader = ShaderConfig.LCD
                    10 -> retroView.shader = ShaderConfig.Default
                    11 -> rewindFiveSeconds()
                    12 -> cheatPicker.launch(arrayOf("*/*"))
                    13 -> showCheats()
                    14 -> showControllerSettings()
                    15 -> showDiskSelector()
                    16 -> biosPicker.launch(null)
                    17 -> finish()
                }
            }.show()
    }


    private fun rewindFiveSeconds() {
        try {
            val previous = synchronized(rewindStates) {
                if (rewindStates.size < 2) null else {
                    rewindStates.removeLast()
                    rewindStates.peekLast()
                }
            }
            if (previous == null || !retroView.unserializeState(previous)) {
                Toast.makeText(this, "Ainda não há histórico suficiente para rewind.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Rewind não é suportado por este núcleo.", Toast.LENGTH_LONG).show()
        }
    }


    private fun importBiosFolder(tree: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { contentResolver.takePersistableUriPermission(tree, flags) } catch (_: Exception) {}
            systemDirectory = File(filesDir, "system").apply { mkdirs() }
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            val copied = intArrayOf(0)
            copyBiosDirectory(tree, children, systemDirectory, copied)
            Toast.makeText(this, "${copied[0]} arquivo(s) de sistema/BIOS importado(s).", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao importar BIOS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyBiosDirectory(tree: Uri, childrenUri: Uri, destination: File, copied: IntArray) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol)
                val name = File(cursor.getString(nameCol) ?: continue).name
                val target = File(destination, name)
                val mime = cursor.getString(mimeCol)
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    target.mkdirs()
                    copyBiosDirectory(tree, DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), target, copied)
                } else {
                    copyDocument(DocumentsContract.buildDocumentUriUsingTree(tree, id), target)
                    copied[0]++
                }
            }
        }
    }


    private fun showDiskSelector() {
        try {
            val count = retroView.getAvailableDisks()
            if (count <= 1) {
                Toast.makeText(this, "Este jogo/núcleo não disponibiliza troca de discos.", Toast.LENGTH_LONG).show()
                return
            }
            val current = retroView.getCurrentDisk().coerceIn(0, count - 1)
            val labels = Array(count) { "Disco ${it + 1}" }
            AlertDialog.Builder(this).setTitle("Selecionar disco")
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    retroView.changeDisk(which)
                    dialog.dismiss()
                }.setNegativeButton("Cancelar", null).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Troca de disco não suportada por este núcleo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showControllerSettings() {
        try {
            val ports = retroView.getControllers()
            if (ports.isEmpty()) {
                Toast.makeText(this, "Este núcleo não informou tipos de controle configuráveis.", Toast.LENGTH_LONG).show()
                return
            }
            showControllerPort(ports, 0)
        } catch (e: Exception) {
            Toast.makeText(this, "Configuração de controle indisponível neste núcleo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showControllerPort(ports: Array<Array<com.swordfish.libretrodroid.Controller>>, port: Int) {
        if (port >= ports.size) return
        val choices = ports[port]
        if (choices.isEmpty()) {
            showControllerPort(ports, port + 1)
            return
        }
        val labels = choices.mapIndexed { index, controller ->
            controller.description?.takeIf { it.isNotBlank() } ?: "Tipo de controle ${index + 1}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Controle • Porta ${port + 1}")
            .setItems(labels) { _, selected ->
                try { retroView.setControllerType(port, selected) } catch (_: Exception) {}
                showControllerPort(ports, port + 1)
            }
            .setNegativeButton("Pular") { _, _ -> showControllerPort(ports, port + 1) }
            .show()
    }

    private fun updateStateFile() {
        stateFile = File(stateDirectory, "$stateBaseName-slot$currentSlot.state")
    }

    private fun chooseSaveSlot() {
        val slots = Array(5) { "Slot ${it + 1}" }
        AlertDialog.Builder(this).setTitle("Slot de salvamento")
            .setSingleChoiceItems(slots, currentSlot - 1) { dialog, which ->
                currentSlot = which + 1
                updateStateFile()
                dialog.dismiss()
                Toast.makeText(this, "Slot $currentSlot selecionado.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun saveSRAM() {
        try {
            sramFile.writeBytes(retroView.serializeSRAM())
        } catch (_: Exception) { }
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
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event)
        }
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

    override fun onResume() {
        super.onResume()
        if (::retroView.isInitialized) rewindHandler.postDelayed(rewindCapture, 5000)
    }

    override fun onPause() {
        rewindHandler.removeCallbacks(rewindCapture)
        if (::retroView.isInitialized) {
            saveSRAM()
            if (isFinishing) saveState()
        }
        super.onPause()
    }

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_TITLE = "title"
    }
}
