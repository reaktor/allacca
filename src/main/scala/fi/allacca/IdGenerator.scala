package fi.allacca

class IdGenerator(start: Int = 0) {
  private var id: Int = start
  def nextId: Int = {
    id = id + 1
    id
  }
}