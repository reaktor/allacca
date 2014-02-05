package fi.allacca.dates

import org.joda.time.{LocalDate, DateTime, ReadableDateTime}

case class YearAndWeek(year: Int, week: Int) {
  private val someDayOnWeek = new DateTime(year, 1, 4, 0, 0, 0).plusDays(7 * (week - 1))

  lazy val next: YearAndWeek = YearAndWeek.from(someDayOnWeek.plusWeeks(1))
  lazy val previous: YearAndWeek = YearAndWeek.from(someDayOnWeek.minusWeeks(1))
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
