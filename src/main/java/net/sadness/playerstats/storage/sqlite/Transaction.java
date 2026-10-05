package net.sadness.playerstats.storage.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

final class Transaction {

    @FunctionalInterface
    interface Work {
        void run() throws SQLException;
    }

    private Transaction() {
    }

    static void run(Connection connection, Work work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            work.run();
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
