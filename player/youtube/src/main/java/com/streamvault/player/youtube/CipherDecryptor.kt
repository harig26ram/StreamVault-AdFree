package com.streamvault.player.youtube

class CipherDecryptor {

    sealed class CipherOp {
        object Reverse : CipherOp()
        data class Splice(val index: Int) : CipherOp()
        data class SpliceDelete(val index: Int, val count: Int) : CipherOp()
        data class Slice(val index: Int) : CipherOp()
        data class Swap(val index: Int) : CipherOp()
    }

    enum class OperationStrategy {
        NAME_LOOKUP, OBJECT_LOOKUP, FALLBACK
    }

    fun decrypt(signature: String, jsCode: String, strategy: OperationStrategy = OperationStrategy.NAME_LOOKUP): String {
        if (signature.isEmpty()) return signature
        val operations = parseOperations(jsCode, strategy)
        return executeOperations(signature, operations)
    }

    fun decryptDirect(signature: String, operations: List<CipherOp>): String {
        return executeOperations(signature, operations)
    }

    fun parseOperations(jsCode: String, strategy: OperationStrategy = OperationStrategy.NAME_LOOKUP): List<CipherOp> {
        return when (strategy) {
            OperationStrategy.NAME_LOOKUP -> parseNameLookup(jsCode)
            OperationStrategy.OBJECT_LOOKUP -> parseObjectLookup(jsCode)
            OperationStrategy.FALLBACK -> parseFallback(jsCode)
        }
    }

    private fun parseNameLookup(jsCode: String): List<CipherOp> {
        val ops = mutableListOf<CipherOp>()
        var body = jsCode

        val splitMatch = Regex("""\w+\s*=\s*\w+\.split\(["']{2}\)""").find(body)
        if (splitMatch != null) {
            body = body.substring(splitMatch.range.last + 1)
        }

        val functionBody = extractFunctionBody(body)
        val statements = splitStatements(functionBody)

        for (stmt in statements) {
            val clean = stmt.trim()
            when {
                clean.matches(Regex("""\w+\.reverse\(\)""")) ->
                    ops.add(CipherOp.Reverse)
                clean.matches(Regex("""\w+\.splice\(\s*0\s*,\s*(\d+)\s*\)""")) -> {
                    Regex("""\w+\.splice\(\s*0\s*,\s*(\d+)\s*\)""").find(clean)?.let { match ->
                        ops.add(CipherOp.SpliceDelete(0, match.groupValues[1].toInt()))
                    }
                }
                clean.matches(Regex("""\w+\.splice\(\s*(\d+)\s*\)""")) -> {
                    Regex("""\w+\.splice\(\s*(\d+)\s*\)""").find(clean)?.let { match ->
                        ops.add(CipherOp.Splice(match.groupValues[1].toInt()))
                    }
                }
                clean.matches(Regex("""\w+\.slice\(\s*(\d+)\s*\)""")) -> {
                    Regex("""\w+\.slice\(\s*(\d+)\s*\)""").find(clean)?.let { match ->
                        ops.add(CipherOp.Slice(match.groupValues[1].toInt()))
                    }
                }
                clean.matches(Regex("""var\s+\w+\s*=\s*\w+\[(\d+)];\s*\w+\[\d+]=\w+\[(\d+)%\w+\.length];\s*\w+\[\2]=\w+""".replace("%", "\\%").replace(".", "\\."))) -> {
                    val match = Regex("""var\s+\w+\s*=\s*\w+\[(\d+)];\s*\w+\[\d+]=\w+\[(\d+)%\w+\.length];\s*\w+\[\2]=\w+""".replace("%", "\\%").replace(".", "\\.")).find(clean)
                    if (match != null) ops.add(CipherOp.Swap(match.groupValues[2].toInt()))
                }
            }
        }

        return ops
    }

    private fun parseObjectLookup(jsCode: String): List<CipherOp> {
        val ops = mutableListOf<CipherOp>()
        val statements = splitStatements(extractFunctionBody(jsCode))

        for (stmt in statements) {
            val clean = stmt.trim()
            when {
                clean.matches(Regex("""\w+\s*=\s*\w+\[["']reverse["']]\s*\(\s*\w+\)""")) ->
                    ops.add(CipherOp.Reverse)
                clean.matches(Regex("""\w+\s*=\s*\w+\[["']splice["']]\s*\(\s*\w+\s*,\s*(\d+)\)""")) -> {
                    Regex("""\w+\s*=\s*\w+\[["']splice["']]\s*\(\s*\w+\s*,\s*(\d+)\)""").find(clean)?.let { match ->
                        ops.add(CipherOp.SpliceDelete(0, match.groupValues[1].toInt()))
                    }
                }
                clean.matches(Regex("""\w+\s*=\s*\w+\[["']slice["']]\s*\(\s*\w+\s*,\s*(\d+)\)""")) -> {
                    Regex("""\w+\s*=\s*\w+\[["']slice["']]\s*\(\s*\w+\s*,\s*(\d+)\)""").find(clean)?.let { match ->
                        ops.add(CipherOp.Slice(match.groupValues[1].toInt()))
                    }
                }
            }
        }

        return ops
    }

    private fun parseFallback(jsCode: String): List<CipherOp> {
        val ops = mutableListOf<CipherOp>()
        val body = extractFunctionBody(jsCode).lowercase()

        if (body.contains(".reverse()")) ops.add(CipherOp.Reverse)
        if (body.contains(".splice(0,2)") || body.contains(".splice(0,2)")) ops.add(CipherOp.SpliceDelete(0, 2))
        if (body.contains(".splice(0,3)") || body.contains(".splice(0,3)")) ops.add(CipherOp.SpliceDelete(0, 3))
        if (body.matches(Regex(""".*splice\(\s*0\s*,\s*\d+.*"""))) {
            val n = Regex("""splice\(\s*0\s*,\s*(\d+)\s*\)""").find(body)
            if (n != null) {
                val existingSplice = ops.filterIsInstance<CipherOp.SpliceDelete>().any { it.count == n.groupValues[1].toInt() }
                if (!existingSplice) ops.add(CipherOp.SpliceDelete(0, n.groupValues[1].toInt()))
            }
        }

        if (body.contains(".slice(")) {
            val match = Regex("""slice\(\s*(\d+)\s*\)""").find(body)
            if (match != null) ops.add(CipherOp.Slice(match.groupValues[1].toInt()))
        }

        return ops
    }

    fun executeOperations(input: String, operations: List<CipherOp>): String {
        var chars = input.toMutableList()
        for (op in operations) {
            when (op) {
                is CipherOp.Reverse -> chars.reverse()
                is CipherOp.SpliceDelete -> {
                    if (chars.isNotEmpty() && op.index < chars.size) {
                        val end = minOf(op.index + op.count, chars.size)
                        chars = chars.subList(end, chars.size).toMutableList()
                    }
                }
                is CipherOp.Splice -> {
                    if (chars.isNotEmpty() && op.index >= 0 && op.index < chars.size) {
                        chars = chars.subList(0, op.index).toMutableList()
                    }
                }
                is CipherOp.Slice -> {
                    if (chars.isNotEmpty() && op.index >= 0 && op.index < chars.size) {
                        chars = chars.subList(op.index, chars.size).toMutableList()
                    }
                }
                is CipherOp.Swap -> {
                    if (chars.size > 1) {
                        val idx = op.index % chars.size
                        val temp = chars[0]
                        chars[0] = chars[idx]
                        chars[idx] = temp
                    }
                }
            }
        }
        return chars.joinToString("")
    }

    private fun extractFunctionBody(code: String): String {
        val bodyMatch = Regex("""function\s*\([^)]*\)\s*\{(.*)\}""", RegexOption.DOT_MATCHES_ALL).find(code)
        if (bodyMatch != null) return bodyMatch.groupValues[1]

        val assignMatch = Regex("""=\s*function\s*\([^)]*\)\s*\{(.*)\}""", RegexOption.DOT_MATCHES_ALL).find(code)
        if (assignMatch != null) return assignMatch.groupValues[1]

        val arrowMatch = Regex("""\([^)]*\)\s*=>\s*\{(.*)\}""", RegexOption.DOT_MATCHES_ALL).find(code)
        if (arrowMatch != null) return arrowMatch.groupValues[1]

        val varBody = Regex("""var\s+\w+\s*=\s*\w+\.split\(["']{2}\)[^;]*;(.*)""", RegexOption.DOT_MATCHES_ALL).find(code)
        if (varBody != null) {
            val joined = varBody.groupValues[1].trim()
            return joined.removeSuffix(";")
        }

        return code
    }

    private fun splitStatements(body: String): List<String> {
        val stmts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (ch in body) {
            when (ch) {
                '{' -> { depth++; current.append(ch) }
                '}' -> { depth--; current.append(ch) }
                ';' -> {
                    if (depth == 0) {
                        stmts.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotBlank()) {
            stmts.add(current.toString())
        }

        return stmts.filter { it.isNotBlank() }
    }

    companion object {
        val KNOWN_FALLBACK_PATTERNS: Map<String, List<CipherOp>> = mapOf(
            "reverse_splice2" to listOf(CipherOp.Reverse, CipherOp.SpliceDelete(0, 2)),
            "reverse_splice3" to listOf(CipherOp.Reverse, CipherOp.SpliceDelete(0, 3)),
            "splice2_reverse" to listOf(CipherOp.SpliceDelete(0, 2), CipherOp.Reverse),
            "splice3_reverse" to listOf(CipherOp.SpliceDelete(0, 3), CipherOp.Reverse),
            "swap_reverse_splice2" to listOf(CipherOp.Swap(18), CipherOp.Reverse, CipherOp.SpliceDelete(0, 2)),
            "reverse_swap_splice2" to listOf(CipherOp.Reverse, CipherOp.Swap(31), CipherOp.SpliceDelete(0, 2)),
            "splice_slice_reverse" to listOf(CipherOp.Splice(3), CipherOp.Slice(0), CipherOp.Reverse),
        )

        fun tryKnownPatterns(jsCode: String): List<CipherOp>? {
            val lower = jsCode.lowercase()
            return when {
                lower.contains(".reverse()") && lower.contains("splice(0,2)") -> KNOWN_FALLBACK_PATTERNS["reverse_splice2"]
                lower.contains(".reverse()") && lower.contains("splice(0,3)") -> KNOWN_FALLBACK_PATTERNS["reverse_splice3"]
                lower.contains("splice(0,2)") && lower.contains(".reverse()") -> KNOWN_FALLBACK_PATTERNS["splice2_reverse"]
                lower.contains("splice(0,3)") && lower.contains(".reverse()") -> KNOWN_FALLBACK_PATTERNS["splice3_reverse"]
                lower.contains(".reverse()") && lower.contains("splice(0,2)") && lower.contains("b[") -> KNOWN_FALLBACK_PATTERNS["swap_reverse_splice2"]
                else -> null
            }
        }
    }
}
