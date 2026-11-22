import Foundation

/// One of the router's 3 schedule slots. days[0] = Sunday … days[6] = Saturday.
struct ScheduleRule {
    var enabled = false
    var days: [Bool] = Array(repeating: false, count: 7)
    var sh = 0, sm = 0, eh = 0, em = 0

    func json() -> String {
        guard enabled else { return #"{"enable":0,"week":"0000000","sh":0,"sm":0,"eh":0,"em":0}"# }
        let week = days.map { $0 ? "1" : "0" }.joined()
        return #"{"enable":1,"week":""# + week + #"","sh":""# + "\(sh)" + #"","sm":""# + "\(sm)" +
            #"","eh":""# + "\(eh)" + #"","em":""# + "\(em)" + #""}"#
    }

    static func parseAll(_ arr: Any?, masterOn: Bool = true) -> [ScheduleRule] {
        let list = (arr as? [[String: Any]]) ?? []
        return (0..<3).map { i in
            guard i < list.count else { return ScheduleRule() }
            let o = list[i]
            var days = Array(repeating: false, count: 7)
            if let w = o["week"] as? [Any] {
                for d in 0..<min(7, w.count) { days[d] = "\(w[d])" == "1" }
            } else {
                let w = Array(o.str("week"))
                for d in 0..<min(7, w.count) { days[d] = w[d] == "1" }
            }
            return ScheduleRule(enabled: o.int("enable") == 1 && masterOn, days: days,
                                sh: Int(o.str("sh")) ?? 0, sm: Int(o.str("sm")) ?? 0,
                                eh: Int(o.str("eh")) ?? 0, em: Int(o.str("em")) ?? 0)
        }
    }

    static func json(_ rules: [ScheduleRule]) -> String {
        "[" + (0..<3).map { i in (i < rules.count ? rules[i] : ScheduleRule()).json() }.joined(separator: ",") + "]"
    }
}

/// Everything the router needs to save one Wi-Fi band, keeping every setting it reported.
struct BandEdit {
    var is5: Bool
    var ssid: String
    var password: String
    var auth: Int
    var radiusIp: String
    var radiusPort: String
    var radiusPwd: String
    var channel: String
    var enabled: Bool
    var hidden: Bool
    var bandwidth: String
    var dfs: String
    var twt: Bool
    var ofdma: Bool
    var rules: [ScheduleRule]

    init(raw r: [String: Any], is5: Bool) {
        let auth = Int(r.str("security")) ?? 0
        self.is5 = is5
        ssid = r.str(is5 ? "SSID5g" : "SSID2g")
        password = auth == 0 ? "" : r.str("passwd")
        self.auth = auth
        radiusIp = r.str("radiusip")
        radiusPort = r.str("radiusport")
        radiusPwd = r.str("radiuspwd")
        channel = r.str("channel").isEmpty ? "0" : r.str("channel")
        enabled = r.str("enable") != "0"
        hidden = r.str(is5 ? "hidden5g" : "hidden2g") == "1"
        bandwidth = r.str("bandwidth").isEmpty ? "0" : r.str("bandwidth")
        dfs = r.str("dfs").isEmpty ? "0" : r.str("dfs")
        twt = r.str(is5 ? "twt5g" : "twt2g") == "1"
        ofdma = r.str(is5 ? "ofdma5g" : "ofdma2g") == "1"
        rules = ScheduleRule.parseAll(r["rules"])
    }

    func setData(country: String, needRestart: Bool) -> String {
        let e = WavlinkAPI.enc
        let pwd = auth == 0 ? "Admin12345" : password   // what the web page sends for open networks
        let b: (Bool) -> String = { $0 ? "1" : "0" }
        let common = "&ssid=\(e(ssid))&password=\(e(pwd))&authmode=\(auth)" +
            "&radiusip=\(radiusIp)&radiusport=\(radiusPort)&radiuspwd=\(e(radiusPwd))&channel=\(channel)"
        let restart = needRestart ? 1 : 0
        let r = ScheduleRule.json(rules)
        if !is5 {
            return "fname=net&opt=wifi_ap&function=set\(common)&need_restart=\(restart)&enable=\(b(enabled))" +
                "&hidden2g=\(b(hidden))&bandwidth=\(bandwidth)&twt2g=\(b(twt))&ofdma2g=\(b(ofdma))&rules=\(r)"
        }
        return "fname=net&opt=wifi_ap_5g&function=set\(common)&dfs=\(dfs)&need_restart=\(restart)&enable=\(b(enabled))" +
            "&hidden5g=\(b(hidden))&bandwidth=\(bandwidth)&skiplist=\(WifiRegions.skipList5(country: country, bandwidth: bandwidth, dfs: dfs))" +
            "&twt5g=\(b(twt))&ofdma5g=\(b(ofdma))&rules=\(r)"
    }
}

/// Port of the router's wifi.js tables for the 5 GHz auto-channel skip list.
enum WifiRegions {
    static let countryRegion: [String: String] = [
        "WO": "5,7", "EU": "1,1", "US": "0,0", "CN": "1,0", "HK": "1,0", "TW": "0,3",
        "JP": "5,9", "AU": "0,0", "CA": "0,0", "RU": "0,11", "IN": "0,0", "SG": "0,0", "ZA": "0,12",
    ]
    static let bandWidthList: [String: Int] = [
        "WO": 2, "EU": 2, "US": 0, "CN": 2, "TW": 0, "JP": 0, "AU": 1, "CA": 1,
        "RU": 2, "IN": 2, "SG": 1, "ZA": 2,
    ]
    static let region5 = "36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140,144,149,153,157,161,165"
    static let highBandWidth5 = [
        "36 40 44 48 52 56 60 64 100 104 108 112 116 120 124 128",
        "36 40 44 48 52 56 60 64 100 104 108 112",
        "36 40 44 48 52 56 60 64",
    ]
    static let skip: [[Int: String]] = [
        [
            0: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            1: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            2: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            3: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            7: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            9: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            10: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            11: "100;104;108;112;116;120;124;128;144;165;",
            12: "36;40;44;48;52;56;60;64;144;149;153;157;161;165;",
        ],
        [
            0: "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            1: "144;149;153;157;161;165;",
            2: "100;104;108;112;116;120;124;128;132;136;140;144;149;153;157;161;165;",
            3: "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            7: "100;104;108;112;116;120;124;128;132;136;140;144;165;",
            9: "120;124;128;144;149;153;157;161;165;",
            10: "52;56;60;64;100;104;108;112;116;120;124;128;132;136;140;144;165;",
            11: "100;104;108;112;116;120;124;128;144;165;",
            12: "144;149;153;157;161;165;",
        ],
    ]

    static func skipList5(country: String, bandwidth: String, dfs: String) -> String {
        switch bandwidth {
        case "1":
            guard let idx = bandWidthList[country], idx < highBandWidth5.count else { return "" }
            let high = highBandWidth5[idx].replacingOccurrences(of: " ", with: ",")
            var s = region5.components(separatedBy: high).joined()
            if s.hasPrefix(",") { s.removeFirst() }
            return s.replacingOccurrences(of: ",", with: ";") + ";"
        case "0":
            guard let parts = countryRegion[country]?.split(separator: ","), parts.count > 1,
                  let region = Int(parts[1]) else { return "" }
            return skip[dfs == "0" ? 0 : 1][region] ?? ""
        default:
            return ""
        }
    }
}

/// One-tap switches (guest Wi-Fi, kids' Wi-Fi), sending the same request as their settings pages.
enum Quick {
    static func guestData(_ d: [String: Any], on: Bool) -> String? {
        let mode = d.str("guest_mode").isEmpty ? "0" : d.str("guest_mode")
        let pwd = d.str("passwd")
        if on && (d.str("SSID").trimmingCharacters(in: .whitespaces).isEmpty || (mode == "0" && wifiPassError(pwd) != nil)) { return nil }
        let e = WavlinkAPI.enc
        let rules = ScheduleRule.parseAll(d["rules"], masterOn: on)
        let iso = d.str("ap_isolation").isEmpty ? "1" : d.str("ap_isolation")
        let td = d.str("touch_duration_time").isEmpty ? "0" : d.str("touch_duration_time")
        let te = d.str("touch_expiration_time").isEmpty ? "0" : d.str("touch_expiration_time")
        return "&need_restart=1&enable=\(on ? 1 : 0)&guestMode=\(mode)" +
            "&ssid=\(e(d.str("SSID")))&password=\(e(pwd))&radiusip=\(d.str("radiusip"))&radiusport=\(d.str("radiusport"))" +
            "&radiuspwd=\(e(d.str("radiuspwd")))&apIsolation=\(iso)&touchDuration=\(td)&touchExpiration=\(te)" +
            "&rules=\(ScheduleRule.json(rules))"
    }

    static func kidsData(_ d: [String: Any], on: Bool) -> String? {
        let rules = ScheduleRule.parseAll(d["rules"], masterOn: on)
        let pwd = d.str("passwd")
        if on && (wifiPassError(pwd) != nil || !rules.contains(where: { $0.enabled }) || d.str("SSID").trimmingCharacters(in: .whitespaces).isEmpty) { return nil }
        let auth = d.str("security") == "2" ? "2" : "1"
        let e = WavlinkAPI.enc
        return "&wifi_type=_child&enable=\(on ? 1 : 0)&ssid=\(e(d.str("SSID")))" +
            "&password=\(e(pwd))&authmode=\(auth)&rules=\(ScheduleRule.json(rules))"
    }
}
