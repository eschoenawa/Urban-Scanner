package de.eschoenawa.urbanscanner.postprocessing

import android.content.Context
import androidx.annotation.StringRes
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.repository.ScanRepository
import kotlinx.coroutines.flow.Flow

interface PostProcessor {
    @StringRes
    fun getName(): Int
    fun process(context: Context, scan: Scan, scanRepository: ScanRepository): Flow<Progress>
    fun getConfig(): List<PostProcessingConfig>
    fun configure(configValues: Map<PostProcessingConfig, String>)
}

data class Progress(
    val infoText: String,
    val progress: Int
)

interface PostProcessingConfig {
    val required: Boolean

    @get:StringRes
    val name: Int
    fun validateValue(value: String): Boolean
}

object PostProcessorRegistry {
    val postProcessorInfos = PostProcessorInfo::class.sealedSubclasses
        .map { it.objectInstance as PostProcessorInfo }
    private val postProcessors = emptyMap<String, () -> PostProcessor>().toMutableMap()

    init {
        postProcessorInfos.forEach { info ->
            postProcessors[info.identifier] = info.factory
        }
    }

    fun getProcessor(name: String): PostProcessor? {
        return postProcessors[name]?.invoke()
    }
}

sealed class PostProcessorInfo {
    abstract val identifier: String

    @get:StringRes
    abstract val name: Int
    abstract val factory: () -> PostProcessor
    abstract fun isApplicable(scan: Scan): Boolean
}

object UtmPostProcessorInfo : PostProcessorInfo() {
    override val identifier = "utm"
    override val name = R.string.utm_post_processor
    override val factory = ::UtmPostProcessor
    override fun isApplicable(scan: Scan): Boolean {
        return scan.isGeoReferenced && scan.pointCount > 0
    }
}

object KmlCameraPathPostProcessorInfo : PostProcessorInfo() {
    override val identifier = "kml_cam"
    override val name = R.string.kml_cam_path_post_processor
    override val factory = ::KmlCameraPathPostProcessor
    override fun isApplicable(scan: Scan): Boolean {
        return scan.isGeoReferenced && scan.pointCount > 0
    }

}
