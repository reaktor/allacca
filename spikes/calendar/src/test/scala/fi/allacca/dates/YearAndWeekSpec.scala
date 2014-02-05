package fi.allacca.dates


import org.scalacheck.{Arbitrary, Properties, Gen}
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.prop.Checkers

@RunWith(classOf[JUnitRunner])
class YearAndWeekSpec extends FunSpec with Matchers with Checkers {
  private val yawGenerator = for {
    year <- Gen.choose(1970, 2038)
    week <- Gen.choose(1,53)
  } yield YearAndWeek(year, week)

  implicit val arbitraryYaw = Arbitrary(yawGenerator)

  describe("YearOfWeek") {
    it ("should be ordered by year then week") {
      check(forAll { (yaws: List[YearAndWeek]) => isSorted(yaws.sorted) })
    }
    it ("should have next week after current week") {
      check(forAll { (current: YearAndWeek) => YearAndWeek.YearAndWeekOrdering.gt(current.next, current) })
    }
    it ("should have previous week before current week") {
      check(forAll { (current: YearAndWeek) => YearAndWeek.YearAndWeekOrdering.lt(current.previous, current) })
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

  private def isSorted(l: List[YearAndWeek]) = {
    if (l.size < 2) true
    else {
      l.zip(l.tail).forall {
        case (first, second) =>
          if (first.year == second.year) {
            first.week <= second.week
          } else first.year <= second.year
      }
    }
  }
}
