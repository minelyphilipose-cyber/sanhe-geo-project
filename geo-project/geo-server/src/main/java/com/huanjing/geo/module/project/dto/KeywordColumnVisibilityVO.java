package com.huanjing.geo.module.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordColumnVisibilityVO {
    /**
     * Whether the area word column is rendered in the builder.
     */
    private boolean area;
    /**
     * Whether the prefix word column is rendered in the builder.
     */
    private boolean prefix;
    /**
     * Whether the standard single core word column is rendered in the builder.
     */
    private boolean core;
    /**
     * Whether the industry word column is rendered in the builder.
     */
    private boolean industry;
    /**
     * Whether the suffix word column is rendered in the builder.
     */
    private boolean suffix;
    /**
     * Whether compare mode renders the two core word columns.
     * This flag controls both coreWordsA and coreWordsB.
     */
    private boolean compareCore;
    /**
     * Whether compare mode renders the compare connector word column.
     */
    private boolean compareWord;
}
