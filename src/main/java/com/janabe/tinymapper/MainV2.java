package com.janabe.tinymapper;

import com.janabe.tinymapper.local.StudentEmbedPOJO;
import com.janabe.tinymapper.local.StudentPOJO;

import java.sql.*;

public class MainV2 {
    public static void main(String[] args) {
        StudentPOJO studentPOJO = null;
        var mapper = new MapperV2<>(StudentPOJO.class);
        Connection connection = null;
        PreparedStatement statement;
        ResultSet resultSet;

        try {
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5433/FeedbackSystem", "postgres", "95Pv&5fc6y&1^8T7ej102I8eW5MGv@");
        } catch (SQLException e) {
            System.out.println(":( " + e);
        }

        final var query = "SELECT * FROM Student WHERE id=?";
        try {
            statement = connection.prepareStatement(query);
            statement.setString(1, "e2c7de0e-8ae8-4529-a036-6b7989e81616");
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                studentPOJO = mapper.map(resultSet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(studentPOJO.toString());
    }
}
