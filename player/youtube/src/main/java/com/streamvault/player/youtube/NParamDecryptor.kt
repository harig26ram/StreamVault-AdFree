package com.streamvault.player.youtube

class NParamDecryptor {

    data class NTransformOp(
        val swapPairs: List<Pair<Int, Int>>
    )

    fun decryptNParam(nParam: String, jsCode: String): String {
        if (nParam.isEmpty() || jsCode.isEmpty()) return nParam
        val transformOp = parseNTransformCode(jsCode) ?: return nParam
        return applyTransform(nParam, transformOp)
    }

    fun decryptNParamDirect(nParam: String, transformOp: NTransformOp): String {
        return applyTransform(nParam, transformOp)
    }

    fun parseNTransformCode(jsCode: String): NTransformOp? {
        val body = extractNTransformBody(jsCode) ?: return parseFallbackN(jsCode)

        val swapPairs = mutableListOf<Pair<Int, Int>>()

        val swapPattern = Regex(
            """var\s+\w+\s*=\s*\w+\[(\d+)];\s*\w+\[\d+]=\s*\w+\[(\d+)];\s*\w+\[\2]=\s*\w+""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in swapPattern.findAll(body)) {
            swapPairs.add(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
        }

        if (swapPairs.isEmpty()) {
            val idxPattern = Regex("""\w+\[(\d+)]""")
            val indices = idxPattern.findAll(body).map { it.groupValues[1].toInt() }.toList()
            for (i in 0 until indices.size - 1 step 2) {
                swapPairs.add(Pair(indices[i], indices[i + 1]))
            }
        }

        if (swapPairs.isEmpty()) {
            val directPairs = parseDirectSwapPairs(body)
            if (directPairs != null) return directPairs
        }

        return if (swapPairs.isNotEmpty()) NTransformOp(swapPairs) else null
    }

    private fun parseDirectSwapPairs(body: String): NTransformOp? {
        val allPattern = Regex("""\b(\d+)\b""")
        val numbers = allPattern.findAll(body).map { it.groupValues[1].toInt() }.toList()
        if (numbers.size < 2) return null

        val swapPairs = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until numbers.size - 1 step 2) {
            swapPairs.add(Pair(numbers[i], numbers[i + 1]))
        }
        return if (swapPairs.isNotEmpty()) NTransformOp(swapPairs) else null
    }

    private fun extractNTransformBody(jsCode: String): String? {
        val funcRegex = Regex(
            """function\s*\([^)]*\)\s*\{(.*?return\s+\w+\.join\([^)]*\)[^}]*)\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = funcRegex.find(jsCode)
        if (match != null) return match.groupValues[1]

        val arrowFunc = Regex(
            """\([^)]*\)\s*=>\s*\{(.*?return\s+\w+\.join\([^)]*\)[^}]*)\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val arrowMatch = arrowFunc.find(jsCode)
        if (arrowMatch != null) return arrowMatch.groupValues[1]

        val splitJoin = Regex(
            """split\(["']{2}\)[^;]*;(?:[^;]*;){1,15}\w+\.join\(["']{2}\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val sjMatch = splitJoin.find(jsCode)
        if (sjMatch != null) return sjMatch.value

        val start = jsCode.indexOf("split(\"\")")
        if (start >= 0) {
            val end = jsCode.indexOf("join(\"\")", start)
            if (end >= 0) {
                return jsCode.substring(start, end + 8)
            }
        }

        val broad = Regex(
            """split\([^)]+\)[^;]*(?:;[^;]*){1,20}join\([^)]+\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val broadMatch = broad.find(jsCode)
        if (broadMatch != null) return broadMatch.value

        return jsCode.takeIf { it.isNotBlank() }
    }

    private fun parseFallbackN(jsCode: String): NTransformOp? {
        val swapPairs = mutableListOf<Pair<Int, Int>>()
        val swapPattern = Regex(
            """\w+\[(\d+)]\s*=\s*\w+\[(\d+)]""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in swapPattern.findAll(jsCode)) {
            swapPairs.add(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
        }
        if (swapPairs.isEmpty()) {
            val allNumbers = Regex("""\b(\d+)\b""").findAll(jsCode).map { it.groupValues[1].toInt() }.toList()
            for (i in 0 until allNumbers.size - 1 step 2) {
                swapPairs.add(Pair(allNumbers[i], allNumbers[i + 1]))
            }
        }
        return if (swapPairs.isNotEmpty()) NTransformOp(swapPairs) else null
    }

    fun applyTransform(input: String, transformOp: NTransformOp): String {
        val chars = input.toMutableList()
        for ((from, to) in transformOp.swapPairs) {
            val idxA = from % chars.size
            val idxB = to % chars.size
            val temp = chars[idxA]
            chars[idxA] = chars[idxB]
            chars[idxB] = temp
        }
        return chars.joinToString("")
    }

    companion object {
        val KNOWN_ALGORITHMS: List<NTransformOp> = listOf(
            NTransformOp(listOf(
                Pair(0, 18), Pair(1, 45), Pair(2, 29), Pair(3, 56),
                Pair(4, 8), Pair(5, 37), Pair(6, 62), Pair(7, 13),
                Pair(9, 41), Pair(10, 50), Pair(11, 23), Pair(12, 3)
            )),
            NTransformOp(listOf(
                Pair(0, 3), Pair(1, 7), Pair(2, 11), Pair(4, 14),
                Pair(5, 9), Pair(6, 13), Pair(8, 15), Pair(10, 12)
            ))
        )
    }
}
