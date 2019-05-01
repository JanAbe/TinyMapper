## TinyMapper

A simple tool that automatically maps a ResultSet to a POJO, using the power of reflection.

\
Usage:
```java
// Certain code has been omitted as to focus only on TinyMapper.

// Make a new mapper of type T. In this case Student.
var studentMapper = new TinyMapper<>(Student.class);

// Use the mapper to map the resultSet to a Student
var student = studentMapper.map(resultSet);
```

\
POJO:

Use the @Column annotation to specify the name of the column that corresponds to the field.
```java
public class Student {
    @Column(name="id")
    private String id;

    @Column(name="first_name")
    private String firstName;

    @Column(name="last_name")
    private String lastName;

    ...
```

\
The POJO must also have an <ins>empty constructor</ins> and <ins>setters</ins> for each field.
```java
    public Student() {}
    
    public void setId(String id) {
        this.id = id;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
```

\
TODO:

- Support inheritance
- Support composition
- Refactor Exception related code
- Support omitted @Column() annotation, column name then equals field name
- Create tests
