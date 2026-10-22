package de.rexlcloud.core.database.helper;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QueryType {

    //GROUP_BY("GROUP BY"),
    ORDER_BY_ASC("ORDER BY"),
    ORDER_BY_DESC("ORDER BY"),
    WHERE_EQUALS_TO("="),
    WHERE_LESS_THAN("<"),
    WHERE_LESS_OR_EQUALS_THAN("<="),
    WHERE_MORE_THAN(">"),
    WHERE_MORE_OR_EQUALS_THAN(">="),
    /**
     * Not equal to the value; with a {@code null} value: the column is set.
     */
    WHERE_MORE_OR_LESS_THAN("<>"),
    LIMIT("LIMIT");

    private final String sqlSyntax;
}