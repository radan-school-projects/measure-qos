package com.mem.mqos.db

import androidx.room.*

@Entity(tableName = "iperf_commands")
data class IperfCommandEntity(
  @PrimaryKey(autoGenerate = true)
  var id: Int,

  @ColumnInfo(name = "value") var value: String
) {
  constructor(value: String): this(0, value)
}

@Dao
interface IperfCommandDao {
  @Query("SELECT * FROM iperf_commands")
  fun getAll(): List<IperfCommandEntity>

  @Query("SELECT * FROM iperf_commands ORDER BY id DESC LIMIT 1")
  fun getWithLatestId(): List<IperfCommandEntity>

  @Insert
  fun insertAll(vararg command: IperfCommandEntity)

  @Query("DELETE FROM iperf_commands")
  fun deleteAll()
}