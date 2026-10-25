package com.hpcalc.core.data

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 迁移 SQL 的数据安全验证。
 *
 * 本地没有设备，跑不了 Room 的 `MigrationTestHelper`，但迁移失败的代价是**用户历史全丢**，
 * 不能只靠人眼读一遍 SQL。这里用 JDBC 版 SQLite 建一张 v1 的真实表，执行
 * [ADD_PINNED_COLUMN]——也就是迁移实际执行的那一句——再检查数据与表结构。
 *
 * 它验证不了 Room 的 identity hash 校验（那需要真机），但能挡住「迁移写错导致丢数据」
 * 这一类最难回滚的问题。
 */
class HistoryMigrationTest {

    private val v1Schema = """
        CREATE TABLE history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            mode TEXT NOT NULL,
            expression TEXT NOT NULL,
            result TEXT NOT NULL,
            createdAt INTEGER NOT NULL
        )
    """.trimIndent()

    private fun withDatabase(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(v1Schema)
                statement.executeUpdate(
                    "INSERT INTO history (mode, expression, result, createdAt) " +
                        "VALUES ('BASIC', '1+1', '2', 100)"
                )
                statement.executeUpdate(
                    "INSERT INTO history (mode, expression, result, createdAt) " +
                        "VALUES ('SYMBOLIC', 'x+x', '2x', 200)"
                )
                statement.executeUpdate(ADD_PINNED_COLUMN)
            }
            block(connection)
        }
    }

    @Test
    fun `existing rows survive the migration`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM history").use { rows ->
                    rows.next()
                    assertEquals(2, rows.getInt(1))
                }
                statement.executeQuery("SELECT expression, result FROM history ORDER BY id").use { rows ->
                    rows.next()
                    assertEquals("1+1", rows.getString("expression"))
                    assertEquals("2", rows.getString("result"))
                    rows.next()
                    assertEquals("x+x", rows.getString("expression"))
                }
            }
        }
    }

    @Test
    fun `the new column is not null with a zero default`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(history)").use { rows ->
                    var found = false
                    while (rows.next()) {
                        if (rows.getString("name") != "pinned") continue
                        found = true
                        assertEquals("INTEGER", rows.getString("type"))
                        assertEquals("必须非空", 1, rows.getInt("notnull"))
                        assertEquals("默认值必须是 0", "0", rows.getString("dflt_value"))
                    }
                    assertEquals("迁移后缺少 pinned 列", true, found)
                }
            }
        }
    }

    @Test
    fun `migrated rows read back as unpinned`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM history WHERE pinned = 0").use { rows ->
                    rows.next()
                    assertEquals(2, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `pinned entries sort before newer unpinned ones`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                // 老的那条被置顶，必须排在更新的那条前面。
                statement.executeUpdate("UPDATE history SET pinned = 1 WHERE expression = '1+1'")
                statement.executeQuery(
                    "SELECT expression FROM history ORDER BY pinned DESC, createdAt DESC LIMIT 100"
                ).use { rows ->
                    rows.next()
                    assertEquals("1+1", rows.getString(1))
                    rows.next()
                    assertEquals("x+x", rows.getString(1))
                }
            }
        }
    }

    @Test
    fun `the v1 rows can still be pinned after migration`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE history SET pinned = 1 WHERE expression = 'x+x'")
                statement.executeQuery(
                    "SELECT id, pinned FROM history WHERE expression = 'x+x'"
                ).use { rows ->
                    rows.next()
                    assertNotNull(rows.getLong("id"))
                    assertEquals(1, rows.getInt("pinned"))
                }
            }
        }
    }
}