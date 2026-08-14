package vesper.core.loader

object PspAutotestDiff {
    fun render(expected: String, actual: String, context: Int = 2): String {
        val expectedLines = expected.split('\n')
        val actualLines = actual.split('\n')
        var first = 0
        while (first < expectedLines.size && first < actualLines.size &&
            expectedLines[first] == actualLines[first]
        ) first++

        if (first == expectedLines.size && first == actualLines.size) return ""

        val lastExpected = expectedLines.lastIndex
        val lastActual = actualLines.lastIndex
        var expectedEnd = lastExpected
        var actualEnd = lastActual
        while (expectedEnd >= first && actualEnd >= first &&
            expectedLines[expectedEnd] == actualLines[actualEnd]
        ) {
            expectedEnd--
            actualEnd--
        }

        val from = (first - context).coerceAtLeast(0)
        val toExpected = (expectedEnd + context).coerceAtMost(lastExpected)
        val toActual = (actualEnd + context).coerceAtMost(lastActual)
        val lines = buildList {
            add("--- expected")
            add("+++ actual")
            add("@@ lines ${first + 1}-${expectedEnd + 1} / ${first + 1}-${actualEnd + 1} @@")
            for (line in from..maxOf(toExpected, toActual)) {
                val expectedLine = expectedLines.getOrNull(line)
                val actualLine = actualLines.getOrNull(line)
                when {
                    expectedLine == actualLine && expectedLine != null -> add("  $expectedLine")
                    expectedLine != null -> add("- $expectedLine")
                    actualLine != null -> add("+ $actualLine")
                }
            }
        }
        return lines.joinToString("\n")
    }
}
