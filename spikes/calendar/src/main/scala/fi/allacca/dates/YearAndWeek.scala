package fi.allacca.dates

import com.github.nscala_time.time.Imports._
import org.joda.time.ReadableDateTime

case class YearAndWeek(year: Int, week: Int)

object YearAndWeek {
  implicit val YearAndWeekOrdering = Ordering.by { yaw: YearAndWeek =>
    (yaw.year, yaw.week)
  }

  def from(date: ReadableDateTime): YearAndWeek = {
    val year = date.getWeekyear
    val week = date.getWeekOfWeekyear
    YearAndWeek(year, week)
  }

  def from(date: LocalDate): YearAndWeek = from(date.toDateTimeAtStartOfDay)
}
