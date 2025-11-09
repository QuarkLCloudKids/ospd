package com.quarlcloud.opsdonwloades.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.quarlcloud.opsdonwloades.utils.TikTokAutomation

class TikTokAccessibilityService : AccessibilityService() {

    private var retriesLeft = 20
    private var autoCopySchedulerRunning = false
    private val handler by lazy { android.os.Handler(mainLooper) }

    private val tiktokPackages = setOf(
        "com.zhiliaoapp.musically", // TikTok global
        "com.ss.android.ugc.trill",  // Alternative package name
        // Paquetes donde puede residir la hoja de compartir
        "android",
        "com.android.systemui"
    )

    override fun onServiceConnected() {
        // Configuración básica; el meta-data xml define la mayoría de parámetros
        super.onServiceConnected()
        // Iniciar un verificador ligero que, solo cuando haya solicitud activa,
        // intente la copia del enlace sin depender de eventos.
        if (!autoCopySchedulerRunning) {
            autoCopySchedulerRunning = true
            handler.postDelayed(object : Runnable {
                override fun run() {
                    try {
                        if (TikTokAutomation.shouldAutoCopy(this@TikTokAccessibilityService)) {
                            attemptAutoCopyOnce()
                        }
                    } catch (_: Exception) {}
                    // Repetir comprobación ligera cada 800ms
                    handler.postDelayed(this, 800)
                }
            }, 800)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!TikTokAutomation.shouldAutoCopy(this)) return

        // Usar siempre la ventana activa en lugar del paquete del evento
        val root = rootInActiveWindow ?: return
        val activePkg = root.packageName?.toString() ?: ""
        if (activePkg.isNotBlank() && !tiktokPackages.contains(activePkg)) {
            // Permitir también la hoja de compartir del sistema
            return
        }

        // 1) Intentar abrir el panel de compartir
        val shareTexts = listOf(
            // Español / Inglés y variantes comunes
            "Compartir", "Share", "Send",
            // Otros idiomas frecuentes
            "Condividi", // IT
            "Partager", // FR
            "Teilen",   // DE
            "Compartilhar", // PT
            "Поделиться", // RU
            "Bagikan", // ID
            "แชร์", // TH
            "共有", // JA
            "分享", // ZH
            // En algunos dispositivos, el botón tiene contentDescription
            "share", "compartir"
        )

        val shareNode = findNodeByTextOrDesc(root, shareTexts)
        val clickedShare = performClick(shareNode)

        // Si no encontramos el botón de compartir, intentamos directamente "Copiar enlace" por si ya está abierto el sheet
        val copyTexts = listOf(
            "Copiar enlace", "Copiar vínculo", // ES
            "Copy link", "Copy", // EN
            "Copia link", // IT
            "Copier le lien", // FR
            "Link kopieren", // DE
            "Copiar link", "Copiar ligação", // PT
            "Скопировать ссылку", // RU
            "Salin tautan", // ID
            "คัดลอกลิงก์", // TH
            "リンクをコピー", // JA
            "复制链接", // ZH
            // Variantes cortas
            "Copiar", "Copiar URL", "Copy URL"
        )
        var copyNode = findNodeByTextOrDesc(root, copyTexts)

        if (!clickedShare && copyNode == null) {
            // Último intento: buscar botón con texto del icono más general
            val moreTexts = listOf("Más", "More", "•••", "⋯")
            val moreNode = findNodeByTextOrDesc(root, moreTexts)
            performClick(moreNode)
            // Tras intentar abrir, re-evaluamos el árbol (puede cambiar)
            copyNode = findNodeByTextOrDesc(rootInActiveWindow, copyTexts)
        }

        var clickedCopy = performClick(copyNode)
        if (clickedCopy) {
            TikTokAutomation.clearAutoCopy(this)
            Toast.makeText(this, "Enlace copiado automáticamente", Toast.LENGTH_SHORT).show()
        } else {
            // Fallback: intentar seleccionar nuestro objetivo de compartir directamente
            val appTexts = listOf(
                try { applicationContext.getString(com.quarlcloud.opsdonwloades.R.string.app_name) } catch (_: Exception) { "OPSDownloader" },
                "OPSDownloader",
                "OpSDonwloades"
            )
            val appShareNode = findNodeByTextOrDesc(rootInActiveWindow, appTexts)
            if (performClick(appShareNode)) {
                TikTokAutomation.clearAutoCopy(this)
                Toast.makeText(this, "Compartiendo con OPSDownloader", Toast.LENGTH_SHORT).show()
                return
            }

            // Reintentar algunos ciclos mientras se mantiene la solicitud activa
            if (retriesLeft > 0) {
                retriesLeft -= 1
                try {
                    android.os.Handler(mainLooper).postDelayed({
                        // Forzar nueva evaluación del árbol actual
                        val newRoot = rootInActiveWindow ?: return@postDelayed
                        val againShare = findNodeByTextOrDesc(newRoot, shareTexts)
                        performClick(againShare)
                        var againCopy = findNodeByTextOrDesc(rootInActiveWindow, copyTexts)
                        // Intento por ID de recurso como heurística adicional
                        if (againCopy == null) {
                            againCopy = findNodeByIdSubstring(rootInActiveWindow, listOf("copy", "link"))
                        }
                        if (performClick(againCopy)) {
                            TikTokAutomation.clearAutoCopy(this)
                            Toast.makeText(this, "Enlace copiado automáticamente", Toast.LENGTH_SHORT).show()
                        } else {
                            // Segundo fallback en reintento: seleccionar nuestro destino de compartir
                            val againAppShare = findNodeByTextOrDesc(rootInActiveWindow, appTexts)
                            if (performClick(againAppShare)) {
                                TikTokAutomation.clearAutoCopy(this)
                                Toast.makeText(this, "Compartiendo con OPSDownloader", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, 500)
                } catch (_: Exception) {}
            } else {
                TikTokAutomation.clearAutoCopy(this)
            }
        }
    }

    override fun onInterrupt() {
        // No acción necesaria
    }

    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var target: AccessibilityNodeInfo? = node
        // Asegurar que el nodo sea clickable; si no, subir al padre
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findNodeByTextOrDesc(root: AccessibilityNodeInfo?, texts: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val txt = n.text?.toString()?.trim()?.lowercase()
            val desc = n.contentDescription?.toString()?.trim()?.lowercase()
            if (txt != null && texts.any { txt.contains(it.lowercase()) }) return n
            if (desc != null && texts.any { desc.contains(it.lowercase()) }) return n
            for (i in 0 until n.childCount) {
                val child = n.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    private fun attemptAutoCopyOnce() {
        val root = rootInActiveWindow ?: return
        val activePkg = root.packageName?.toString() ?: return
        if (!tiktokPackages.contains(activePkg)) return

        val shareTexts = listOf(
            "Compartir", "Share", "Send",
            "Condividi", "Partager", "Teilen", "Compartilhar",
            "Поделиться", "Bagikan", "แชร์", "共有", "分享",
            "share", "compartir"
        )

        val copyTexts = listOf(
            "Copiar enlace", "Copiar vínculo",
            "Copy link", "Copy",
            "Copia link",
            "Copier le lien",
            "Link kopieren",
            "Copiar link", "Copiar ligação",
            "Скопировать ссылку",
            "Salin tautan",
            "คัดลอกลิงก์",
            "リンクをコピー",
            "复制链接",
            "Copiar", "Copiar URL", "Copy URL"
        )

        val shareNode = findNodeByTextOrDesc(root, shareTexts)
        performClick(shareNode)
        var copyNode = findNodeByTextOrDesc(rootInActiveWindow, copyTexts)
        if (copyNode == null) {
            copyNode = findNodeByIdSubstring(rootInActiveWindow, listOf("copy", "link"))
        }
        if (performClick(copyNode)) {
            TikTokAutomation.clearAutoCopy(this)
            Toast.makeText(this, "Enlace copiado automáticamente", Toast.LENGTH_SHORT).show()
        } else {
            // Fallback: seleccionar nuestro propio destino de compartir para iniciar descarga
            val appTexts = listOf(
                try { applicationContext.getString(com.quarlcloud.opsdonwloades.R.string.app_name) } catch (_: Exception) { "OPSDownloader" },
                "OPSDownloader",
                "OpSDonwloades"
            )
            val appShareNode = findNodeByTextOrDesc(rootInActiveWindow, appTexts)
            if (performClick(appShareNode)) {
                TikTokAutomation.clearAutoCopy(this)
                Toast.makeText(this, "Compartiendo con OPSDownloader", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
    // Búsqueda heurística por ID de recurso (requiere flagReportViewIds)
    private fun findNodeByIdSubstring(root: AccessibilityNodeInfo?, substrings: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.lowercase() ?: ""
            if (id.isNotEmpty() && substrings.any { id.contains(it) }) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }