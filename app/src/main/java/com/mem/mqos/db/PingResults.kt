package com.mem.mqos.db

import androidx.room.*

@Entity(tableName = "ping_sequence_results")
data class PingSequenceResultEntity(
  @PrimaryKey(autoGenerate = true)
  var id: Int,

  @ColumnInfo(name = "seq") var seq: String,
  @ColumnInfo(name = "size") var size: String,
  @ColumnInfo(name = "ttl") var ttl: String,
  @ColumnInfo(name = "rtt") var rtt: String,

  //@ForeignKey
  @ColumnInfo(name = "commandId") var commandId: Int
) {
  constructor(seq: String, size: String, ttl: String, rtt: String, commandId: Int): this(0, seq, size, ttl, rtt, commandId)
}

@Dao
interface PingSequenceResultDao {
  @Query("SELECT * FROM ping_sequence_results")
  fun getAll(): List<PingSequenceResultEntity>

  @Query("SELECT * FROM ping_sequence_results WHERE commandId LIKE :id")
  fun getAllWithCommandId(id: Int): List<PingSequenceResultEntity>

  @Insert
  fun insertAll(vararg command: PingSequenceResultEntity)

  //@Query("DELETE FROM ping_sequence_results")
  //fun deleteAll(command: CommandEntity)
}

@Entity(tableName = "ping_final_results")
data class PingFinalResultEntity (
  @PrimaryKey(autoGenerate = true)
  var id: Int,

  @ColumnInfo(name = "loss_rate") var lossRate: String?,
  @ColumnInfo(name = "average_rtt") var averageRtt: String?,

  //@ForeignKey
  @ColumnInfo(name = "commandId") var commandId: Int
) {
  constructor(commandId: Int, lossRate: String?, averageRtt: String?): this(0, lossRate, averageRtt, commandId)
}

@Dao
interface PingFinalResultDao {
  @Query("SELECT * FROM ping_final_results")
  fun getAll(): List<PingFinalResultEntity>

  @Query("SELECT * FROM ping_final_results WHERE commandId LIKE :id")
  fun getAllWithCommandId(id: Int): List<PingFinalResultEntity>

  @Insert
  fun insertAll(vararg command: PingFinalResultEntity)

  @Query("UPDATE ping_final_results SET loss_rate = :lossRate WHERE commandId = :commandId")
  fun updateLossRateWhereCommandId(commandId: Int, lossRate: String?): Int

  @Query("UPDATE ping_final_results SET average_rtt = :averageRtt WHERE commandId = :commandId")
  fun updateAverageRttWhereCommandId(commandId: Int, averageRtt: String?): Int

  //@Query("DELETE FROM ping_final_results")
  //fun deleteAll(command: CommandEntity)
}
