package com.hakimi.aviation.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AirportCityUtil {

    // 三字码 -> 城市名
    private static final Map<String, String> AIRPORT_CITY_MAP;

    // 主键 -> 航线 (出发三字码, 到达三字码)
    private static final Map<Integer, String[]> ROUTE_MAP;

    static {
        Map<String, String> cityMap = new HashMap<>();
        cityMap.put("PEK", "北京");
        cityMap.put("CAN", "广州");
        cityMap.put("CTU", "成都");
        cityMap.put("SHA", "上海");
        cityMap.put("HRB", "哈尔滨");
        cityMap.put("DLC", "大连");
        cityMap.put("SHE", "沈阳");
        cityMap.put("SJW", "石家庄");
        cityMap.put("SZX", "深圳");
        cityMap.put("HAK", "海口");
        cityMap.put("NNG", "南宁");
        cityMap.put("XMN", "厦门");
        cityMap.put("MIG", "绵阳");
        cityMap.put("KWE", "贵阳");
        cityMap.put("CKG", "重庆");
        cityMap.put("KMG", "昆明");
        cityMap.put("HGH", "杭州");
        cityMap.put("NKG", "南京");
        cityMap.put("WUH", "武汉");
        cityMap.put("CSX", "长沙");
        AIRPORT_CITY_MAP = Collections.unmodifiableMap(cityMap);

        Map<Integer, String[]> routeMap = new HashMap<>();
        routeMap.put(1,  new String[]{"PEK", "CAN"});
        routeMap.put(2,  new String[]{"CAN", "PEK"});
        routeMap.put(3,  new String[]{"PEK", "CTU"});
        routeMap.put(4,  new String[]{"CTU", "PEK"});
        routeMap.put(5,  new String[]{"PEK", "SHA"});
        routeMap.put(6,  new String[]{"SHA", "PEK"});
        routeMap.put(7,  new String[]{"CAN", "CTU"});
        routeMap.put(8,  new String[]{"CTU", "CAN"});
        routeMap.put(9,  new String[]{"CAN", "SHA"});
        routeMap.put(10, new String[]{"SHA", "CAN"});
        routeMap.put(11, new String[]{"CTU", "SHA"});
        routeMap.put(12, new String[]{"SHA", "CTU"});
        routeMap.put(13, new String[]{"HRB", "PEK"});
        routeMap.put(14, new String[]{"PEK", "HRB"});
        routeMap.put(15, new String[]{"DLC", "PEK"});
        routeMap.put(16, new String[]{"PEK", "DLC"});
        routeMap.put(17, new String[]{"SHE", "PEK"});
        routeMap.put(18, new String[]{"PEK", "SHE"});
        routeMap.put(19, new String[]{"SJW", "PEK"});
        routeMap.put(20, new String[]{"PEK", "SJW"});
        routeMap.put(21, new String[]{"SZX", "CAN"});
        routeMap.put(22, new String[]{"CAN", "SZX"});
        routeMap.put(23, new String[]{"HAK", "CAN"});
        routeMap.put(24, new String[]{"CAN", "HAK"});
        routeMap.put(25, new String[]{"NNG", "CAN"});
        routeMap.put(26, new String[]{"CAN", "NNG"});
        routeMap.put(27, new String[]{"XMN", "CAN"});
        routeMap.put(28, new String[]{"CAN", "XMN"});
        routeMap.put(29, new String[]{"MIG", "CTU"});
        routeMap.put(30, new String[]{"CTU", "MIG"});
        routeMap.put(31, new String[]{"KWE", "CTU"});
        routeMap.put(32, new String[]{"CTU", "KWE"});
        routeMap.put(33, new String[]{"CKG", "CTU"});
        routeMap.put(34, new String[]{"CTU", "CKG"});
        routeMap.put(35, new String[]{"KMG", "CTU"});
        routeMap.put(36, new String[]{"CTU", "KMG"});
        routeMap.put(37, new String[]{"HGH", "SHA"});
        routeMap.put(38, new String[]{"SHA", "HGH"});
        routeMap.put(39, new String[]{"NKG", "SHA"});
        routeMap.put(40, new String[]{"SHA", "NKG"});
        routeMap.put(41, new String[]{"WUH", "SHA"});
        routeMap.put(42, new String[]{"SHA", "WUH"});
        routeMap.put(43, new String[]{"CSX", "SHA"});
        routeMap.put(44, new String[]{"SHA", "CSX"});
        ROUTE_MAP = Collections.unmodifiableMap(routeMap);
    }

    // ----------------------------------------------------------------
    // 三字码 <-> 城市名
    // ----------------------------------------------------------------

    /**
     * 根据机场三字码获取城市名称
     *
     * @param iataCode 机场三字码，如 "PEK"
     * @return 城市名称，未找到时返回 null
     */
    public static String getCityName(String iataCode) {
        if (iataCode == null || iataCode.isBlank()) return null;
        return AIRPORT_CITY_MAP.get(iataCode.toUpperCase());
    }

    /**
     * 根据机场三字码获取城市名称，未找到时返回默认值
     *
     * @param iataCode     机场三字码
     * @param defaultValue 未找到时的默认值
     * @return 城市名称或默认值
     */
    public static String getCityName(String iataCode, String defaultValue) {
        String city = getCityName(iataCode);
        return city != null ? city : defaultValue;
    }

    // ----------------------------------------------------------------
    // 航线主键 -> 城市名
    // ----------------------------------------------------------------

    /**
     * 根据航线模板主键，返回出发城市名称
     *
     * @param id 航线主键（1~44）
     * @return 出发城市名称，主键不存在时返回 null
     */
    public static String findDeptCity(Long id) {

        if (id == null) return null;
        Integer routeId = id.intValue();

        String[] codes = ROUTE_MAP.get(routeId);
        if (codes == null) return null;
        return getCityName(codes[0], codes[0]);
    }

    /**
     * 根据航线模板主键，返回到达城市名称
     *
     * @param id 航线主键（1~44）
     * @return 到达城市名称，主键不存在时返回 null
     */
    public static String findArrCity(Long id) {

        if (id == null) return null;
        Integer routeId = id.intValue();

        String[] codes = ROUTE_MAP.get(routeId);
        if (codes == null) return null;
        return getCityName(codes[1], codes[1]);
    }

    private AirportCityUtil() {}
}
