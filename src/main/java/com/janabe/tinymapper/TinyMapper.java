package com.janabe.tinymapper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    public T map(ResultSet rs) throws Exception, SQLException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException {
        var fieldToColumnToType = this.fieldToColumnToType();
        var embeddedInfo = this.parseEmbedded();
        var constructor = type.getDeclaredConstructor();
        var instance = constructor.newInstance();
        // variable that gets used by setters for T
        var value = new Object();

        // all fieldNames that are marked as @Embed
        var embeddedFields = new ArrayList<String>();
        for (Field embeddedField : type.getDeclaredFields()) {
            if (embeddedField.isAnnotationPresent(Embed.class)) {
                embeddedFields.add(embeddedField.getName());
            }
        }

        // the attribute that is marked as @Embed, acts as a placeholder, that slowly gets build up
        var embeddedObject = new Object();
        // variable that gets used by setters on the embeddedObject
        var val = new Object();

        // Prepare the embeddedObject(s)
        // loop over all keys (these are all ClassNames)
        for (var clazz : embeddedInfo.keySet()) {
            // get the class that belongs to the ClassName, and create a new instance
            embeddedObject = this.classByName(clazz).getDeclaredConstructor().newInstance();
            // get the value that belongs to key=clazz from the embeddedField
            var ebFieldToColumnToType = embeddedInfo.get(clazz);
            // loop over all fields of 'clazz'
            for (var field : embeddedInfo.get(clazz).keySet()) {
                // get the column and type map that corresponds to the 'field'
                var columnToType = ebFieldToColumnToType.get(field);
                // get the value from the resultSet that corresponds to the column and cast it to the correct dataType.
                val = this.cast(columnToType, rs);
                // get the setter method of 'field'
                var setterName = this.setMethod(field);
                var embeddedClass = embeddedObject.getClass();
                var valueClass = val.getClass();
                var setter = embeddedClass.getDeclaredMethod(setterName, valueClass);
                setter.invoke(embeddedObject, val);
            }

            for (var field : fieldToColumnToType.keySet()) {
                // if the field is marked as @Embed -> call the setter method corresponding to this field, with the earlier made object as argument.
                // check if clazz contains field, otherwise the setter for field gets called multiple times with different arguments that are not part of the method signature
                // this is complete shitt.
                if (embeddedFields.contains(field) && clazz.toLowerCase().contains(field.toLowerCase())) {
                    instance.getClass().getDeclaredMethod(this.setMethod(field), embeddedObject.getClass()).invoke(instance, embeddedObject);
                }
            }
        }

        // loop over all fields of T
        for (var field : fieldToColumnToType.keySet()) {
            // if the field is @Column -> call the setter method corresponding to this field on T, with the earlier made value as argument.
            if (!embeddedFields.contains(field)) {
                value = this.cast(fieldToColumnToType.get(field), rs);
                type.getDeclaredMethod(this.setMethod(field), String.class).invoke(instance, value);
            }
        }

        return instance;
    }

    /**
     * <p>Cast a columns value from the result set to it's correct data type.
     * Which can then be used as value for setters.</p>
     * @param columnToType Map(Column, Type)
     * @param rs result set
     * @return casted object
     * @throws SQLException e
     */
    private Object cast(Map<String, String> columnToType, ResultSet rs) throws SQLException {
        var castedObject = new Object();
        // get the column (key) from the map
        var column = (String) columnToType.keySet().toArray()[0];
        // get the ClassName of the type that the column has
        var clazz = columnToType.get(column);

        try {
            // get the value from the resultSet that corresponds to the column
            // then cast it to the correct dataType
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
     * <p> Get information about the column attributes.
     * Get all fields, corresponding columns and corresponding data types.
     * Column -> Map<ClassName, Map<ColumnName, TypeName>>
     *           Map<id, Map<id, String>>
     * </p>
     * @return Map(Field, Map(Column, Type))
     */
    private Map<String, Map<String, String>> fieldToColumnToType() {
        var fieldToColumnToType = new HashMap<String, Map<String, String>>();
        String columnName;
        String typeName;

        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class)) {
                columnName = field.getAnnotation(Column.class).name();
                typeName = field.getType().getName();
                fieldToColumnToType.put(field.getName(), Map.of(columnName, typeName));
            } else if (field.isAnnotationPresent(Embed.class)) {
                // if field is marked with @Embed -> still add it to the map with an empty map as value
                // reason for empty map: the @Embed field doesn't directly has a column and type
                // reason for adding it at all: the result from this method is used to find all fields from T.
                // this is later used to find the corresponding setter methods. Might be able to remove it if a refactor happens.
                fieldToColumnToType.put(field.getName(), Map.of());
            }
        }

        return fieldToColumnToType;
    }

    /**
     * <p>Get information about the embedded attributes.
     * Find all attributes marked with @Embed.
     * Find all attributes of these attributes that are marked with @Column.
     * Find the columnName and dataType of these attributes.
     * e.g. :
     * Embed -> Map<ClassName, Map<FieldName, Map<ColumnName, TypeName>>>
     *          Map<FullName, Map<firstName, Map<first_name, String>>>
     *          Map<FullName, Map<lastName, Map<last_name, String>>>
     * </p>
     * @return Map(ClassName, Map(FieldName, Map(ColumnName, TypeName)))
     */
    // alle attributen, van alle embedded klassen, worden gekoppeld aan de fields.
    // dus de attributen van FullName worden gekoppeld aan zowel fullName als email
    // de attributen van email worden gekoppeld aan zowel email als fullName.
    // fullName: firstName, lastName, emailAddress
    // en dit klopt natuurlijk niet, het moet beschouwd worden als twee losse dingen.
    private Map<String, Map<String, Map<String, String>>> parseEmbedded() {
        var map = new HashMap<String, Map<String, Map<String, String>>>();
        String columnName;
        String typeName;

        for (Field field : type.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Embed.class)) {
                continue;
            }
            var fieldToColumn = new HashMap<String, Map<String, String>>();

            for (Field childField : field.getType().getDeclaredFields()) {
                if (!childField.isAnnotationPresent(Column.class)) {
                    continue;
                }
                columnName = childField.getAnnotation(Column.class).name();
                typeName = childField.getType().getName();
                fieldToColumn.put(childField.getName(), Map.of(columnName, typeName));
                map.put(field.getType().getName(), fieldToColumn);
            }
        }

        return map;
    }

    /**
     * <p>Get the setter method from the provided field.
     * The setter method should follow the standard conventions.
     * e.g. setName() for an attribute with the name 'name'.</p>
     * @param field f
     * @return setter of field
     */
    private String setMethod(String field) {
        return "set" + field.substring(0, 1).toUpperCase() + field.substring(1);
    }

}
