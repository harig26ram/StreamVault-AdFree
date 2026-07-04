package com.streamvault.player.youtube

object ItagInfo {

    data class ItagProperties(
        val itag: Int,
        val container: String,
        val resolution: String?,
        val videoCodec: String?,
        val audioCodec: String?,
        val audioBitrate: Int?,
        val channels: Int?,
        val isDash: Boolean,
        val isHdr: Boolean,
        val is3d: Boolean
    )

    private val itagMap: Map<Int, ItagProperties> = listOf(
        ItagProperties(5, "flv", "240p", "h264", "mp3", 64, 1, false, false, false),
        ItagProperties(6, "flv", "270p", "h264", "mp3", 64, 1, false, false, false),
        ItagProperties(13, "3gp", "144p", "mp4v", "aac", 24, 1, false, false, false),
        ItagProperties(17, "3gp", "144p", "mp4v", "aac", 24, 1, false, false, false),
        ItagProperties(18, "mp4", "360p", "h264", "aac", 96, 2, false, false, false),
        ItagProperties(22, "mp4", "720p", "h264", "aac", 192, 2, false, false, false),
        ItagProperties(34, "flv", "360p", "h264", "aac", 128, 2, false, false, false),
        ItagProperties(35, "flv", "480p", "h264", "aac", 128, 2, false, false, false),
        ItagProperties(36, "3gp", "240p", "mp4v", "aac", 32, 1, false, false, false),
        ItagProperties(37, "mp4", "1080p", "h264", "aac", 192, 2, false, false, false),
        ItagProperties(38, "mp4", "3072p", "h264", "aac", 192, 2, false, false, false),
        ItagProperties(43, "webm", "360p", "vp8", "vorbis", 128, 2, false, false, false),
        ItagProperties(44, "webm", "480p", "vp8", "vorbis", 128, 2, false, false, false),
        ItagProperties(45, "webm", "720p", "vp8", "vorbis", 192, 2, false, false, false),
        ItagProperties(46, "webm", "1080p", "vp8", "vorbis", 192, 2, false, false, false),
        ItagProperties(59, "mp4", "480p", "h264", "aac", 128, 2, false, false, false),
        ItagProperties(78, "mp4", "480p", "h264", "aac", 128, 2, false, false, false),
        ItagProperties(82, "mp4", "360p", "h264", "aac", 128, 2, false, false, true),
        ItagProperties(83, "mp4", "480p", "h264", "aac", 128, 2, false, false, true),
        ItagProperties(84, "mp4", "720p", "h264", "aac", 192, 2, false, false, true),
        ItagProperties(85, "mp4", "1080p", "h264", "aac", 192, 2, false, false, true),
        ItagProperties(100, "webm", "360p", "vp8", "vorbis", 128, 2, false, false, true),
        ItagProperties(101, "webm", "480p", "vp8", "vorbis", 192, 2, false, false, true),
        ItagProperties(102, "webm", "720p", "vp8", "vorbis", 192, 2, false, false, true),
        ItagProperties(133, "mp4", "240p", "h264", null, null, null, true, false, false),
        ItagProperties(134, "mp4", "360p", "h264", null, null, null, true, false, false),
        ItagProperties(135, "mp4", "480p", "h264", null, null, null, true, false, false),
        ItagProperties(136, "mp4", "720p", "h264", null, null, null, true, false, false),
        ItagProperties(137, "mp4", "1080p", "h264", null, null, null, true, false, false),
        ItagProperties(138, "mp4", "2160p", "h264", null, null, null, true, false, false),
        ItagProperties(160, "mp4", "144p", "h264", null, null, null, true, false, false),
        ItagProperties(212, "mp4", "480p", "h264", null, null, null, true, false, false),
        ItagProperties(213, "mp4", "480p", "h264", null, null, null, true, false, false),
        ItagProperties(214, "mp4", "720p", "h264", null, null, null, true, false, false),
        ItagProperties(215, "mp4", "720p", "h264", null, null, null, true, false, false),
        ItagProperties(216, "mp4", "1080p", "h264", null, null, null, true, false, false),
        ItagProperties(217, "mp4", "1080p", "h264", null, null, null, true, false, false),
        ItagProperties(264, "mp4", "1440p", "h264", null, null, null, true, false, false),
        ItagProperties(266, "mp4", "2160p", "h264", null, null, null, true, false, false),
        ItagProperties(298, "mp4", "720p60", "h264", null, null, null, true, false, false),
        ItagProperties(299, "mp4", "1080p60", "h264", null, null, null, true, false, false),
        ItagProperties(302, "webm", "720p60", "vp9", null, null, null, true, false, false),
        ItagProperties(303, "webm", "1080p60", "vp9", null, null, null, true, false, false),
        ItagProperties(308, "webm", "1440p60", "vp9", null, null, null, true, false, false),
        ItagProperties(313, "webm", "2160p", "vp9", null, null, null, true, false, false),
        ItagProperties(315, "webm", "2160p60", "vp9", null, null, null, true, false, false),
        ItagProperties(330, "webm", "144p60", "vp9", null, null, null, true, false, false),
        ItagProperties(331, "webm", "240p60", "vp9", null, null, null, true, false, false),
        ItagProperties(332, "webm", "360p60", "vp9", null, null, null, true, false, false),
        ItagProperties(333, "webm", "480p60", "vp9", null, null, null, true, false, false),
        ItagProperties(334, "webm", "720p60", "vp9", null, null, null, true, false, false),
        ItagProperties(335, "webm", "1080p60", "vp9", null, null, null, true, false, false),
        ItagProperties(336, "webm", "1440p60", "vp9", null, null, null, true, false, false),
        ItagProperties(337, "webm", "2160p60", "vp9", null, null, null, true, false, false),
        ItagProperties(394, "mp4", "144p60", "av1", null, null, null, true, false, false),
        ItagProperties(395, "mp4", "240p60", "av1", null, null, null, true, false, false),
        ItagProperties(396, "mp4", "360p60", "av1", null, null, null, true, false, false),
        ItagProperties(397, "mp4", "480p60", "av1", null, null, null, true, false, false),
        ItagProperties(398, "mp4", "720p60", "av1", null, null, null, true, false, false),
        ItagProperties(399, "mp4", "1080p60", "av1", null, null, null, true, false, false),
        ItagProperties(400, "mp4", "1440p60", "av1", null, null, null, true, false, false),
        ItagProperties(401, "mp4", "2160p60", "av1", null, null, null, true, false, false),
        ItagProperties(402, "mp4", "4320p60", "av1", null, null, null, true, false, false),
        ItagProperties(571, "mp4", "2160p60", "av1", null, null, null, true, false, false),
        ItagProperties(140, "m4a", null, null, "aac", 128, 2, true, false, false),
        ItagProperties(141, "m4a", null, null, "aac", 256, 2, true, false, false),
        ItagProperties(171, "webm", null, null, "vorbis", 128, 2, true, false, false),
        ItagProperties(172, "webm", null, null, "vorbis", 192, 2, true, false, false),
        ItagProperties(249, "webm", null, null, "opus", 50, 2, true, false, false),
        ItagProperties(250, "webm", null, null, "opus", 70, 2, true, false, false),
        ItagProperties(251, "webm", null, null, "opus", 160, 2, true, false, false),
        ItagProperties(256, "m4a", null, null, "aac", 197, 2, true, false, false),
        ItagProperties(258, "m4a", null, null, "aac", 383, 2, true, false, false),
        ItagProperties(325, "m4a", null, null, "aac", 48, 1, true, false, false),
        ItagProperties(328, "m4a", null, null, "aac", 96, 2, true, false, false),
        ItagProperties(599, "m4a", null, null, "aac", 32, 1, true, false, false),
        ItagProperties(600, "webm", null, null, "opus", 32, 1, true, false, false),
    ).associateBy { it.itag }

    fun get(itag: Int): ItagProperties? = itagMap[itag]

    fun getVideoItagsSorted(): List<ItagProperties> = itagMap.values
        .filter { it.videoCodec != null && it.resolution != null }
        .sortedByDescending { parseResolution(it.resolution) }

    fun getAudioItagsSorted(): List<ItagProperties> = itagMap.values
        .filter { it.audioCodec != null }
        .sortedByDescending { it.audioBitrate ?: 0 }

    private fun parseResolution(resolution: String?): Int {
        if (resolution == null) return 0
        val num = resolution.replace(Regex("[^0-9]"), "")
        val r = num.toIntOrNull() ?: return 0
        return if (resolution.contains("60")) r + 10000 else r
    }
}
