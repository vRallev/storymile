package software.ralf.storymile.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Coordinates access to named resources whose previous owners may still be cleaning up. */
class ExclusiveResourceOwners(private val resourceName: String) {
  private val owners = MutableStateFlow<Map<String, List<Job>>>(emptyMap())

  /**
   * Claims [key] for [owner]. Join the returned jobs before using the resource.
   *
   * @throws IllegalStateException if another active job owns [key].
   */
  fun claim(key: String, owner: Job): List<Job> =
    owners
      .updateAndGet { keys ->
        owner.ensureActive()
        val previous = keys[key].orEmpty().filterNot { it.isCompleted }
        check(previous.none { it.isActive }) {
          "$resourceName is already owned by another active scope."
        }
        keys.filterValues { jobs -> jobs.any { !it.isCompleted } } + (key to (previous + owner))
      }
      .getValue(key)
      .dropLast(1)
}
