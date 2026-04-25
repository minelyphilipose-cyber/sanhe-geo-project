package com.huanjing.geo.module.presale.generate.l3;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 售前报表文案格式化工具。
 *
 * <p>约定:所有对用户可见的数值文案统一按 HALF_UP 取整输出。
 * 统一入口可避免在各文案拼接点重复处理小数规则。</p>
 */
@Component
public class PresaleTextFormatter {

    public String formatInt(Number value) {
        if (value == null) {
            return "—";
        }
        return String.valueOf(roundToInt(value));
    }

    public String formatPercentInt(Number value) {
        if (value == null) {
            return "—";
        }
        return roundToInt(value) + "%";
    }

    public int roundToInt(Number value) {
        if (value == null) {
            return 0;
        }
        return BigDecimal.valueOf(value.doubleValue())
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}
