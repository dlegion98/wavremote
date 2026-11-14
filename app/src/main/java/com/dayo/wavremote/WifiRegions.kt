package com.dayo.wavremote

/**
 * Port of the router's /js/static/wifi.js tables, used to work out the 5 GHz auto-channel
 * "skip list" the same way the web UI does when Wi-Fi settings are saved.
 */
object WifiRegions {
    private val countryRegion = mapOf(
        "WO" to "5,7", "EU" to "1,1", "US" to "0,0", "CN" to "1,0", "HK" to "1,0", "TW" to "0,3",
        "JP" to "5,9", "AU" to "0,0", "CA" to "0,0", "RU" to "0,11", "IN" to "0,0", "SG" to "0,0",
        "ZA" to "0,12",
    )

    private val bandWidthList = mapOf(
        "WO" to 2, "EU" to 2, "US" to 0, "CN" to 2, "TW" to 0, "JP" to 0, "AU" to 1, "CA" to 1,
        "RU" to 2, "IN" to 2, "SG" to 1, "ZA" to 2,
    )

    private const val REGION5 =
        "36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140,144,149,153,157,161,165"

    private val highBandWidth5 = listOf(
        "36 40 44 48 52 56 60 64 100 104 108 112 116 120 124 128",
        "36 40 44 48 52 56 60 64 100 104 108 112",
        "36 40 44 48 52 56 60 64",
    )

    // [DFS off / on][region]
    private val skipList5 = listOf(
        mapOf(
            0 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            1 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            2 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            3 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            7 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            9 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            10 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            11 to "100;104;108;112;116;120;124;128;144;165;",
            12 to "36;40;44;48;52;56;60;64;144;149;153;157;161;165;",
        ),
        mapOf(
            0 to "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            1 to "144;149;153;157;161;165;",
            2 to "100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            3 to "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            7 to "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            9 to "120;124;128;144;149;153;157;161;165;",
            10 to "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            11 to "100;104;108;112;116;120;124;128;144;165;",
            12 to "144;149;153;157;161;165;",
        ),
    )

    fun skipList5(country: String, bandwidth: String, dfs: String): String = when (bandwidth) {
        "1" -> {
            val idx = bandWidthList[country]
            val high = idx?.let { highBandWidth5.getOrNull(it) }?.replace(" ", ",")
            if (high == null) "" else {
                var s = REGION5.split(high).joinToString("")
                if (s.startsWith(",")) s = s.substring(1)
                s.replace(",", ";") + ";"
            }
        }
        "0" -> {
            val region = countryRegion[country]?.split(",")?.getOrNull(1)?.toIntOrNull()
            if (region == null) "" else skipList5[if (dfs == "0") 0 else 1][region] ?: ""
        }
        else -> ""
    }

    val channels2: List<String> = (1..11).map { it.toString() }

    /** Same list the web UI offers for 5 GHz (showChannel5G in wifi.js). */
    fun channels5(country: String, dfs: String, bandwidth: String): List<String> {
        if (bandwidth == "1") {
            val idx = bandWidthList[country] ?: return emptyList()
            return highBandWidth5.getOrNull(idx)?.split(" ") ?: emptyList()
        }
        val region = countryRegion[country]?.split(",")?.getOrNull(1)?.toIntOrNull()
        val skip = region?.let { skipList5[if (dfs == "0") 0 else 1][it] }?.split(";")?.toSet() ?: emptySet()
        return REGION5.split(",").filter { it !in skip }
    }
}
