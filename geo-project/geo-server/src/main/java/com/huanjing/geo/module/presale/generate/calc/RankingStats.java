package com.huanjing.geo.module.presale.generate.calc;

public record RankingStats(
        int count1,
        int count2,
        int count3,
        int count4,
        int count5,
        int countGe6
) {
    public int total() {
        return count1 + count2 + count3 + count4 + count5 + countGe6;
    }
}

