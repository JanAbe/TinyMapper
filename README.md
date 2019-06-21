## TinyMapper

A simple generic tool that automatically maps a ResultSet to a POJO, using the power of reflection and generics.

#### Usage
```java
// Certain code has been omitted as to focus only on TinyMapper.

// Make a new mapper of type T. In this case Student.
var studentMapper = new TinyMapper<>(Student.class);

// Use the mapper to map the resultSet to a Student.
var student = studentMapper.map(resultSet);
```

---

```java
// A slightly more fleshed out example:

// Make a new mapper of type T. In this case Student.
var studentMapper = new TinyMapper<>(Student.class);
var student = new Student();

// Query everything, or just a subset!
final var query = "SELECT * FROM Student WHERE id = ?";
final var query1 = "SELECT firstname, lastname FROM Student WHERE id = ?";
var statement = connection.prepareStatement(query);
statement.setString(1, "some-id");
var resultSet = statement.executeQuery();

while (resultSet.next()) {
    // Use the mapper to map the resultSet to a Student.
    student = studentMapper.map(resultSet);
}

statement.close();
resultSet.close();
```

#### Rules:
| POJO                                                           |
| ---------------------------------------------------------------|
| - Each POJO should have an empty constructor                   |
| - Each field should be annotated with either @Column or @Embed |
| - Each field should have a corresponding setter method         |

#### Info
| Annotation | Param | Function | 
| ---------- | ----- |--------  |
| @Column    | name  | Specify the name of the column that corresponds to this field |
| @Embed     | -     | Mark when a field doesn't directly map to a column, but its values do. Doesn't support infinite embedding |

#### Annotation Examples:

```java
public class Student {

    @Column(name="student_id")
    private String id;

    @Embed
    private Email email;
    
    @Embed
    private Person person;
    
    public Student() {}
    
    public void setId(String id) {
        this.id = id;
    }

    public void setEmail(Email email) {
        this.email = email;
    }
    
    public void setPerson(Person person) {
        this.person = person;
    }
}
```

```java
public class Person {

    @Embed
    private FullName fullName;
    
    public Person() {}
    
    public void setFullName(FullName fullName) {
        this.fullName = fullName;
    }
}
```

```java
public class FullName {
    
    @Column(name="firstname")
    private String firstName;

    @Column(name="lastname")
    private String lastName;

    public FullName() {}

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
```
