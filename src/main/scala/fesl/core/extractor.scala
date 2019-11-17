package fesl.core

import java.util.UUID

object ExtractUUID {
  type ExtractUUID[E] = E => UUID
}
