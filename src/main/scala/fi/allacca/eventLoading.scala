package fi.allacca

import org.joda.time.LocalDate

class EventModel {
  @volatile
  private var contents: Map[Long, DayWithEvents] = Map()
  @volatile
  private var sortedIds = List[Long]()
  @volatile
  var focusDay: LocalDate = new LocalDate

  def size = synchronized { sortedIds.size }

  def firstDay: LocalDate = synchronized {
    if (size < 1) {
      new LocalDate
    } else {
      val firstItemsKey = sortedIds.head
      contents(firstItemsKey).day
    }
  }

  def lastDay: LocalDate = synchronized {
    if (size < 1) {
      new LocalDate
    } else {
      val lastItemsKey = sortedIds.last
      contents(lastItemsKey).day
    }
  }

  def setFocusDay(focusDay: LocalDate) {
    this.focusDay = focusDay
  }

  def getItemFromContents(index: Int): Option[DayWithEvents] = synchronized {
    if (sortedIds.isEmpty) {
      None
    } else {
      if (sortedIds.size < index + 1) {
        None
      } else {
        val keyOfIndex = sortedIds(index)
        contents.lift(keyOfIndex)
      }
    }
  }

  def indexOf(day: LocalDate) = synchronized {
    sortedIds.indexOf(day.toDate.getTime)
  }

  /**
   * @param newDaysAndEventsFromLoader data to add
   * @param days days in data to add, passed in redundantly for performance reasons
   */
  def addOrUpdate(newDaysAndEventsFromLoader: Set[DayWithEvents], days: Set[LocalDate]) {
    synchronized {
      val oldItemsToRetain = contents.values.filter { dwe => !days.contains(dwe.day) && !dwe.events.isEmpty }
      val oldAndNewItems: Iterable[DayWithEvents] = oldItemsToRetain ++ newDaysAndEventsFromLoader
      val itemsInTotal: Iterable[DayWithEvents] = if (oldAndNewItems.exists { _.day == focusDay}) {
        oldAndNewItems
      } else oldAndNewItems ++ List(DayWithEvents(focusDay, Nil))
      val newIdsArray = new Array[Long](itemsInTotal.size)
      var i = 0
      itemsInTotal.foreach { dwe =>
        newIdsArray.update(i, dwe.id)
        i = i + 1
      }
      sortedIds = listSortedDistinctValues(newIdsArray)
      contents = itemsInTotal.map { dwe => (dwe.id, dwe) }.toMap
    }
  }

  /**
   * Fast unique sort from http://stackoverflow.com/a/8162643
   */
  def listSortedDistinctValues(someArray: Array[Long]): List[Long] = {
    if (someArray.length == 0) List[Long]()
    else {
      java.util.Arrays.sort(someArray)
      var last = someArray(someArray.length - 1)
      var list = last :: Nil
      var i = someArray.length - 2
      while (i >= 0) {
        if (someArray(i) < last) {
          last = someArray(i)
          if (last <= 0) return list
          list = last :: list
        }
        i -= 1
      }
      list
    }
  }
}
