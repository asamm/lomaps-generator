package com.asamm.osmTools.mbtilesextract.mbtiles

import com.asamm.osmTools.mbtilesextract.tiles.Tile
import com.asamm.osmTools.mbtilesextract.tiles.TileCoord
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import org.sqlite.SQLiteConfig
import java.nio.file.Path
import java.sql.*
import java.util.*

class Mbtiles private constructor(
    private val connection: Connection,
    private val skipIndexCreation: Boolean = false,
    private val vacuumAnalyze: Boolean = false
) : AutoCloseable {



    private var getTileStatement: PreparedStatement? = null

    private var _tileWriter: TileWriter? = null
    private val tileWriter: TileWriter
        get() {
            if (_tileWriter == null) {
                _tileWriter = TileWriter(connection)
            }
            return _tileWriter!!
        }


    init {
        //this.connection = connection
        if (skipIndexCreation) Logger.i(TAG, "Skip adding index to sqlite DB")
        if (vacuumAnalyze) Logger.i(TAG, "Vacuum analyze sqlite DB after writing")
    }


    companion object {
        val TAG: String = Mbtiles::class.java.simpleName

        // https://www.sqlite.org/src/artifact?ci=trunk&filename=magic.txt
        private const val MBTILES_APPLICATION_ID = 0x4d504258

        private const val TILES_TABLE = "tiles"
        private const val TILES_COL_X = "tile_column"
        private const val TILES_COL_Y = "tile_row"
        private const val TILES_COL_Z = "zoom_level"
        private const val TILES_COL_DATA = "tile_data"

        private const val TILES_DATA_TABLE = "tiles_data"
        private const val TILES_DATA_COL_DATA_ID = "tile_data_id"
        private const val TILES_DATA_COL_DATA = "tile_data"

        private const val TILES_SHALLOW_TABLE = "tiles_shallow"
        private const val TILES_SHALLOW_COL_DATA_ID = TILES_DATA_COL_DATA_ID

        private const val METADATA_TABLE = "metadata"
        private const val METADATA_COL_NAME = "name"
        private const val METADATA_COL_VALUE = "value"

        fun newWriteToFileDatabase(path: Path): Mbtiles {

            val sqliteConfig = SQLiteConfig()
            sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.OFF)
            sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            sqliteConfig.setCacheSize(1000000) // 1GB
            sqliteConfig.setLockingMode(SQLiteConfig.LockingMode.EXCLUSIVE)
            sqliteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY)
            sqliteConfig.setApplicationId(MBTILES_APPLICATION_ID)

            Utils.createParentDirs(path)

            val connection: Connection = newConnection(
                "jdbc:sqlite:" + path.toAbsolutePath(),
                sqliteConfig
            )
            val mbtiles = Mbtiles(
                connection, true, true
            )

            // create tables and skip index creation
            mbtiles.createTables()

            return mbtiles
        }

        /**
         * Returns a new connection to an mbtiles file optimized for reads with extra mbtiles and pragma options set from
         * `options`.
         */
        fun newReadOnlyDatabase(path: Path): Mbtiles {
            Objects.requireNonNull(path)
            val config = SQLiteConfig()
            config.setReadOnly(true)
            config.setCacheSize(100000)
            config.setLockingMode(SQLiteConfig.LockingMode.EXCLUSIVE)
            config.setPageSize(32768)
            // helps with 3 or more threads concurrently accessing:
            // config.setOpenMode(SQLiteOpenMode.NOMUTEX);
            val connection: Connection = newConnection(
                "jdbc:sqlite:" + path.toAbsolutePath(),
                config
            )
            return Mbtiles(connection, false, false)
        }

        private fun newConnection(url: String, defaults: SQLiteConfig): Connection {
            try {
                loadDriver()

                val config = SQLiteConfig(defaults.toProperties())

                return DriverManager.getConnection(url, config.toProperties())
            } catch (e: SQLException) {
                Logger.e(TAG, "Error in opening SQLite connection: $url, Error: ${e.message}")
                throw IllegalArgumentException("Unable to create connection to DB $url", e)
            }
        }


        /**
         * Loads the SQLite JDBC driver.
         */
        private fun loadDriver() {
            try {
                Class.forName("org.sqlite.JDBC")
            } catch (e: ClassNotFoundException) {
                Logger.e(TAG, "Error: SQLite JDBC Driver not found!")
                throw IllegalStateException("Can not load JDBC driver", e)
            }
        }
    }

    // LOAD TILES

    fun getTile(x: Int, y: Int, z: Int): Tile? {
        return try {
            val stmt = prepareGetTileStatement()
            stmt.setInt(1, x)
            stmt.setInt(2, (1 shl z) - 1 - y) // convert to TMS
            stmt.setInt(3, z)
            stmt.executeQuery().use { rs ->
                if (rs.next()) Tile(TileCoord(x, y, z), rs.getBytes(TILES_COL_DATA)) else null
            }
        } catch (throwables: SQLException) {
            throw IllegalStateException("Problem when load tile, stmt ${getTileStatement.toString()}", throwables)
        }
    }

    /**
     * Load tiles for defined zoom level, x, and range of y     *
     */
    fun getTilesInZoomAndRange(zoomLevel: Int, x: Int, yStart: Int, yEnd: Int): QueryIterator<Tile> {
        return try {

            val query = { connection: Connection ->
                connection.prepareStatement("SELECT * FROM $TILES_TABLE WHERE $TILES_COL_Z = ? AND $TILES_COL_X = ? AND $TILES_COL_Y >= ? AND $TILES_COL_Y <= ?")
                    .apply {
                        setInt(1, zoomLevel)
                        setInt(2, x)
                        setInt(3, (1 shl zoomLevel) - 1 - yEnd)
                        setInt(4, (1 shl zoomLevel) - 1 - yStart)
                    }
            }
            val rowMapper = { rs: ResultSet ->
                Tile(
                    TileCoord(rs.getInt(TILES_COL_X), rs.getInt(TILES_COL_Y), rs.getInt(TILES_COL_Z)),
                    rs.getBytes(TILES_COL_DATA)
                )
            }
            return QueryIterator(connection, query, rowMapper)
        } catch (throwables: SQLException) {
            throw IllegalStateException("Problem when load tiles, stmt ${getTileStatement.toString()}", throwables)
        }
    }

    // INSERT TILE
    fun insertTile(tile: Tile) {
        tileWriter.insertTile(tile)
    }

    // METADATA

    fun getMetadata(): Metadata {

        val sql = "SELECT * FROM $METADATA_TABLE"

        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val metadata = Metadata()
                    while (rs.next()) {
                        metadata.setValue(rs.getString(METADATA_COL_NAME), rs.getString(METADATA_COL_VALUE))
                    }
                    return metadata
                }
            }
        } catch (throwables: SQLException) {
            throw IllegalStateException("Problem when getting metadata, stmt $sql", throwables)
        }
    }

    fun insertMetadata(metadata: Metadata) {
        val sql = "INSERT OR REPLACE INTO $METADATA_TABLE ($METADATA_COL_NAME, $METADATA_COL_VALUE) VALUES (?, ?)"
        try {
            connection.prepareStatement(sql).use { stmt ->
                for ((key, value) in metadata.data) {
                    stmt.setString(1, key)
                    stmt.setString(2, value)
                    stmt.executeUpdate()
                }
            }
        } catch (throwables: SQLException) {
            throw IllegalStateException("Problem when insert metadata, stmt $sql", throwables)
        }
    }

    // PREPARED STATEMENTS

    private fun prepareGetTileStatement(): PreparedStatement {
        if (getTileStatement == null) {
            try {
                getTileStatement = connection.prepareStatement(
                    """
                    SELECT tile_data FROM $TILES_TABLE
                    WHERE $TILES_COL_X=? AND $TILES_COL_Y=? AND $TILES_COL_Z=?""".trimIndent()
                )
            } catch (throwables: SQLException) {
                throw IllegalStateException(throwables)
            }
        }
        return getTileStatement!!
    }


    // EXECUTE SQL

    private fun execute(sqls: Collection<String>): Mbtiles {
        for (query in sqls) {
            try {
                connection.createStatement().use { statement ->
                    statement.execute(query)
                }
            } catch (e: SQLException) {
                throw IllegalStateException("Error executing queries " + java.lang.String.join(",", sqls), e)
            }
        }
        return this
    }

    private fun execute(vararg sql: String): Mbtiles {
        return execute(listOf(*sql))
    }

    // INIT TABLES

    private fun createTables(): Mbtiles {
        val sqlStatements = mutableListOf<String>()

        sqlStatements.add("create table $METADATA_TABLE ($METADATA_COL_NAME text, $METADATA_COL_VALUE text);")

        val tilesShallowPrimaryKeyAddition = if (skipIndexCreation) "" else """
             , primary key($TILES_COL_Z,$TILES_COL_X,$TILES_COL_Y)
                """.trimIndent()
        sqlStatements.add(
            """
                create table $TILES_SHALLOW_TABLE (
                    $TILES_COL_Z integer,
                    $TILES_COL_X integer,
                    $TILES_COL_Y integer,
                    $TILES_SHALLOW_COL_DATA_ID integer
                    $tilesShallowPrimaryKeyAddition
                ) ${if (skipIndexCreation) "" else "without rowid"}
                """.trimIndent()
        )

        sqlStatements.add(
            """
                create table $TILES_DATA_TABLE (
                    $TILES_DATA_COL_DATA_ID integer primary key,
                    $TILES_DATA_COL_DATA blob
                )
            """.trimIndent()
        )

        // VIEW TILES
        sqlStatements.add(
            """
                create view $TILES_TABLE AS
                select
                    $TILES_SHALLOW_TABLE.$TILES_COL_Z as $TILES_COL_Z,
                    $TILES_SHALLOW_TABLE.$TILES_COL_X as $TILES_COL_X,
                    $TILES_SHALLOW_TABLE.$TILES_COL_Y as $TILES_COL_Y,
                    $TILES_DATA_TABLE.$TILES_DATA_COL_DATA as $TILES_COL_DATA
                from $TILES_SHALLOW_TABLE
                join $TILES_DATA_TABLE on $TILES_SHALLOW_TABLE.$TILES_SHALLOW_COL_DATA_ID = $TILES_DATA_TABLE.$TILES_DATA_COL_DATA_ID
            """.trimIndent()
        )

        // Create indexes on the tiles table
        if ( !skipIndexCreation){
            createIndexes()
        }

        // Execute the DDL statements
        return execute(sqlStatements)
    }

    /**
     * Creates indexes on the tiles table.
     */
    private fun createIndexes() {

        val sqls = listOf(
            "create unique index name on $METADATA_TABLE ($METADATA_COL_NAME);",
            "create unique index tiles_shallow_index on $TILES_SHALLOW_TABLE ($TILES_COL_Z, $TILES_COL_X, $TILES_COL_Y);"
        )
        execute(sqls)
    }
    // CONNECTION

    /**
     * Returns the active connection.
     */
    fun getConnection(): Connection? {
        return connection
    }

    /**
     * Does a VACUUM and ANALYZE on the database.
     */
    private fun vacuumAnalyze(): Mbtiles {
        return execute(listOf("VACUUM", "ANALYZE"))
    }

    /**
     * Closes the database connection.
     */
    override fun close() {
        try {
            // close tile writer only if it's initialized
            _tileWriter?.close()  // Close only if initialized

            if (skipIndexCreation) {
                // create indexes only if not created before
                createIndexes()
            }

            if (vacuumAnalyze) {
                vacuumAnalyze()
            }
            connection?.close()

        } catch (e: SQLException) {
            Logger.e(TAG, "Error closing SQLite connection!")
            throw IllegalStateException("Could not close SQLite connection", e)
        }
    }

    // TILE WRITER
    /**
     * Writes tiles to the database.
     */
    class TileWriter(connection: Connection) : AutoCloseable {

        private val TAG: String = TileWriter::class.java.simpleName

        private var tileInsertedCounter = 1

        private val insertTileShallowStatement: PreparedStatement = connection.prepareStatement(
            """
            INSERT INTO $TILES_SHALLOW_TABLE ($TILES_COL_Z, $TILES_COL_X, $TILES_COL_Y, $TILES_SHALLOW_COL_DATA_ID)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        )

        private val insertTileDataStatement: PreparedStatement = connection.prepareStatement(
            """
            INSERT INTO $TILES_DATA_TABLE ($TILES_DATA_COL_DATA_ID, $TILES_DATA_COL_DATA)
            VALUES (?, ?)
            """.trimIndent(),
            PreparedStatement.RETURN_GENERATED_KEYS
        )

        private fun insertTileData(tile: Tile) {
            try {
                insertTileDataStatement.setBytes(2, tile.bytes)
                insertTileDataStatement.setInt(1, tileInsertedCounter)
                insertTileDataStatement.addBatch()
            } catch (e: SQLException) {
                Logger.e(
                    TAG, "Problem when adding tile data ${tile.coord} to batch, " +
                            "stmt ${insertTileDataStatement.toString()}", e
                )
                throw IllegalStateException(
                    "Problem when adding tile data ${tile.coord} to batch}", e
                )
            }
        }

        fun insertTile(tile: Tile) {
            try {
                insertTileData(tile)
                insertTileShallowStatement.setInt(1, tile.coord.z)
                insertTileShallowStatement.setInt(2, tile.coord.x)
                //insertTileShallowStatement.setInt(3, (1 shl tile.coord.z) - 1 - tile.coord.y) // use if not TMS
                insertTileShallowStatement.setInt(3, tile.coord.y)
                insertTileShallowStatement.setInt(4, tileInsertedCounter)

                insertTileShallowStatement.addBatch()
                tileInsertedCounter++

                if (tileInsertedCounter % 1000 == 0) {
                    flushBatch()
                }
            } catch (e: SQLException) {
                throw IllegalStateException(
                    "Problem when adding tile ${tile.coord} to batch, stmt ${insertTileShallowStatement.toString()}", e
                )
            }
        }

        private fun flushBatch() {
            try {
                insertTileDataStatement.executeBatch()
                insertTileShallowStatement.executeBatch()

                insertTileDataStatement.clearBatch()
                insertTileShallowStatement.clearBatch()
            } catch (e: SQLException) {
                throw IllegalStateException(
                    "Problem when executing batch, stmt ${insertTileShallowStatement.toString()}", e
                )
            }
        }

        override fun close() {
            flushBatch()
        }
    }


    // QUERY ITERATOR

    /**
     * Generic iterator that lazily fetches results from a database query.
     */
    class QueryIterator<T>(
        private val connection: Connection,
        query: (Connection) -> PreparedStatement,
        private val rowMapper: (ResultSet) -> T
    ) : Iterator<T>, AutoCloseable {


        private val statement: PreparedStatement = query(connection)
        private val resultSet: ResultSet = statement.executeQuery()
        private var hasNext: Boolean = resultSet.next()

        override fun hasNext(): Boolean = hasNext

        override fun next(): T {
            if (!hasNext) throw NoSuchElementException("No more rows in the result set")

            val item = rowMapper(resultSet)
            hasNext = resultSet.next()
            if (!hasNext) close() // Auto-close when iteration ends
            return item
        }

        override fun close() {
            try {
                resultSet.close()
                statement.close()
            } catch (e: SQLException) {
                Logger.e(QueryIterator::class.simpleName, "Error closing resources: ${e.message}")
            }
        }
    }

}