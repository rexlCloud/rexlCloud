package de.rexlcloud.core.database;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * How a class annotated with {@link DatabaseData} maps to a table, and the SQL to read and write it.
 * Identifiers are checked and quoted, all values are bound as parameters.
 */
final class TableMapping<T> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /**
     * A field stored in a column.
     */
    record Column(String name, Field field, DatabaseValue annotation, ValueCodec codec) {

        String quoted() {
            return quote(name);
        }

        Object read(Object object) {
            try {
                return codec.encode(field.get(object));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        void write(Object object, Object databaseValue) {
            try {
                field.set(object, codec.decode(databaseValue));
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalStateException("Could not read column " + name + " into "
                        + field.getDeclaringClass().getSimpleName() + "." + field.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * A statement with its parameters.
     */
    record Query(String sql, List<Object> parameters) {
    }

    private final Class<T> type;
    private final String table;
    private final List<Column> columns;
    private final Map<String, Column> columnsByName;
    @Nullable
    private final Column id;
    private final Constructor<T> constructor;

    TableMapping(Class<T> type) {
        this.type = type;
        var data = type.getAnnotation(DatabaseData.class);
        if (data == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @DatabaseData");
        }
        this.table = checkIdentifier(data.tableName(), type);

        var columns = new ArrayList<Column>();
        Column id = null;
        for (var field : fieldsOf(type)) {
            var annotation = field.getAnnotation(DatabaseValue.class);
            if (annotation == null || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            var name = checkIdentifier(annotation.valueName().isBlank() ? field.getName() : annotation.valueName(), type);
            var column = new Column(name, field, annotation, new ValueCodec(field, annotation));
            columns.add(column);
            if (annotation.id()) {
                if (id != null) {
                    throw new IllegalArgumentException(type.getName() + " has more than one id column");
                }
                id = column;
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " has no @DatabaseValue fields");
        }
        this.columns = List.copyOf(columns);
        this.columnsByName = columns.stream().collect(Collectors.toMap(Column::name, column -> column,
                (first, second) -> {
                    throw new IllegalArgumentException(type.getName() + " uses the column " + first.name() + " twice");
                }, LinkedHashMap::new));
        this.id = id;

        try {
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getName() + " needs a constructor without parameters", e);
        }
    }

    /**
     * Annotated fields of the class and its superclasses, superclass fields first.
     */
    private static List<Field> fieldsOf(Class<?> type) {
        var hierarchy = new ArrayList<Class<?>>();
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.addFirst(current);
        }
        var fields = new ArrayList<Field>();
        hierarchy.forEach(current -> fields.addAll(List.of(current.getDeclaredFields())));
        return fields;
    }

    private static String checkIdentifier(String name, Class<?> type) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid table or column name '" + name + "' in " + type.getName()
                    + ": use letters, digits and '_'");
        }
        return name;
    }

    private static String quote(String identifier) {
        return "`" + identifier + "`";
    }

    Class<T> type() {
        return type;
    }

    List<Column> columns() {
        return columns;
    }

    private Column requireId() {
        if (id == null) {
            throw new IllegalStateException(type.getName() + " has no id column (@DatabaseValue(id = true))");
        }
        return id;
    }

    /**
     * Whether the database generates the id (AUTO_INCREMENT), so inserts leave it out.
     */
    private boolean generatesId() {
        return id != null && id.codec().isInteger();
    }

    private List<Column> insertColumns() {
        return generatesId() ? columns.stream().filter(column -> column != id).toList() : columns;
    }

    private String columnList() {
        return columns.stream().map(Column::quoted).collect(Collectors.joining(", "));
    }

    // Statements

    String createTable() {
        var definitions = new ArrayList<String>();
        for (var column : columns) {
            var definition = column.quoted() + " " + column.codec().sqlType();
            if (column == id) {
                definition += generatesId() ? " NOT NULL AUTO_INCREMENT" : " NOT NULL";
            }
            definitions.add(definition);
        }
        if (id != null) {
            definitions.add("PRIMARY KEY (" + id.quoted() + ")");
        }
        return "CREATE TABLE IF NOT EXISTS " + quote(table) + " (" + String.join(", ", definitions) + ")";
    }

    String dropTable() {
        return "DROP TABLE IF EXISTS " + quote(table);
    }

    Query insert(Object object) {
        var insertColumns = insertColumns();
        var sql = "INSERT INTO " + quote(table) + " ("
                + insertColumns.stream().map(Column::quoted).collect(Collectors.joining(", "))
                + ") VALUES (" + "?, ".repeat(insertColumns.size() - 1) + "?)";
        return new Query(sql, insertColumns.stream().map(column -> column.read(object)).toList());
    }

    boolean hasGeneratedId() {
        return generatesId();
    }

    Query update(Object object) {
        var idColumn = requireId();
        var setColumns = columns.stream().filter(column -> column != idColumn).toList();
        var sql = "UPDATE " + quote(table) + " SET "
                + setColumns.stream().map(column -> column.quoted() + " = ?").collect(Collectors.joining(", "))
                + " WHERE " + idColumn.quoted() + " = ?";
        var parameters = new ArrayList<>(setColumns.stream().map(column -> column.read(object)).toList());
        parameters.add(idColumn.read(object));
        return new Query(sql, parameters);
    }

    Query deleteObject(Object object) {
        var idColumn = requireId();
        return new Query("DELETE FROM " + quote(table) + " WHERE " + idColumn.quoted() + " = ?",
                parameters(idColumn.read(object)));
    }

    Query selectById(Object id) {
        var idColumn = requireId();
        return new Query("SELECT " + columnList() + " FROM " + quote(table) + " WHERE " + idColumn.quoted() + " = ?",
                parameters(idColumn.codec().encode(id)));
    }

    Query select(QueryFilter... filters) {
        return appendFilters(new Query("SELECT " + columnList() + " FROM " + quote(table), List.of()), filters, true);
    }

    /**
     * DELETE with at least one WHERE filter; deleting every row by accident is refused.
     */
    Query delete(QueryFilter... filters) {
        var hasWhere = filters != null && Arrays.stream(filters).anyMatch(filter -> isWhere(filter.getType()));
        if (!hasWhere) {
            throw new IllegalArgumentException("Refusing to delete from " + table + " without a WHERE filter");
        }
        return appendFilters(new Query("DELETE FROM " + quote(table), List.of()), filters, false);
    }

    private static List<Object> parameters(Object value) {
        var parameters = new ArrayList<>();
        parameters.add(value);
        return parameters;
    }

    private static boolean isWhere(QueryType type) {
        return type != QueryType.LIMIT && type != QueryType.ORDER_BY_ASC && type != QueryType.ORDER_BY_DESC;
    }

    private Column filterColumn(QueryFilter filter) {
        var column = columnsByName.get(filter.getValueName());
        if (column == null) {
            throw new IllegalArgumentException("Unknown column '" + filter.getValueName() + "' for table " + table
                    + ", known columns: " + columnsByName.keySet());
        }
        return column;
    }

    /**
     * Object built from a row of a SELECT made by this mapping.
     */
    T createObject(Object[] row) {
        try {
            var object = constructor.newInstance();
            for (int i = 0; i < columns.size(); i++) {
                columns.get(i).write(object, row[i]);
            }
            return object;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    void setGeneratedId(Object object, Object key) {
        requireId().write(object, key);
    }

    private Query appendFilters(Query query, QueryFilter[] filters, boolean allowOrder) {
        if (filters == null || filters.length == 0) {
            return query;
        }
        var sql = new StringBuilder(query.sql());
        var parameters = new ArrayList<>(query.parameters());

        var where = new ArrayList<String>();
        var order = new ArrayList<String>();
        Integer limit = null;
        for (var filter : filters) {
            switch (filter.getType()) {
                case LIMIT -> {
                    if (!(filter.getValue() instanceof Number number) || number.longValue() < 0) {
                        throw new IllegalArgumentException("LIMIT needs a positive number, got " + filter.getValue());
                    }
                    limit = (int) Math.min(number.longValue(), Integer.MAX_VALUE);
                }
                case ORDER_BY_ASC, ORDER_BY_DESC -> {
                    if (!allowOrder) {
                        throw new IllegalArgumentException("ORDER BY is not supported here");
                    }
                    order.add(filterColumn(filter).quoted() + (filter.getType() == QueryType.ORDER_BY_DESC ? " DESC" : " ASC"));
                }
                default -> {
                    var column = filterColumn(filter);
                    if (filter.getValue() == null && filter.getType() == QueryType.WHERE_EQUALS_TO) {
                        where.add(column.quoted() + " IS NULL");
                    } else if (filter.getValue() == null && filter.getType() == QueryType.WHERE_MORE_OR_LESS_THAN) {
                        where.add(column.quoted() + " IS NOT NULL");
                    } else if (filter.getType() == QueryType.WHERE_EQUALS_IGNORE_CASE) {
                        if (!(filter.getValue() instanceof String)) {
                            throw new IllegalArgumentException("WHERE_EQUALS_IGNORE_CASE needs a text, got " + filter.getValue());
                        }
                        where.add("LOWER(" + column.quoted() + ") = LOWER(?)");
                        parameters.add(column.codec().encode(filter.getValue()));
                    } else {
                        where.add(column.quoted() + " " + filter.getType().getSqlSyntax() + " ?");
                        parameters.add(column.codec().encode(filter.getValue()));
                    }
                }
            }
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        if (!order.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", order));
        }
        if (limit != null) {
            // A checked number, so it can be part of the statement
            sql.append(" LIMIT ").append(limit);
        }
        return new Query(sql.toString(), parameters);
    }

}
