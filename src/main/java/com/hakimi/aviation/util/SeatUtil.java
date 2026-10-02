package com.hakimi.aviation.util;

/**
 * 座位工具类：座位偏移量与物理座位号之间的转换。
 * 约定每排 6 座，列依次为 A、B、C、D、E、F；offset 从 0 开始。
 */
public class SeatUtil {

    private static final char[] SEAT_COLUMNS = {'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * 座位偏移量 → 物理座位号。
     * 例如 offset=0 → "1A"，offset=67 → "12B"。
     *
     * @param seatOffset 座位偏移量，null 或负数返回 null
     * @return 形如 "12A" 的座位号
     */
    public static String toSeatNo(Integer seatOffset) {
        if (seatOffset == null || seatOffset < 0) {
            return null;
        }
        int row = seatOffset / 6 + 1;
        int col = seatOffset % 6;
        return row + String.valueOf(SEAT_COLUMNS[col]);
    }
}
