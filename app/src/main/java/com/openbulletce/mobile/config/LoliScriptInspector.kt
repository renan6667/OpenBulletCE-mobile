package com.openbulletce.mobile.config

enum class LoliLineKind {
    BLOCK,
    COMMAND,
    FLOW,
    SCRIPT_DIRECTIVE,
    COMMENT,
    EMPTY,
    UNKNOWN
}

data class LoliLineInfo(
    val lineNumber: Int,
    val kind: LoliLineKind,
    val identifier: String,
    val disabled: Boolean,
    val original: String
)

data class LoliScriptReport(
    val lines: List<LoliLineInfo>
) {
    val blockCounts: Map<String, Int>
        get() = lines
            .filter { it.kind == LoliLineKind.BLOCK }
            .groupingBy { it.identifier }
            .eachCount()
            .toSortedMap()

    val unknownLines: List<LoliLineInfo>
        get() = lines.filter { it.kind == LoliLineKind.UNKNOWN }

    val embeddedScriptPresent: Boolean
        get() = lines.any { it.kind == LoliLineKind.SCRIPT_DIRECTIVE }
}

/**
 * Read-only LoliScript inspector based on the identifiers exposed by the
 * upstream Cookie Edition BlockParser and CommandParser.
 *
 * This class never executes imported script text.
 */
object LoliScriptInspector {
    private val blocks = setOf(
        "BYPASSCF",
        "SOLVECAPTCHA",
        "REPORTCAPTCHA",
        "CAPTCHA",
        "FUNCTION",
        "KEYCHECK",
        "PARSE",
        "RECAPTCHA",
        "REQUEST",
        "COOKIECONTAINER",
        "TCP",
        "UTILITY",
        "BROWSERACTION",
        "ELEMENTACTION",
        "EXECUTEJS",
        "NAVIGATE"
    )

    private val commands = setOf(
        "PRINT",
        "SET",
        "DELETE",
        "MOUSEACTION"
    )

    private val flow = setOf(
        "IF",
        "ELSE",
        "ENDIF",
        "WHILE",
        "ENDWHILE",
        "JUMP",
        "END"
    )

    fun inspect(script: String): LoliScriptReport {
        var inEmbeddedScript = false

        val lines = script
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .mapIndexed { index, source ->
                val original = source
                var text = source.trim()

                if (text.isBlank()) {
                    return@mapIndexed LoliLineInfo(
                        index + 1, LoliLineKind.EMPTY, "", false, original
                    )
                }

                var disabled = false
                if (text.startsWith("!")) {
                    disabled = true
                    text = text.drop(1).trimStart()
                }

                if (inEmbeddedScript) {
                    val isEnd = text.startsWith("END SCRIPT", ignoreCase = true)
                    if (isEnd) inEmbeddedScript = false
                    return@mapIndexed LoliLineInfo(
                        index + 1,
                        LoliLineKind.SCRIPT_DIRECTIVE,
                        if (isEnd) "END SCRIPT" else "SCRIPT BODY",
                        disabled,
                        original
                    )
                }

                if (text.startsWith("BEGIN SCRIPT", ignoreCase = true)) {
                    inEmbeddedScript = true
                    return@mapIndexed LoliLineInfo(
                        index + 1,
                        LoliLineKind.SCRIPT_DIRECTIVE,
                        "BEGIN SCRIPT",
                        disabled,
                        original
                    )
                }

                if (text.startsWith("##")) {
                    return@mapIndexed LoliLineInfo(
                        index + 1, LoliLineKind.COMMENT, "", disabled, original
                    )
                }

                // Upstream parsers permit an optional #label before blocks/commands.
                if (text.startsWith("#")) {
                    val firstSpace = text.indexOf(' ')
                    if (firstSpace < 0) {
                        return@mapIndexed LoliLineInfo(
                            index + 1, LoliLineKind.UNKNOWN, "", disabled, original
                        )
                    }
                    text = text.substring(firstSpace + 1).trimStart()
                }

                val token = firstToken(text)

                when {
                    token in blocks -> LoliLineInfo(
                        index + 1, LoliLineKind.BLOCK, token, disabled, original
                    )
                    token in commands -> LoliLineInfo(
                        index + 1, LoliLineKind.COMMAND, token, disabled, original
                    )
                    token in flow -> LoliLineInfo(
                        index + 1, LoliLineKind.FLOW, token, disabled, original
                    )
                    else -> LoliLineInfo(
                        index + 1, LoliLineKind.UNKNOWN, token, disabled, original
                    )
                }
            }

        return LoliScriptReport(lines)
    }

    private fun firstToken(text: String): String =
        text.trimStart()
            .substringBefore(' ')
            .uppercase()
}
