package fi.allacca.dates

import com.github.nscala_time.time.Imports._
import org.joda.time.ReadableDateTime

case class YearAndWeek(year: Int, week: Int) {
  def next: YearAndWeek = {
    val someDayOnWeek = new DateTime(year, 1, 4, 0, 0, 0).plusDays(7 * (week - 1))
    val someDayOnNextWeek = someDayOnWeek.plusWeeks(1)
    YearAndWeek.from(someDayOnNextWeek)
  }
}

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
