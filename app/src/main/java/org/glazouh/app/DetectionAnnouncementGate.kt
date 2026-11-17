package org.glazouh.app

/**
 * Prevents live narration from repeating the same category on every camera frame.
 * A category must be seen in several consecutive analyzed frames. It is re-armed
 * only after it has disappeared for a few analyzed frames.
 */
internal class DetectionAnnouncementGate(
    private val minConsecutiveFrames: Int = 3,
    private val minConfidence: Float = 0.55f,
    private val minIntervalMs: Long = 2_500L,
    private val absentFramesToReset: Int = 5
) {
    private val consecutiveFrames = mutableMapOf<String, Int>()
    private val absentFrames = mutableMapOf<String, Int>()
    private val announced = mutableSetOf<String>()
    private var lastAnnouncementAt = Long.MIN_VALUE

    fun update(detections: List<Pair<String, Float>>, nowMs: Long): List<String> {
        val currentScores = detections
            .filter { it.first.isNotBlank() && it.second >= minConfidence }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.maxOf { it.second } }

        val knownLabels = (consecutiveFrames.keys + currentScores.keys + absentFrames.keys).toSet()
        knownLabels.forEach { label ->
            if (label in currentScores) {
                consecutiveFrames[label] = (consecutiveFrames[label] ?: 0) + 1
                absentFrames[label] = 0
            } else {
                consecutiveFrames[label] = 0
                val absent = (absentFrames[label] ?: 0) + 1
                absentFrames[label] = absent
                if (absent >= absentFramesToReset) {
                    announced.remove(label)
                    consecutiveFrames.remove(label)
                    absentFrames.remove(label)
                }
            }
        }

        if (lastAnnouncementAt != Long.MIN_VALUE && nowMs - lastAnnouncementAt < minIntervalMs) {
            return emptyList()
        }

        val ready = currentScores.entries
            .filter { (label, _) ->
                (consecutiveFrames[label] ?: 0) >= minConsecutiveFrames && label !in announced
            }
            .sortedByDescending { it.value }
            .take(MAX_LABELS_PER_ANNOUNCEMENT)
            .map { it.key }

        if (ready.isEmpty()) return emptyList()

        announced.addAll(ready)
        lastAnnouncementAt = nowMs
        return ready
    }

    fun reset() {
        consecutiveFrames.clear()
        absentFrames.clear()
        announced.clear()
        lastAnnouncementAt = Long.MIN_VALUE
    }

    private companion object {
        const val MAX_LABELS_PER_ANNOUNCEMENT = 3
    }
}
