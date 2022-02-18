package com.mem.mqos.db

import androidx.room.*

@Entity(tableName = "commands")
data class CommandEntity(
  @PrimaryKey(autoGenerate = true)
  var id: Int,

  @ColumnInfo(name = "value") var value: String
) {
  constructor(value: String): this(0, value)
}
//@Entity(tableName = "commands")
//class CommandEntity {
//  @PrimaryKey(autoGenerate = true)
//  var id: Int
//
//  @ColumnInfo(name = "value") var value: String
//}

@Dao
interface CommandDao {
  @Query("SELECT * FROM commands")
  fun getAll(): List<CommandEntity>

  @Query("SELECT * FROM commands ORDER BY id DESC LIMIT 1")
  fun getWithLatestId(): List<CommandEntity>

  @Insert
  fun insertAll(vararg command: CommandEntity)

  //@Query("DELETE FROM commands")
  //fun deleteAll(command: CommandEntity)
}