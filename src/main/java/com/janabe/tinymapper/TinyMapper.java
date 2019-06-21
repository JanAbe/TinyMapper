package com.janabe.tinymapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TinyMapper<T> {
    private Class<T> type;

    public TinyMapper(Class<T> type) {
        this.type = type;
    }

    /**
     * <p>Map the provided ResultSet to an object of type T.</p>
     * @param resultSet ResultSet containing the data
     * @return Newly created Object of type T, filled with data
     */
    public T map(ResultSet resultSet) {
        var queriedFields = getQueriedFields(resultSet);
        var columnFields = getColumnFields(type, queriedFields);
        var embedFields = getRootOfEmbedFields(type, getEmbedFields(type, queriedFields, new HashSet<>()));
        var instance = createInstance(type);

        fillColumnFields(instance, columnFields, resultSet);
        fillEmbedFields(instance, embedFields, resultSet, queriedFields);

        return (T) instance;
    }

    /**
     * <p>Fill the queried fields that are marked with @Embed.</p>
     * @param instance Class containing fields marked with @Embed
     * @param embedFields Set of all fields marked with @Embed
     * @param resultSet ResultSet used to obtain data from
     * @param queriedFields Collection of all the field names that have been queried
     */
    private void fillEmbedFields(Object instance, Set<Field> embedFields, ResultSet resultSet, Set<String> queriedFields) {
        for (Field field : embedFields) {
            var embedInstance = createInstance(field.getType());
            fillColumnFields(embedInstance, getColumnFields(field.getType(), queriedFields), resultSet);
            fillField(instance, field, embedInstance);
            fillEmbedFields(embedInstance, getEmbedFields(field.getType(), queriedFields, new HashSet<>()), resultSet, queriedFields);
        }
    }

    /**
     * <p>Fill all provided Fields of the provided (Object) Instance with values obtained from the provided ResultSet</p>
     * @param instance Instance of an object who's fields you want filled
     * @param fields Fields of an instance you want filled
     * @param resultSet ResultSet used to obtain values from
     */
    private void fillColumnFields(Object instance, Set<Field> fields, ResultSet resultSet) {
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
     * <p>Fill the field of the provided instance with the provided argument.</p>
     * @param instance Object
     * @param field Field
     * @param arg Object
     */
    private void fillField(Object instance, Field field, Object arg) {
        var setter = getSetterOf(instance, field, arg.getClass());

        try {
            setter.invoke(instance, arg);
        } catch (IllegalAccessException | InvocationTargetException e) {
            System.out.println("Exception occurred while invoking a setter method on object " + instance.getClass().getName());
            e.printStackTrace();
        }
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

    /**
     * <p>Create a new instance of the provided _class.</p>
     * @param _class Class you want a new instance from
     * @return New instance
     */
    private Object createInstance(Class<?> _class) {
        Object instance = null;

        try {
            instance = getEmptyConstructor(_class).newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            System.out.println("Exception occurred while creating a new instance of " + _class.getName());
            e.printStackTrace();
        }

        return instance;
    }

    /**
     * <p>Get the empty constructor of the provided class.</p>
     * @param _class Class whose empty constructor you want
     * @return Empty constructor
     */
    private Constructor<?> getEmptyConstructor(Class<?> _class) {
        Constructor<?> constructor = null;

        try {
            constructor = _class.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            System.out.println("No empty constructor found! TinyMapper needs an empty constructor in order to work!");
            e.printStackTrace();
        }

        return constructor;
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

    /**
     * <p>Get the name of the column of the @Column annotated field.</p>
     * @param columnField Field marked with @Column
     * @return Name of the column corresponding to the provided field
     */
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
     * @return a list containing all fields marked with the provided annotation
     */
    private Set<Field> getAnnotatedFields(Class<?> _class, Class<? extends Annotation> wantedAnnotation) {
        var fields = Arrays.stream(_class.getDeclaredFields());
        return fields
                .filter(field -> field.isAnnotationPresent(wantedAnnotation))
                .collect(Collectors.toSet());
    }

    /**
     * <p>Get all fields that are annotated with @Column and that are present in the queried fields.</p>
     * @param _class Class you want the column fields from that are being queried
     * @param queriedFields Set of names of the fields that are being queried
     * @return Set of all fields that are annotated with @Column and who are actually being queried
     */
    private Set<Field> getColumnFields(Class<?> _class, Set<String> queriedFields) {
        var wantedFields = new HashSet<Field>();

        for (String queriedField : queriedFields) {
            for (Field field : _class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class) 
                        && field.getAnnotation(Column.class).name().equals(queriedField)) {
                    wantedFields.add(field);
                }
            }
        }

        return wantedFields;
    }

    /**
     * <p> Get all fields that are annotated with @Embed and that are present in the queried fields.</p>
     * @param _class Start point class from which you want all the embed anontated fields (that are queried)
     * @param queriedFields Set of names of the fields that are being queried
     * @param wantedFields Set containing all embed fields that are being queried, passed for recursive purposes
     * @return Set containing all fields that annotated with @Embed and that are being queried.
     */
    private Set<Field> getEmbedFields(Class<?> _class, Set<String> queriedFields, Set<Field> wantedFields) {
        var embedFields = getAnnotatedFields(_class, Embed.class);

        for (Field embedField : embedFields) {
            if (queriedFieldMatch(embedField.getType(), queriedFields)) {
                wantedFields.add(embedField);
            } else {
                getEmbedFields(embedField.getType(), queriedFields, wantedFields);
            }
        }

        return wantedFields;
    }

    /**
     * <p>Get the root embed field for each embed fields that is being queried.
     * It only supports two levels of @Embed fields. 
     * e.g. Student { 
     *      @Embed
     *      Person person;
     * }
     * Person {
     *      @Embed
     *      FullName fullName;
     * }
     * 
     * FullName can not have a field that is annotated with @Embed.
     * </p>
     * @param _class Root class
     * @param embedFields Set of all embed fields that are being queried
     * @return Set of all root fields that are annotated with @Embed.
     */
    private Set<Field> getRootOfEmbedFields(Class<?> _class, Set<Field> embedFields) {
        Set<Field> roots = new HashSet<>();

        for (Field embedField : getAnnotatedFields(_class, Embed.class)) {
            if (contains(embedFields, embedField.getName())) {
                roots.add(embedField);
            } else {
                for (Field embedField1 : getAnnotatedFields(embedField.getType(), Embed.class)) {
                    if (contains(embedFields, embedField1.getName())) {
                        roots.add(embedField);
                    } 
                }
            }
        }

        return roots;
    }
    
    /**
     * <p>Get all names of the fields that have been queried.</p>
     * @param resultSet ResultSet of the executed query
     * @return All names of the queried fields that are present in the resultSet
     */
    private Set<String> getQueriedFields(ResultSet resultSet) {
        Set<String> queriedFields = new HashSet<String>();

        try {
            var metaData = resultSet.getMetaData();
            var nrOfColumns = metaData.getColumnCount();

            for (int i=1; i<=nrOfColumns; i++) {
                var columnName = metaData.getColumnName(i);
                queriedFields.add(columnName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return queriedFields;
    }
    
    /**
     * <p>Helper method to check if the provided class has at least one field that is annotated with @Column and that matches a field in the queried fields set.<p>
     * @param _class
     * @param queriedFields
     * @return
     */
    private boolean queriedFieldMatch(Class<?> _class, Set<String> queriedFields) {
        var matchFound = false;
        for (String queriedField : queriedFields) {
            for (Field field : _class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class) 
                        && field.getAnnotation(Column.class).name().equals(queriedField)) {
                    matchFound = true;
                    return matchFound;
                }
            }
        }

        return matchFound;
    }

    /**
     * <p>Helper method to check if a list of embed annotated fields has atleast one field with the provided name.</p>
     * @param embedFields Set of fields that are marked with @Embed.
     * @param name The name of a field that is being queried.
     * @return True if the name matches one of the embed fields. False otherwise.
     */
    private boolean contains(Set<Field> embedFields, String name) {
        for (Field f : embedFields) {
            if (f.getName().equals(name)) {
                return true;
            }
        }

        return false;
    }
}
