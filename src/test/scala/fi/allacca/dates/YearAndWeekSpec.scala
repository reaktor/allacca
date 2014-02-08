package fi.allacca.dates


import org.scalacheck.{Arbitrary, Properties, Gen}
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.prop.Checkers
import org.joda.time.DateTime

@RunWith(classOf[JUnitRunner])
class YearAndWeekSpec extends FunSpec with Matchers with Checkers {
  private val yawGenerator = for {
    year <- Gen.choose(1970, 2038)
    week <- Gen.choose(1,53)
  } yield YearAndWeek(year, week)

  implicit val arbitraryYaw = Arbitrary(yawGenerator)

  describe("YearOfWeek") {
    it ("should be ordered isGreater year then week") {
      check(forAll { (yaws: List[YearAndWeek]) => isSorted(yaws.sorted) })
    }
    it ("should have next week after current week") {
      check(forAll { (current: YearAndWeek) => YearAndWeek.YearAndWeekOrdering.gt(current.next, current) })
    }
    it ("should have previous week before current week") {
      check(forAll { (current: YearAndWeek) => YearAndWeek.YearAndWeekOrdering.lt(current.previous, current) })
    }
    it ("should start on Monday 00.00") {
      check(forAll { (any: YearAndWeek) => any.firstDay.getDayOfWeek == 1 })
    }
    it ("should stop on Monday 00.00") {
      check(forAll { (any: YearAndWeek) => any.lastDay.getDayOfWeek == 1 })
    }

    it ("should consist of 7 consecutive days") {
      check(forAll { (any: YearAndWeek) => any.days.length == 7 })
      check(forAll { (any: YearAndWeek) => isSorted(any.days, (d1: DateTime, d2: DateTime) => d1.isBefore(d2)) })
    }

    it ("should work around start of a year") {
      val lastWeekOf2013 = YearAndWeek(2013, 52)
      lastWeekOf2013.next should be(YearAndWeek(2014, 1))
      lastWeekOf2013.next.next should be(YearAndWeek(2014, 2))

      lastWeekOf2013.previous should be(YearAndWeek(2013, 51))
      lastWeekOf2013.next.previous should be(lastWeekOf2013)
      lastWeekOf2013.next.next.previous should be(YearAndWeek(2014, 1))
      lastWeekOf2013.next.previous.next.previous should be(lastWeekOf2013)
      YearAndWeek(2014, 2).previous.previous should be(lastWeekOf2013)
    }
  }

  it ("should range from Monday 00.00 to next Monday 00.00") {
    val week6Of2014 = YearAndWeek(2014, 6)
    week6Of2014.firstDay should be(new DateTime(2014, 2, 3, 0, 0, 0))
    week6Of2014.lastDay should be(new DateTime(2014, 2, 10, 0, 0, 0))
    week6Of2014.days(0) should be(new DateTime(2014, 2, 3, 0, 0, 0))
    week6Of2014.days(1) should be(new DateTime(2014, 2, 4, 0, 0, 0))
    week6Of2014.days(2) should be(new DateTime(2014, 2, 5, 0, 0, 0))
    week6Of2014.days(3) should be(new DateTime(2014, 2, 6, 0, 0, 0))
    week6Of2014.days(4) should be(new DateTime(2014, 2, 7, 0, 0, 0))
    week6Of2014.days(5) should be(new DateTime(2014, 2, 8, 0, 0, 0))
    week6Of2014.days(6) should be(new DateTime(2014, 2, 9, 0, 0, 0))
  }

  private def isSorted(l: List[YearAndWeek]): Boolean = {
    isSorted(l, (first: YearAndWeek, second: YearAndWeek) =>
      if (first.year == second.year) {
        first.week <= second.week
      } else first.year <= second.year)
  }

  private def isSorted[T](l: Seq[T], isSmaller: ((T, T) => Boolean)): Boolean = {
    if (l.size < 2) true
    else {
      l.zip(l.tail).forall { case (first, second) => isSmaller(first, second) }
    }
  }
}
