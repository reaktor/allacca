package fi.allacca

class IdGenerator {
  private var id: Int = 0
  def nextId: Int = {
    id = id + 1
    id
  }
}