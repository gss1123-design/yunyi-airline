package com.hakimi.aviation.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeatProbeFactory {

    // 假设 MVP 版本全部为 180 座 (30排 * 6列)
    private static final int TOTAL_ROWS = 30;
    private static final int COLS_PER_ROW = 6;

    // 静态常量池：三种偏好的全量探测序列
    public static final List<Integer> WINDOW_PREF_LIST;
    public static final List<Integer> AISLE_PREF_LIST;
    public static final List<Integer> MIDDLE_PREF_LIST;

    static {
        List<Integer> window = new ArrayList<>();
        List<Integer> aisle = new ArrayList<>();
        List<Integer> middle = new ArrayList<>();

        // 1. 基础分类：把 180 个座位按属性拆分
        for (int row = 0; row < TOTAL_ROWS; row++) {
            int base = row * COLS_PER_ROW;
            window.add(base);     // A座 (0)
            window.add(base + 5); // F座 (5)

            middle.add(base + 1); // B座 (1)
            middle.add(base + 4); // E座 (4)

            aisle.add(base + 2);  // C座 (2)
            aisle.add(base + 3);  // D座 (3)
        }

        // 2. 组装“靠窗优先”序列 (靠窗 -> 过道 -> 中间)
        List<Integer> windowPref = new ArrayList<>(window);
        windowPref.addAll(aisle); // 降级选过道，都不喜欢夹在中间
        windowPref.addAll(middle);
        WINDOW_PREF_LIST = Collections.unmodifiableList(windowPref); // 变为只读，防并发篡改

        // 3. 组装“过道优先”序列 (过道 -> 靠窗 -> 中间)
        List<Integer> aislePref = new ArrayList<>(aisle);
        aislePref.addAll(window);
        aislePref.addAll(middle);
        AISLE_PREF_LIST = Collections.unmodifiableList(aislePref);

        // 4. 组装“中间优先”序列 (极其罕见的偏好，中间 -> 过道 -> 靠窗)
        List<Integer> middlePref = new ArrayList<>(middle);
        middlePref.addAll(aisle);
        middlePref.addAll(window);
        MIDDLE_PREF_LIST = Collections.unmodifiableList(middlePref);
    }

    /**
     * 对外暴露的获取探针方法
     */
    public static List<Integer> getProbeSequence(String preference) {
        return switch (preference != null ? preference.toLowerCase() : "") {
            case "window" -> WINDOW_PREF_LIST;
            case "aisle" -> AISLE_PREF_LIST;
            case "middle" -> MIDDLE_PREF_LIST;
            default -> WINDOW_PREF_LIST; // 默认给个靠窗优先兜底
        };
    }
}
