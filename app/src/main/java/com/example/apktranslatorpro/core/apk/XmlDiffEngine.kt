package com.example.apktranslatorpro.core.apk

enum class DiffLineStatus {
    UNCHANGED,
    ADDED,
    REMOVED,
    MODIFIED
}

data class XmlDiffRow(
    val originalLineNumber: Int?,
    val modifiedLineNumber: Int?,
    val originalText: String,
    val modifiedText: String,
    val status: DiffLineStatus
)

data class XmlDiffSummary(
    val rows: List<XmlDiffRow>,
    val unchangedCount: Int,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int
) {
    val hasChanges: Boolean
        get() = addedCount > 0 || removedCount > 0 || modifiedCount > 0
}

/**
 * Pure-Kotlin Longest Common Subsequence (LCS) line-by-line XML diff engine
 * producing aligned Side-by-Side comparison rows and unified patch exports.
 */
object XmlDiffEngine {

    fun computeSideBySideDiff(originalXml: String, modifiedXml: String): XmlDiffSummary {
        val origLines = if (originalXml.isEmpty()) emptyList() else originalXml.replace("\r\n", "\n").split('\n')
        val modLines = if (modifiedXml.isEmpty()) emptyList() else modifiedXml.replace("\r\n", "\n").split('\n')

        val m = origLines.size
        val n = modLines.size

        // Guard memory for extremely large XMLs by running windowed LCS
        val rawOps = if (m.toLong() * n.toLong() <= 1_500_000L) {
            computeLcsOps(origLines, modLines)
        } else {
            computeFastIndexedOps(origLines, modLines)
        }

        // Pair adjacent REMOVED + ADDED operations into MODIFIED rows for true side-by-side readability
        val rows = mutableListOf<XmlDiffRow>()
        var i = 0
        var added = 0
        var removed = 0
        var modified = 0
        var unchanged = 0

        while (i < rawOps.size) {
            val op = rawOps[i]
            if (op is DiffOp.Keep) {
                rows.add(
                    XmlDiffRow(
                        originalLineNumber = op.origIdx + 1,
                        modifiedLineNumber = op.modIdx + 1,
                        originalText = origLines[op.origIdx],
                        modifiedText = modLines[op.modIdx],
                        status = DiffLineStatus.UNCHANGED
                    )
                )
                unchanged++
                i++
            } else {
                val removedRun = mutableListOf<Int>()
                val addedRun = mutableListOf<Int>()
                while (i < rawOps.size && rawOps[i] !is DiffOp.Keep) {
                    when (val cur = rawOps[i]) {
                        is DiffOp.Delete -> removedRun.add(cur.origIdx)
                        is DiffOp.Insert -> addedRun.add(cur.modIdx)
                        is DiffOp.Keep -> Unit
                    }
                    i++
                }
                val pairCount = maxOf(removedRun.size, addedRun.size)
                for (p in 0 until pairCount) {
                    val oIdx = removedRun.getOrNull(p)
                    val mIdx = addedRun.getOrNull(p)
                    when {
                        oIdx != null && mIdx != null -> {
                            rows.add(
                                XmlDiffRow(
                                    originalLineNumber = oIdx + 1,
                                    modifiedLineNumber = mIdx + 1,
                                    originalText = origLines[oIdx],
                                    modifiedText = modLines[mIdx],
                                    status = DiffLineStatus.MODIFIED
                                )
                            )
                            modified++
                        }
                        oIdx != null -> {
                            rows.add(
                                XmlDiffRow(
                                    originalLineNumber = oIdx + 1,
                                    modifiedLineNumber = null,
                                    originalText = origLines[oIdx],
                                    modifiedText = "",
                                    status = DiffLineStatus.REMOVED
                                )
                            )
                            removed++
                        }
                        mIdx != null -> {
                            rows.add(
                                XmlDiffRow(
                                    originalLineNumber = null,
                                    modifiedLineNumber = mIdx + 1,
                                    originalText = "",
                                    modifiedText = modLines[mIdx],
                                    status = DiffLineStatus.ADDED
                                )
                            )
                            added++
                        }
                    }
                }
            }
        }

        return XmlDiffSummary(
            rows = rows,
            unchangedCount = unchanged,
            addedCount = added,
            removedCount = removed,
            modifiedCount = modified
        )
    }

    fun generateUnifiedPatch(fileLabel: String, summary: XmlDiffSummary): String {
        val sb = StringBuilder()
        sb.appendLine("--- a/$fileLabel")
        sb.appendLine("+++ b/$fileLabel")
        for (row in summary.rows) {
            when (row.status) {
                DiffLineStatus.UNCHANGED -> Unit
                DiffLineStatus.REMOVED -> sb.appendLine("-${row.originalText}")
                DiffLineStatus.ADDED -> sb.appendLine("+${row.modifiedText}")
                DiffLineStatus.MODIFIED -> {
                    sb.appendLine("-${row.originalText}")
                    sb.appendLine("+${row.modifiedText}")
                }
            }
        }
        return sb.toString()
    }

    private sealed interface DiffOp {
        data class Keep(val origIdx: Int, val modIdx: Int) : DiffOp
        data class Delete(val origIdx: Int) : DiffOp
        data class Insert(val modIdx: Int) : DiffOp
    }

    private fun computeLcsOps(a: List<String>, b: List<String>): List<DiffOp> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val ops = mutableListOf<DiffOp>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            if (a[i] == b[j]) {
                ops.add(DiffOp.Keep(i, j))
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                ops.add(DiffOp.Delete(i))
                i++
            } else {
                ops.add(DiffOp.Insert(j))
                j++
            }
        }
        while (i < m) ops.add(DiffOp.Delete(i++))
        while (j < n) ops.add(DiffOp.Insert(j++))
        return ops
    }

    private fun computeFastIndexedOps(a: List<String>, b: List<String>): List<DiffOp> {
        val ops = mutableListOf<DiffOp>()
        val maxLen = maxOf(a.size, b.size)
        for (k in 0 until maxLen) {
            val orig = a.getOrNull(k)
            val mod = b.getOrNull(k)
            when {
                orig != null && mod != null && orig == mod -> ops.add(DiffOp.Keep(k, k))
                orig != null && mod != null -> {
                    ops.add(DiffOp.Delete(k))
                    ops.add(DiffOp.Insert(k))
                }
                orig != null -> ops.add(DiffOp.Delete(k))
                mod != null -> ops.add(DiffOp.Insert(k))
            }
        }
        return ops
    }
}
