package com.mem.mqos.db

import androidx.room.*

@Entity(tableName = "ping_commands")
data class CommandEntity(
  @PrimaryKey(autoGenerate = true)
  var id: Int,

  @ColumnInfo(name = "value") var value: String
) {
  constructor(value: String): this(0, value)
}

@Dao
interface PingCommandDao {
  @Query("SELECT * FROM ping_commands")
  fun getAll(): List<CommandEntity>

  @Query("SELECT * FROM ping_commands ORDER BY id DESC LIMIT 1")
  fun getWithLatestId(): List<CommandEntity>

  @Insert
  fun insertAll(vararg command: CommandEntity)

  @Query("DELETE FROM ping_commands")
  fun deleteAll()
}

data class JoinElement(
  var id: Int,
  var value: String,
  var loss_rate: String,
  var average_rtt: String,
  var seq: String,
  var size: String,
  var ttl: String,
  val rtt: String
)

@Dao
interface PingJoinDao {
  @Query("SELECT " +
      "ping_commands.id, " +
      "ping_commands.value, " +
      "ping_final_results.loss_rate, " +
      "ping_final_results.average_rtt, " +
      "ping_sequence_results.seq, " +
      "ping_sequence_results.size, " +
      "ping_sequence_results.ttl, " +
      "ping_sequence_results.rtt " +
      "FROM ping_commands " +
      "JOIN ping_final_results " +
      "ON ping_commands.id = ping_final_results.commandId " +
      "JOIN ping_sequence_results " +
      "ON ping_commands.id = ping_sequence_results.commandId")
  fun joinAllPing(): List<JoinElement>
}