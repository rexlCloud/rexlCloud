package de.rexlcloud.core.database.helper;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueryFilter {

    private QueryType type;
    private String valueName;
    private Object value;

}