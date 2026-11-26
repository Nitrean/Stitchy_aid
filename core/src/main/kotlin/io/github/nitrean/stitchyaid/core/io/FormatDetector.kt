package io.github.nitrean.stitchyaid.core.io

/** The kinds of file the Import button can be given. */
enum class FileKind(val friendlyName: String) {
    STITCHY_PROJECT("Stitchy Aid project"),
    OXS("Open Cross Stitch chart"),
    PDF("PDF pattern"),
    IMAGE("picture"),
    PATTERN_MAKER("Pattern Maker chart"),
    PCSTITCH("PCStitch chart"),
    UNKNOWN("file"),
}

/**
 * Works out what a file is from its contents, so people never have to pick a
 * file type. The file name is only a fallback for formats without a clear
 * signature.
 */
object FormatDetector {
    fun detect(bytes: ByteArray, fileName: String? = null): FileKind {
        if (bytes.isEmpty()) return FileKind.UNKNOWN
        if (startsWith(bytes, "%PDF".toByteArray())) return FileKind.PDF
        if (startsWith(bytes, byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))) return FileKind.IMAGE
        if (startsWith(bytes, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) return FileKind.IMAGE
        if (startsWith(bytes, "GIF8".toByteArray())) return FileKind.IMAGE
        if (startsWith(bytes, "BM".toByteArray()) && fileName?.endsWith(".bmp", true) == true) return FileKind.IMAGE
        if (bytes.size > 12 && startsWith(bytes, "RIFF".toByteArray()) && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return FileKind.IMAGE
        if (bytes.size > 12 && String(bytes, 4, 8, Charsets.US_ASCII).let { it == "ftypheic" || it == "ftypheix" || it == "ftypmif1" || it == "ftypavif" }) return FileKind.IMAGE

        val head = textHead(bytes)
        if (head != null) {
            val trimmed = head.trimStart('﻿', ' ', '\n', '\r', '\t')
            if (trimmed.startsWith("{") && head.contains("\"stitchyFormat\"")) return FileKind.STITCHY_PROJECT
            if (trimmed.startsWith("<") && Regex("<chart[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(head)) return FileKind.OXS
        }

        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "oxs", "xml" -> FileKind.OXS
            "xsd" -> FileKind.PATTERN_MAKER
            "pat" -> FileKind.PCSTITCH
            "stitchy" -> FileKind.STITCHY_PROJECT
            else -> FileKind.UNKNOWN
        }
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    /** The first few KB as text, or null if it looks binary. */
    private fun textHead(bytes: ByteArray): String? {
        val n = minOf(bytes.size, 4096)
        var control = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return null
            if (b < 0x09 || (b in 0x0E..0x1F)) control++
        }
        if (control > n / 20) return null
        return String(bytes, 0, n, Charsets.UTF_8)
    }
}
