package com.janabe.tinymapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class TinyMapper<T> {
    private Class<T> type;

    public TinyMapper(Class<T> type) {
        this.type = type;
    }

    /**
     * <p>Map the results from the result set to the attributes of T,
     * creating a new instance of T.</p>
     * @param rs result set
     * @return Instance of T, based on the Result Set.
     * @throws SQLException e
     * @throws NoSuchMethodException e
     * @throws IllegalArgumentException e
     * @throws IllegalAccessException e
     * @throws InstantiationException e
     * @throws InvocationTargetException e
     */
    public T map(ResultSet rs) throws SQLException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException {
        Constructor<T> constructor;
        var fieldToColumnToType = this.fieldToColumnToType();
        constructor = type.getDeclaredConstructor();
        T instance = constructor.newInstance();
        var value = new Object();

        for (var field : fieldToColumnToType.keySet()) {
            value = this.cast(fieldToColumnToType.get(field), rs);
            type.getDeclaredMethod(this.setMethod(field), String.class).invoke(instance, value);
        }

        return instance;
    }

    /**
     * <p>Cast a columns value from the result set to it's correct data type.
     * Which can then be used as value for setters.</p>
     * @param map Map(Field, Map(Column, Type))
     * @param rs result set
     * @return casted object
     * @throws SQLException e
     */
    private Object cast(Map<String, String> map, ResultSet rs) throws SQLException {
        var castedObject = new Object();
        var column = (String) map.keySet().toArray()[0];
        var clazz = map.get(column);

        try {
            castedObject = classByName(clazz).cast(rs.getObject(column));
        } catch (ClassCastException e) {
            System.out.println("Exception occurred casting an object from resultSet value: " + e);
        }

        return castedObject;
    }

    /**
     * <p>Get the class based on its classname. Functions as a wrapper
     * for Class.forName().</p>
     * @param className className
     * @return Class
     */
    private Class<?> classByName(String className) {
        Class<?> c = null;

        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.out.println("Exception occurred while getting class based on name: " + e);
        }

        return c;
    }

    /**
     * <p>Get all fields, corresponding columns and corresponding data types.</p>
     * @return Map(Field, Map(Column, Type))
     */
    private Map<String, Map<String, String>> fieldToColumnToType() {
        var fieldToColumnToType = new HashMap<String, Map<String, String>>();
        String column;
        String typeName;

        for (Field field : type.getDeclaredFields()) {
            column = field.getAnnotation(Column.class).name();
            typeName = field.getType().getName();
            fieldToColumnToType.put(field.getName(), Map.of(column, typeName));
        }

        return fieldToColumnToType;
    }

    /**
     * <p>Get the setter method from the provided field.
     * The setter method should follow the standard conventions.
     * e.g. setName() for an attribute with the name 'name'.</p>
     * @param field
     * @return
     */
    private String setMethod(String field) {
        return "set" + field.substring(0, 1).toUpperCase() + field.substring(1);
    }

}
