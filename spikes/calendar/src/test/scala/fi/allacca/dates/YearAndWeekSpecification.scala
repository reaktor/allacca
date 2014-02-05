package fi.allacca.dates


import org.scalacheck.{Arbitrary, Properties, Gen}
import org.scalacheck.Prop.forAll

object YearAndWeekSpecification extends Properties(classOf[YearAndWeek].getSimpleName) {
  private val yawGenerator = for {
    year <- Gen.choose(1970, 2038)
    week <- Gen.choose(1,53)
  } yield YearAndWeek(year, week)

  implicit val arbitraryYaw = Arbitrary(yawGenerator)

  property("isOrderedByYearThenWeek") = forAll { (yaws: List[YearAndWeek]) => isSorted(yaws.sorted) }

  property("nextIsAfterCurrent") = forAll { (current: YearAndWeek) => YearAndWeek.YearAndWeekOrdering.gt(current.next, current) }

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