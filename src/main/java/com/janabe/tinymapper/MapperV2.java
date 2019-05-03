package com.janabe.tinymapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
    getEmbedFields()
    getColumnFields()

    processEmbedField()
    processColumnField()

    getSetterMethodOf(clazz)

    getClassOfField()
    getNameOfField()
    getFieldsOfField()

    prepareObject()

    castResultSetToField()
*/

public class MapperV2<T> {
    private Class<T> type;

    public MapperV2(Class<T> type) {
        this.type = type;
    }

    public T map(ResultSet resultSet) {
        var columnFields = this.getAnnotatedFields(type, Column.class);
        var embedFields = this.getAnnotatedFields(type, Embed.class);
        var instance = createInstance(type);
        fillFields(instance, columnFields, resultSet);
        return (T) instance;
    }

    /**
     * <p>Transform the resultSet into the value that belongs to the provided columnField.</p>
     * @param resultSet ResultSet
     * @param columnField field marked with @Column
     * @return the object that
     */
    private Object transform(ResultSet resultSet, Field columnField) {
        var castedObject = new Object();
        var columnName = getColumnNameOf(columnField);
        var _class = columnField.getType();

        try {
            castedObject = _class.cast(resultSet.getObject(columnName));
        } catch (SQLException e) {
            System.out.println("Exception occurred casting an object from resultSet value");
            e.printStackTrace();
        }

        return castedObject;
    }

    // kinda want to refactor this, looks ugly :c

    /**
     * <p>Fill all provided Fields of the provided (Object) Instance with values obtained from the provided ResultSet</p>
     * @param instance Instance of an object who's fields you want filled
     * @param fields Fields of an instance you want filled
     * @param resultSet ResultSet used to obtain values from
     */
    private void fillFields(Object instance, List<Field> fields, ResultSet resultSet) {
        for (Field field : fields) {
            var result = transform(resultSet, field);
            var setter = getSetterOf(instance, field, result.getClass());
            try {
                setter.invoke(instance, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * <p>Get the setter method with the specified signature (field, arg) from the provided _class.</p>
     * @param _class Class containing the setter method
     * @param field Field wherefore you want to obtain the setter method
     * @param arg Argument the setter method expects as parameter
     * @return Setter method for Field from _Class
     */
    private Method getSetterOf(Object _class, Field field, Class<?> arg) {
        Method method = null;

        try {
            method = _class.getClass().getDeclaredMethod(getSetterNameOf(field.getName()), arg);
        } catch (NoSuchMethodException e) {
            System.out.println("Exception occurred while getting the setter method of " + _class + " for field " + field.getName());
            e.printStackTrace();
        }

        return method;
    }

    // maybe give an option to specify the naming conventions used for setter methods, like a client can give regex expression or something)
    /**
     * <p>Get the name of the setter method corresponding to the provided fieldName.
     * This method is used as a helper method in getSetterOf.
     * e.g. field: name -> setter: setName.</p>
     * @param fieldName name of a field
     * @return name of setter method
     */
    private String getSetterNameOf(String fieldName) {
        return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    // got to look up the difference between Class<?> and Object
    private Object createInstance(Class<?> type) {
        Object instance = null;

        try {
            instance = getEmptyConstructor(type).newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            System.out.println("Exception occurred while creating a new instance of " + type.getName());
            e.printStackTrace();
        }

        return instance;
    }

    private Constructor<?> getEmptyConstructor(Class<?> type) {
        Constructor<?> constructor = null;

        try {
            constructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            System.out.println("No empty constructor found! TinyMapper needs an empty constructor in order to work!");
            e.printStackTrace();
        }

        return constructor;
    }

    // how can i create a lambda expression for this
    private String getColumnNameOf(Field columnField) {
        if (!columnField.isAnnotationPresent(Column.class)) {
            return null;
        }

        return columnField.getAnnotation(Column.class).name();
    }

    /**
     * <p>Get all fields that are annotated with the provided Annotation
     * from the provided Class.</p>
     * @param _class some Class
     * @return a list containing all fields marked with @Column
     */
    private List<Field> getAnnotatedFields(Class<?> _class, Class<? extends Annotation> wantedAnnotation) {
        var fields = Arrays.stream(_class.getDeclaredFields());
        return fields
                .filter(field -> field.isAnnotationPresent(wantedAnnotation))
                .collect(Collectors.toList());
    }
}
