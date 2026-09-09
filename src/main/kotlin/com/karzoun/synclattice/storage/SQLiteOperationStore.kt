package com.karzoun.synclattice.storage

import com.karzoun.synclattice.clock.VectorClock
import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.Operation
import com.karzoun.synclattice.model.RegisterWrite
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types

class SQLiteOperationStore(
    databasePath: Path,
) : OperationStore {
    private val lock = Any()
    private val connection: Connection

    init {
        val absolutePath = databasePath.toAbsolutePath()
        absolutePath.parent?.let(Files::createDirectories)
        connection = DriverManager.getConnection("jdbc:sqlite:$absolutePath")
        configure()
        migrate()
    }

    fun schemaVersion(): Int = synchronized(lock) {
        connection.prepareStatement("SELECT value FROM sync_meta WHERE key = 'schema_version'").use { statement ->
            statement.executeQuery().use { result ->
                check(result.next()) { "schema version is missing" }
                result.getString(1).toInt()
            }
        }
    }

    override fun append(operation: Operation): Boolean = synchronized(lock) {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val existing = loadOne(operation.id)
            if (existing != null) {
                connection.rollback()
                if (existing == operation) {
                    return@synchronized false
                }
                throw OperationConflictException("operation id ${operation.id} already exists with different content")
            }

            insertOperation(operation)
            insertContext(operation)
            if (operation is SetRemove) {
                insertRemovedDots(operation)
            }
            connection.commit()
            true
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override fun loadAll(): List<Operation> = synchronized(lock) {
        val rows = mutableListOf<StoredRow>()
        connection.prepareStatement(
            "SELECT actor, sequence, kind, logical_time, payload FROM sync_operations ORDER BY actor, sequence",
        ).use { statement ->
            statement.executeQuery().use { result ->
                while (result.next()) {
                    rows += result.toStoredRow()
                }
            }
        }
        rows.map(::hydrate)
    }

    override fun close() = synchronized(lock) {
        connection.close()
    }

    private fun configure() {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = FULL")
            statement.execute("PRAGMA busy_timeout = 5000")
        }
    }

    private fun migrate() = synchronized(lock) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_operations (
                    actor TEXT NOT NULL,
                    sequence INTEGER NOT NULL CHECK (sequence > 0),
                    kind TEXT NOT NULL CHECK (kind IN ('REGISTER_WRITE', 'SET_ADD', 'SET_REMOVE')),
                    logical_time INTEGER CHECK (logical_time IS NULL OR logical_time >= 0),
                    payload TEXT NOT NULL,
                    PRIMARY KEY (actor, sequence)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_operation_context (
                    op_actor TEXT NOT NULL,
                    op_sequence INTEGER NOT NULL,
                    actor TEXT NOT NULL,
                    counter INTEGER NOT NULL CHECK (counter >= 0),
                    PRIMARY KEY (op_actor, op_sequence, actor),
                    FOREIGN KEY (op_actor, op_sequence)
                        REFERENCES sync_operations(actor, sequence)
                        ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_removed_dots (
                    op_actor TEXT NOT NULL,
                    op_sequence INTEGER NOT NULL,
                    removed_actor TEXT NOT NULL,
                    removed_sequence INTEGER NOT NULL CHECK (removed_sequence > 0),
                    PRIMARY KEY (op_actor, op_sequence, removed_actor, removed_sequence),
                    FOREIGN KEY (op_actor, op_sequence)
                        REFERENCES sync_operations(actor, sequence)
                        ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                "INSERT OR IGNORE INTO sync_meta(key, value) VALUES ('schema_version', '$SCHEMA_VERSION')",
            )
        }

        check(schemaVersion() == SCHEMA_VERSION) {
            "unsupported SyncLattice schema version ${schemaVersion()}"
        }
    }

    private fun loadOne(dot: Dot): Operation? {
        val row = connection.prepareStatement(
            "SELECT actor, sequence, kind, logical_time, payload FROM sync_operations WHERE actor = ? AND sequence = ?",
        ).use { statement ->
            statement.setString(1, dot.actor)
            statement.setLong(2, dot.sequence)
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredRow() else null
            }
        }
        return row?.let(::hydrate)
    }

    private fun hydrate(row: StoredRow): Operation {
        val contextEntries = mutableListOf<Pair<String, Long>>()
        connection.prepareStatement(
            "SELECT actor, counter FROM sync_operation_context WHERE op_actor = ? AND op_sequence = ? ORDER BY actor",
        ).use { statement ->
            statement.setString(1, row.actor)
            statement.setLong(2, row.sequence)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    contextEntries += result.getString("actor") to result.getLong("counter")
                }
            }
        }
        val context = VectorClock.of(*contextEntries.toTypedArray())
        val id = Dot(row.actor, row.sequence)

        return when (row.kind) {
            OperationKind.REGISTER_WRITE -> RegisterWrite(
                id = id,
                context = context,
                logicalTime = checkNotNull(row.logicalTime) { "register operation is missing logical_time" },
                value = row.payload,
            )

            OperationKind.SET_ADD -> SetAdd(id, context, row.payload)
            OperationKind.SET_REMOVE -> SetRemove(id, context, row.payload, loadRemovedDots(id))
        }
    }

    private fun loadRemovedDots(operationId: Dot): Set<Dot> {
        val removed = sortedSetOf<Dot>()
        connection.prepareStatement(
            """
            SELECT removed_actor, removed_sequence
            FROM sync_removed_dots
            WHERE op_actor = ? AND op_sequence = ?
            ORDER BY removed_actor, removed_sequence
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, operationId.actor)
            statement.setLong(2, operationId.sequence)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    removed += Dot(result.getString("removed_actor"), result.getLong("removed_sequence"))
                }
            }
        }
        return removed
    }

    private fun insertOperation(operation: Operation) {
        connection.prepareStatement(
            "INSERT INTO sync_operations(actor, sequence, kind, logical_time, payload) VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, operation.id.actor)
            statement.setLong(2, operation.id.sequence)
            when (operation) {
                is RegisterWrite -> {
                    statement.setString(3, OperationKind.REGISTER_WRITE.name)
                    statement.setLong(4, operation.logicalTime)
                    statement.setString(5, operation.value)
                }

                is SetAdd -> {
                    statement.setString(3, OperationKind.SET_ADD.name)
                    statement.setNull(4, Types.BIGINT)
                    statement.setString(5, operation.element)
                }

                is SetRemove -> {
                    statement.setString(3, OperationKind.SET_REMOVE.name)
                    statement.setNull(4, Types.BIGINT)
                    statement.setString(5, operation.element)
                }
            }
            check(statement.executeUpdate() == 1) { "operation insert did not affect exactly one row" }
        }
    }

    private fun insertContext(operation: Operation) {
        if (operation.context.asMap().isEmpty()) return
        connection.prepareStatement(
            "INSERT INTO sync_operation_context(op_actor, op_sequence, actor, counter) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            for ((actor, counter) in operation.context.asMap()) {
                statement.setString(1, operation.id.actor)
                statement.setLong(2, operation.id.sequence)
                statement.setString(3, actor)
                statement.setLong(4, counter)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun insertRemovedDots(operation: SetRemove) {
        if (operation.removedDots.isEmpty()) return
        connection.prepareStatement(
            """
            INSERT INTO sync_removed_dots(op_actor, op_sequence, removed_actor, removed_sequence)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            for (removed in operation.removedDots.sorted()) {
                statement.setString(1, operation.id.actor)
                statement.setLong(2, operation.id.sequence)
                statement.setString(3, removed.actor)
                statement.setLong(4, removed.sequence)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun ResultSet.toStoredRow(): StoredRow {
        val logicalTime = getLong("logical_time").let { value -> if (wasNull()) null else value }
        return StoredRow(
            actor = getString("actor"),
            sequence = getLong("sequence"),
            kind = OperationKind.valueOf(getString("kind")),
            logicalTime = logicalTime,
            payload = getString("payload"),
        )
    }

    private data class StoredRow(
        val actor: String,
        val sequence: Long,
        val kind: OperationKind,
        val logicalTime: Long?,
        val payload: String,
    )

    private enum class OperationKind {
        REGISTER_WRITE,
        SET_ADD,
        SET_REMOVE,
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
