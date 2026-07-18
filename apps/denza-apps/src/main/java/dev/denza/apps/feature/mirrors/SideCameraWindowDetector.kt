package dev.denza.apps.feature.mirrors

object SideCameraWindowDetector {
    private val blockStart = Regex("\\n(?=  Window #[0-9]+ Window\\{)")

    fun isLeftVisible(windows: String, clusterDisplayId: Int): Boolean =
        blockStart.split(windows).any { block ->
            block.contains("com.byd.avc/com.byd.avc.PIP2MeterActivity") &&
                block.contains("mDisplayId=$clusterDisplayId") &&
                block.contains("package=com.byd.avc")
        }

    fun isRightVisible(windows: String): Boolean = blockStart.split(windows).any { block ->
        block.contains("com.byd.avc") &&
            block.contains("mDisplayId=0") &&
            block.contains("package=com.byd.avc") &&
            block.contains("ty=SYSTEM_ALERT") &&
            block.contains("(720x450)")
    }
}
